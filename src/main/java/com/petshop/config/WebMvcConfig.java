package com.petshop.config;

import com.petshop.security.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 JWT 拦截器，并放行接口文档等静态资源。
 * 同时将本地上传视频目录映射为可访问的静态资源（/files/videos/**）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    /** 本地视频存储目录，与 VideoController 保持一致 */
    @Value("${upload.video-path:${user.home}/petshop/videos}")
    private String videoStoragePath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/doc.html", "/webjars/**", "/swagger-resources/**",
                        "/v2/api-docs", "/v3/api-docs", "/favicon.ico", "/error",
                        // 放行本地上传文件的静态资源路径
                        "/files/**"
                );
    }

    /**
     * 将本地磁盘上的视频文件目录映射到 /files/videos/** URL，
     * 使上传后的视频文件可通过 HTTP 直接访问。
     * 例如：http://localhost:8080/files/videos/xxxxxx.mp4
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/videos/**")
                .addResourceLocations("file:" + videoStoragePath + "/");
    }
}

