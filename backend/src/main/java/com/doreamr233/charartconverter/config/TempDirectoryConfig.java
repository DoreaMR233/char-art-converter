package com.doreamr233.charartconverter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 临时目录配置类
 * <p>
 * 用于管理应用程序的临时文件目录配置。
 * 从application.properties中读取临时目录路径，
 * 并确保目录存在且可写。
 * </p>
 *
 * @author doreamr233
 */
@Component
@Configuration
@Slf4j
public class TempDirectoryConfig {

    /**
     * 临时目录路径，从配置文件中读取
     */
    @Value("${java.io.tmpdir:/tmp}")
    private String tempDirectory;

    /**
     * 获取临时目录路径
     *
     * @return 临时目录路径字符串
     */
    public String getTempDirectory() {
        return tempDirectory;
    }

    /**
     * 获取临时目录Path对象
     *
     * @return 临时目录Path对象
     */
    public Path getTempDirectoryPath() {
        return Paths.get(tempDirectory);
    }

    /**
     * 初始化临时目录
     * <p>
     * 在Bean初始化后检查临时目录是否存在，
     * 如果不存在则创建，并验证目录的可读写权限。
     * </p>
     */
    @PostConstruct
    public void initTempDirectory() {
        try {
            Path tempPath = Paths.get(tempDirectory);
            
            // 如果目录不存在，则创建
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
                log.info("创建临时目录: {}", tempDirectory);
            }
            
            // 验证目录权限
            File tempDir = tempPath.toFile();
            if (!tempDir.canRead()) {
                log.warn("临时目录不可读: {}", tempDirectory);
            }
            if (!tempDir.canWrite()) {
                log.warn("临时目录不可写: {}", tempDirectory);
            }
            
            log.info("临时目录配置完成: {}", tempDirectory);
            
        } catch (Exception e) {
            log.error("初始化临时目录失败: {}", tempDirectory, e);
            throw new RuntimeException("初始化临时目录失败: " + e.getMessage(), e);
        }
    }
}