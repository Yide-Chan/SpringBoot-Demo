package com.chenyide.minio.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author chenyide
 * @version v1.0
 * @className MinioProperties
 * @description
 * @date 2024/6/7 17:17
 **/
@Data
@Component
public class MinioProperties {
    /**
     * 对象存储服务的URL
     */
    @Value("${minio.endpoint}")
    private String endpoint;

    /**
     * Access key就像用户ID，可以唯一标识你的账户
     */
    @Value("${minio.accessKey}")
    private String accessKey;

    /**
     * Secret key是你账户的密码
     */
    @Value("${minio.secretKey}")
    private String secretKey;
}
