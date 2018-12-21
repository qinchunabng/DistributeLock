# 分布式锁
下载该项目到本地，使用mvn命令mvn install将该项目安装到本地仓库，然后在pom文件添加依赖：
```
<dependency>
  <groupId>com.qin</groupId>
  <artifactId>distributelock-spring-boot-starter</artifactId>
  <version>1.0</version>
</dependency>
```
在springboot配置文件中添加zookeeper连接地址：
```
lock.connect-string=localhost:2181
```
在需要使用的分布式锁的注入锁对象:
```
@Autowired
private ReadWriteLock readWriteLock;


//获取读锁，读锁为共享锁
DistributeLock readLock = readWriteLock.readLock();
readLock.lock();
doSomething();
readLock.unlock();

//获取写锁，写锁为排他锁
DistributeLock writeLock = readWriteLock.writeLock();
writeLock.lock();
doSomething();
writeLock.unlock();
```
