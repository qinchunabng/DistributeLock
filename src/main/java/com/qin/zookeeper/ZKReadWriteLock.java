package com.qin.zookeeper;

import com.qin.DistributeLock;
import com.qin.ReadWriteLock;
import com.qin.entity.DistributeLockProperties;
import com.qin.entity.LockStatus;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * zookeeper读写分布式锁
 * Created by DELL on 2018/12/20.
 */
public class ZKReadWriteLock implements ReadWriteLock {

    private static final Logger logger = LoggerFactory.getLogger(ZKReadWriteLock.class);

    private static final String READ_LOCK_PREFIX = new Random().nextInt(10000000) + "-read-";

    private static final String WRITE_LOCK_PREFIX = new Random().nextInt(10000000) + "-write-";

    private ZooKeeper zooKeeper;

    private DistributeLockProperties distributeLockProperties;

//    private ReadLock readLock;
//
//    private WriteLock writeLock;

    private Comparator<String> nameComparator;


    /**
     * zookeeper连接标识
     */
    private CountDownLatch connectedSemaphore = new CountDownLatch(1);

    public ZKReadWriteLock(DistributeLockProperties distributeLockProperties) throws Exception {
        this.distributeLockProperties = distributeLockProperties;
        zooKeeper = new ZooKeeper(distributeLockProperties.getConnectString(), 1000, watchedEvent -> {
            if (Watcher.Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                connectedSemaphore.countDown();
            }
        });

        connectedSemaphore.await();

        nameComparator = (x, y) -> {
            int xs = getSequence(x);
            int ys = getSequence(y);
            return xs > ys ? 1 : (xs < ys ? -1 : 0);
        };

//        readLock = new ReadLock();
//        writeLock = new WriteLock();
    }

    public DistributeLock readLock() {
        return new ReadLock();
    }

    public DistributeLock writeLock() {
//        if (writeLock == null) {
//            throw new LockInitialException("WriteLock has not be initialized!");
//        }
        return new WriteLock();
    }

    /**
     * 获取序号
     *
     * @param name
     * @return
     */
    private int getSequence(String name) {
        return Integer.valueOf(name.substring(name.lastIndexOf("-") + 1));
    }

