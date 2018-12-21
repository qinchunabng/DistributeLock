package com.qin.lock;

/**
 * 读写锁接口
 * Created by DELL on 2018/12/20.
 */
public interface ReadWriteLock {

    /**
     * 获取读锁
     * @return
     */
    DistributeLock readLock();

    /**
     * 获取写锁
     * @return
     */
    DistributeLock writeLock();
}
