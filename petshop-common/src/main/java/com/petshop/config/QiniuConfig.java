package com.petshop.config;

import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 七牛云对象存储配置。
 * <p>
 * 读取 application.yml 里 {@code qiniu.*} 的配置，并把上传要用的
 * {@link Auth}（生成上传凭证）和 {@link UploadManager}（执行上传）注册成 Bean，
 * 供 {@code QiniuService} 注入使用。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qiniu")
public class QiniuConfig {

    /** 七牛 AccessKey */
    private String accessKey;
    /** 七牛 SecretKey */
    private String secretKey;
    /** 存储空间（Bucket）名 */
    private String bucket;
    /** 访问域名（末尾不带 /），拼在文件 key 前组成最终可访问 URL */
    private String domain;

    /** 鉴权对象：用 AK/SK 生成上传凭证。 */
    @Bean
    public Auth qiniuAuth() {
        return Auth.create(accessKey, secretKey);
    }

    /** 上传管理器：autoRegion 自动探测 Bucket 所在区域，无需手填华东/华北等。 */
    @Bean
    public UploadManager qiniuUploadManager() {
        com.qiniu.storage.Configuration cfg =
                new com.qiniu.storage.Configuration(Region.autoRegion());
        return new UploadManager(cfg);
    }
}
