package com.qin.lock;

/**
 * 分布式锁接口
 * Created by DELL on 2018/12/20.
 */
public interface DistributeLock {

    void lock() throws Exception;

    boolean tryLock() throws Exception;

    boolean tryLock(long millisecond) throws Exception;

    void unlock() throws Exception;
}
