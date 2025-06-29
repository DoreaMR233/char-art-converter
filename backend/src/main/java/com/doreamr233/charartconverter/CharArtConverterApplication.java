package com.doreamr233.charartconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 字符画转换应用程序的主类
 * <p>
 * 这个类是Spring Boot应用程序的入口点，负责启动整个应用程序。
 * 使用@SpringBootApplication注解来启用自动配置和组件扫描。
 * 使用@EnableScheduling注解来启用定时任务功能。
 * </p>
 *
 * @author doreamr233
 */
@SpringBootApplication
@EnableScheduling
public class CharArtConverterApplication {

    /**
     * 应用程序的主方法，启动Spring Boot应用
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CharArtConverterApplication.class, args);
    }

}