    /**
     * 创建锁节点
     *
     * @return
     */
    private String createLockNode(String name) throws Exception {
        if (zooKeeper.exists(distributeLockProperties.getLockNodeParentPath(), null) == null) {
            synchronized (ZKReadWriteLock.class) {
                if (zooKeeper.exists(distributeLockProperties.getLockNodeParentPath(), null) == null) {
                    try {
                        zooKeeper.create(distributeLockProperties.getLockNodeParentPath(), "".getBytes(Charset.forName("UTF-8")),
                                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    } catch (KeeperException.NodeExistsException e) {//捕获这个异常防止创建冲突
                        if (logger.isWarnEnabled()) {
                            logger.warn("{}创建冲突", distributeLockProperties.getLockNodeParentPath());
                        }
                    }
                }
            }
        }
        return zooKeeper.create(distributeLockProperties.getLockNodeParentPath() + "/" + name, "".getBytes("UTF-8"),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
    }

    /**
     * 删除锁节点
     *
     * @param name
     * @throws Exception
     */
    private void deleteLockNode(String name) throws Exception {
        Stat stat = zooKeeper.exists(distributeLockProperties.getLockNodeParentPath() + "/" + name, false);
        zooKeeper.delete(distributeLockProperties.getLockNodeParentPath() + "/" + name, stat.getVersion());
    }

    /**
     * 能否获取读锁
     * 获取读锁条件：
     * 1.自己创建的节点序号排在所有其他子节点前面
     * 2.自己创建的节点前面无写锁节点
     * 满足其中一个就行
     *
     * @param name
     * @param nodes
     * @return
     */
    private boolean canAcquireReadLock(String name, List<String> nodes) {
        //判断是否第一个节点
        if (isFirstNode(name, nodes)) {
            return true;
        }
        //判断节点前是否有写节点
        Map<String, Boolean> map = new HashMap<>(nodes.size());
        boolean hasWriteNode = false;
        for (String n : nodes) {
            if (n.contains("read") && !hasWriteNode) {
                map.put(n, true);
            } else {
                hasWriteNode = true;
                map.put(n, false);
            }
        }
        return map.get(name);
    }

    /**
     * 是否可以获取写锁
     * 因为写锁是排他锁，所以当前节点必须是第一个节点才能获取得到写锁
     *
     * @param name
     * @param nodes
     * @return
     */
    private boolean canAcquireWriteLock(String name, List<String> nodes) {
        return isFirstNode(name, nodes);
    }

    /**
     * 是否第一个节点
     *
     * @param name
     * @param nodes
     * @return
     */
    private boolean isFirstNode(String name, List<String> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }
        return Objects.equals(name, nodes.get(0));
    }

    /**
     * 读锁
     */
    class ReadLock implements DistributeLock, Watcher {

        private volatile LockStatus lockStatus = LockStatus.UNLOCK;

        private CyclicBarrier lockBarrier = new CyclicBarrier(2);

//        private String prefix = new Random().nextInt(10000000) + "-read-";

        private String name;

        @Override
        public void lock() throws Exception {
            if (lockStatus == LockStatus.LOCKED) {
                return;
            }
            //1.创建锁节点
            if (name == null) {
                name = createLockNode(READ_LOCK_PREFIX);
                name = name.substring(name.lastIndexOf("/") + 1);
                if (logger.isInfoEnabled()) {
                    logger.info("创建锁节点：{}", name);
                }
            }

            //2.获取锁节点列表
            List<String> nodes = zooKeeper.getChildren(distributeLockProperties.getLockNodeParentPath(), null);
            nodes.sort(nameComparator);

            //3.检查能否获取锁
            if (canAcquireReadLock(name, nodes)) {
                if (logger.isInfoEnabled()) {
                    logger.info("{}获取读锁", name);
                }
                lockStatus = LockStatus.LOCKED;
                return;
            }

            //4.不能获取锁，找到比自己小的最后一个写锁节点并监视
            int index = Collections.binarySearch(nodes, name, nameComparator);
            for (int i = index - 1; i >= 0; i--) {
                if (nodes.get(i).contains("write")) {
                    Stat stat = zooKeeper.exists(distributeLockProperties.getLockNodeParentPath() + "/" + nodes.get(i), this);
                    //如果节点不存在，说明已经被删除，获取锁成功
                    if (stat == null) {
                        lockStatus = LockStatus.LOCKED;
                        return;
                    }
                    break;
                }
            }

            //5.等待监视的节点被删除
            lockStatus = LockStatus.TRY_LOCK;
            lockBarrier.await();
        }

        @Override
        public boolean tryLock() throws Exception {
            if (lockStatus == LockStatus.LOCKED) {
                return true;
            }
            //1.创建锁节点
            if (name == null) {
                name = createLockNode(READ_LOCK_PREFIX);
                name = name.substring(name.lastIndexOf("/") + 1);
                if (logger.isInfoEnabled()) {
                    logger.info("创建锁节点：{}", name);
                }
            }

            //2.获取锁节点列表
            List<String> nodes = zooKeeper.getChildren(distributeLockProperties.getLockNodeParentPath(), null);
            nodes.sort(nameComparator);

            //3.检查能否获取锁
            if (canAcquireReadLock(name, nodes)) {
                if (logger.isInfoEnabled()) {
                    logger.info("{}获取锁");
                }
                lockStatus = LockStatus.LOCKED;
                return true;
            }
            return false;
        }

        @Override
        public boolean tryLock(long millisecond) throws Exception {
            if (lockStatus == LockStatus.LOCKED) {
                return true;
            }
            final long deadline = System.currentTimeMillis() + millisecond;
            for (; ; ) {
                if (tryLock()) {
                    return true;
                }

                //如果millisecond小于0或者大于等待时间阙值
                //线程休眠，避免浪费CPU资源
                if (millisecond < 0 || millisecond > distributeLockProperties.getSpinForTimeoutThreshold()) {
                    Thread.sleep(distributeLockProperties.getSleepTime());
                }

                //超时返回获取锁失败
                if (deadline - System.currentTimeMillis() <= 0) {
                    return false;
                }
            }
        }

        @Override
        public void unlock() throws Exception {
            if (lockStatus == LockStatus.UNLOCK) {
                return;
            }
            deleteLockNode(name);
            lockStatus = LockStatus.UNLOCK;
            lockBarrier.reset();
            if (logger.isInfoEnabled()) {
                logger.info("{}释放锁", name);
            }
            name = null;
        }

        @Override
        public void process(WatchedEvent watchedEvent) {
            if (Event.KeeperState.SyncConnected != watchedEvent.getState()) {
                return;
            }

            if (Event.EventType.NodeDeleted == watchedEvent.getType()) {
                if (lockStatus != LockStatus.TRY_LOCK) {
                    return;
                }

                lockStatus = LockStatus.LOCKED;
                try {
                    lockBarrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
    }


    /**
     * 写锁
     */
    class WriteLock implements DistributeLock, Watcher {

        private volatile LockStatus lockStatus = LockStatus.UNLOCK;

        private CyclicBarrier lockBarrier = new CyclicBarrier(2);

//        private String prefix = new Random().nextInt(10000000) + "-write-";

        private String name;

        @Override
        public void lock() throws Exception {
            if (lockStatus == LockStatus.LOCKED) {
                return;
            }

            //1.创建锁节点
            if (name == null) {
                name = createLockNode(WRITE_LOCK_PREFIX);
                name = name.substring(name.lastIndexOf("/") + 1);
                if (logger.isInfoEnabled()) {
                    logger.info("创建锁节点：{}", name);
                }
            }

            //2.获取锁节点列表
            List<String> nodes = zooKeeper.getChildren(distributeLockProperties.getLockNodeParentPath(), null);
            nodes.sort(nameComparator);

            //3.检查能否获取锁
            if (canAcquireWriteLock(name, nodes)) {
                if (logger.isInfoEnabled()) {
                    logger.info("{}获取锁", name);
                }
                lockStatus = LockStatus.LOCKED;
                return;
            }

            //4.获取锁失败，定位到上一个锁节点，并监视
            int index = Collections.binarySearch(nodes, name, nameComparator);
            Stat stat = zooKeeper.exists(distributeLockProperties.getLockNodeParentPath() + "/" + nodes.get(index - 1), this);

            //如果节点不存在，说明已经被删除，获取锁成功
            if (stat == null) {
                lockStatus = LockStatus.LOCKED;
                return;
            }
            //5.等待监视的节点被删除
            lockStatus = LockStatus.TRY_LOCK;
            lockBarrier.await();
        }

        @Override
        public boolean tryLock() throws Exception {
            if (lockStatus == LockStatus.LOCKED) {
                return true;
            }

            //1.创建节点
            if (name == null) {
                name = createLockNode(WRITE_LOCK_PREFIX);
                name = name.substring(name.lastIndexOf("/") + 1);
                if (logger.isInfoEnabled()) {
                    logger.info("创建锁节点：{}", name);
                }
            }

            //2.获取锁节点列表
            List<String> nodes = zooKeeper.getChildren(distributeLockProperties.getLockNodeParentPath(), null);
            nodes.sort(nameComparator);

            //3.检查能否获取锁
            if (canAcquireWriteLock(name, nodes)) {
                if (logger.isInfoEnabled()) {
                    logger.info("{}获取锁", name);
                }
                lockStatus = LockStatus.LOCKED;
                return true;
            }
            return false;
        }

        @Override
        public boolean tryLock(long millisecond) throws Exception {
            if (lockStatus == LockStatus.LOCKED) {
                return true;
            }
            final long deadline = System.currentTimeMillis() + millisecond;
            for (; ; ) {
                if (tryLock()) {
                    return true;
                }

                //如果millisecond小于0或者大于等待时间阙值
                //线程休眠，避免浪费CPU资源
                if (millisecond < 0 || millisecond > distributeLockProperties.getSpinForTimeoutThreshold()) {
                    Thread.sleep(distributeLockProperties.getSleepTime());
                }

                //超时
                if (deadline - System.currentTimeMillis() <= 0L) {
                    return false;
                }
            }
        }

        @Override
        public void unlock() throws Exception {
            if (lockStatus == LockStatus.UNLOCK) {
                return;
            }
            deleteLockNode(name);
            if (logger.isInfoEnabled()) {
                logger.info("{}释放锁", name);
            }
            lockStatus = LockStatus.UNLOCK;
            lockBarrier.reset();
            name = null;
        }

        @Override
        public void process(WatchedEvent watchedEvent) {
            if (Event.EventType.NodeDeleted == watchedEvent.getType()) {
                if (lockStatus != LockStatus.TRY_LOCK) {
                    return;
                }

                lockStatus = LockStatus.LOCKED;
                try {
                    lockBarrier.await();
                    if (logger.isInfoEnabled()) {
                        logger.info("{}获取锁", name);
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
    }
}
