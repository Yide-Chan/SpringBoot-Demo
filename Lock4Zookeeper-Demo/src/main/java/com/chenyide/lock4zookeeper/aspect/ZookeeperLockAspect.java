package com.chenyide.lock4zookeeper.aspect;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.chenyide.lock4zookeeper.annotation.LockKeyParam;
import com.chenyide.lock4zookeeper.annotation.ZookeeperLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author chenyide
 * @version v1.0
 * @className ZookeeperLockAspect
 * @description
 * @date 2024/6/11 23:55
 **/
@Aspect
@Component
@Slf4j
public class ZookeeperLockAspect {
    private final CuratorFramework zkClient;

    private static final String KEY_PREFIX = "DISTRIBUTED_LOCK_";

    private static final String KEY_SEPARATOR = "/";

    @Autowired
    public ZookeeperLockAspect(CuratorFramework zkClient) {
        this.zkClient = zkClient;
    }

    @Pointcut("@annotation(com.chenyide.lock4zookeeper.annotation.ZookeeperLock)")
    public void Poincut() {

    }

    @Around("Poincut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Exception {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        ZookeeperLock zLock = method.getAnnotation(ZookeeperLock.class);
        if (StrUtil.isBlank(zLock.key())) {
            throw new RuntimeException("分布式锁键不能为空");
        }
        String lockKey = buildLockKey(zLock, method, args);
        /**
         * 进程间互斥量 InterProcessMutex该类实现了InterProcessLock和Revocable<InterProcessMutex>两个接口,
         *
         * InterProcessMutex是一把跨JVM的可重入互斥锁，用于ZooKeeper持有锁
         */

        InterProcessMutex lock = new InterProcessMutex(zkClient, lockKey);
        try {
            // lock.acquire获取锁 假设上锁成功，以后拿到的都是 false
            if (lock.acquire(zLock.timeout(), zLock.timeUnit())) {
                return joinPoint.proceed();
            } else {
                throw new RuntimeException("请勿重复提交");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            try {
                // 释放锁
                lock.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 构造分布式锁的键
     *
     * @param zLock  注解
     * @param method 注解标记的方法
     * @param args   方法上的参数
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private String buildLockKey(ZookeeperLock zLock, Method method, Object[] args) throws Exception {
        StringBuilder key = new StringBuilder(KEY_SEPARATOR + KEY_PREFIX + zLock.key());

        // 迭代全部参数的注解，根据使用LockKeyParam的注解的参数所在的下标，来获取args中对应下标的参数值拼接到前半部分key上
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            // 循环该参数全部注解
            for (Annotation annotation : parameterAnnotations[i]) {
                // 注解不是 @LockKeyParam
                if (!annotation.annotationType().isInstance(LockKeyParam.class)) {
                    continue;
                }

                // 获取所有字段->fields
                String[] fields = ((LockKeyParam) annotation).fields();
                if (ArrayUtil.isEmpty(fields)) {
                    // 普通数据类型直接拼接
                    if (ObjectUtil.isNull(args[i])) {
                        throw new RuntimeException("动态参数不能为null");
                    }
                    key.append(KEY_SEPARATOR).append(args[i]);
                } else {
                    // @LockKeyParam的fields值不为null，所以当前参数应该是对象类型
                    for (String field : fields) {
                        Class<?> clazz = args[i].getClass();
                        Field declaredField = clazz.getDeclaredField(field);
                        declaredField.setAccessible(true);
                        Object value = declaredField.get(clazz);
                        key.append(KEY_SEPARATOR).append(value);
                    }
                }
            }
        }
        return key.toString();
    }
}
