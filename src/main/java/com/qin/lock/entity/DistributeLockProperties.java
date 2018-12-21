package com.qin.lock.entity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created by DELL on 2018/12/21.
 */
@ConfigurationProperties(prefix = "lock")
public class DistributeLockProperties {

    private LockType lockType = LockType.ZOOKEEPER;

    private String connectString;

    private String username;

    private String password;

    private String host = "localhost";

    private int port = 6379;

    /**
     * 锁的父节点路径
     */
    private String lockNodeParentPath = "/share_lock";

    /**
     * 自旋超时时间阙值，超过这个阙值，将会休眠，防止浪费CPU资源
     */
    private long spinForTimeoutThreshold=1000L;

    /**
     * 自旋休眠时间
     */
    private long sleepTime = 100L;



    public DistributeLockProperties() {
    }

    public LockType getLockType() {
        return lockType;
    }

    public void setLockType(LockType lockType) {
        this.lockType = lockType;
    }

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLockNodeParentPath() {
        return lockNodeParentPath;
    }

    public void setLockNodeParentPath(String lockNodeParentPath) {
        this.lockNodeParentPath = lockNodeParentPath;
    }

    public long getSpinForTimeoutThreshold() {
        return spinForTimeoutThreshold;
    }

    public void setSpinForTimeoutThreshold(long spinForTimeoutThreshold) {
        this.spinForTimeoutThreshold = spinForTimeoutThreshold;
    }

    public long getSleepTime() {
        return sleepTime;
    }

    public void setSleepTime(long sleepTime) {
        this.sleepTime = sleepTime;
    }
}
