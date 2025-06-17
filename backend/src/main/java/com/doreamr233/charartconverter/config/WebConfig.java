package com.doreamr233.charartconverter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * <p>
 * 该类负责配置Web相关的设置，主要处理跨域资源共享(CORS)问题。
 * 实现了WebMvcConfigurer接口，提供了两种CORS配置方式：
 * 1. 通过重写addCorsMappings方法配置基本的CORS规则
 * 2. 通过CorsFilter Bean提供更细粒度的CORS控制，特别是针对SSE连接
 * </p>
 *
 * @author doreamr233
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置跨域请求
     * <p>
     * 通过重写WebMvcConfigurer接口的addCorsMappings方法，
     * 为所有API端点配置CORS规则，允许前端开发服务器访问后端API。
     * </p>
     *
     * @param registry CORS注册表，用于添加CORS映射
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173") // 前端开发服务器地址
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false) // 修改为false，与前端EventSource保持一致
                .maxAge(3600);
    }
    
    /**
     * 配置CORS过滤器，特别处理SSE连接的跨域问题
     * <p>
     * 创建一个CorsFilter Bean，提供比addCorsMappings更细粒度的CORS控制。
     * 特别针对Server-Sent Events (SSE)连接的跨域问题进行了优化配置。
     * 设置allowCredentials为false，与前端EventSource保持一致。
     * </p>
     *
     * @return 配置好的CorsFilter实例
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的源
        config.addAllowedOrigin("http://localhost:5173");
        
        // 允许的HTTP方法
        config.addAllowedMethod("*");
        
        // 允许的头部
        config.addAllowedHeader("*");
        
        // 不允许发送凭证，与前端EventSource保持一致
        config.setAllowCredentials(false);
        
        // 暴露的响应头，对于SSE连接很重要
        config.addExposedHeader("Content-Type");
        config.addExposedHeader("Cache-Control");
        config.addExposedHeader("Connection");
        
        // 预检请求的有效期
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}