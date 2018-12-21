package com.qin.lock;

import com.qin.lock.entity.DistributeLockProperties;
import com.qin.lock.entity.LockType;
import com.qin.lock.zookeeper.ZKReadWriteLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Created by DELL on 2018/12/21.
 */
@Configuration
@EnableConfigurationProperties(DistributeLockProperties.class)
@ConditionalOnClass(ReadWriteLock.class)
public class DistributeLockAutoConfiguration {

    @Autowired
    private DistributeLockProperties distributeLockProperties;

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean(ReadWriteLock.class)
    public ReadWriteLock readWriteLock(){
        try {
            if(distributeLockProperties.getLockType()== LockType.ZOOKEEPER){
                return new ZKReadWriteLock(distributeLockProperties);
            }
            return new ZKReadWriteLock(distributeLockProperties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
