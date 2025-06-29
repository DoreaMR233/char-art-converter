package com.doreamr233.charartconverter.config;

import com.doreamr233.charartconverter.util.CharArtProcessor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import cn.hutool.core.io.FileUtil;
import java.io.File;
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
@Getter
@Component
@Configuration
@Slf4j
public class TempDirectoryConfig {

    /**
     * 临时目录路径，从配置文件中读取
     * 如果配置文件中未设置，则使用系统默认临时目录
     * -- GETTER --
     *  获取临时目录路径
     *

     */
    @Value("${char-art.temp-directory:#{systemProperties['java.io.tmpdir']}}")
    private String tempDirectory;

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
     * 支持相对路径和绝对路径，自动处理跨平台路径格式。
     * </p>
     */
    @PostConstruct
    public void initTempDirectory() {
        try {
            // 处理相对路径，转换为绝对路径
            Path tempPath = Paths.get(tempDirectory);
            if (!tempPath.isAbsolute()) {
                // 相对路径基于当前工作目录
                tempPath = Paths.get(System.getProperty("user.dir")).resolve(tempDirectory);
                tempDirectory = tempPath.toString();
                log.debug("相对路径转换为绝对路径: {}", tempDirectory);
            }
            
            // 规范化路径（处理.和..等）
            tempPath = tempPath.normalize().toAbsolutePath();
            tempDirectory = tempPath.toString();
            
            // 如果目录不存在，则创建
            if (!FileUtil.exist(tempPath.toFile())) {
                FileUtil.mkdir(tempPath.toFile());
                log.debug("创建临时目录: {}", tempDirectory);
            }
            
            // 验证目录权限
            File tempDir = tempPath.toFile();
            if (!tempDir.canRead()) {
                log.warn("临时目录不可读: {}", tempDirectory);
            }
            if (!tempDir.canWrite()) {
                log.warn("临时目录不可写: {}", tempDirectory);
            }
            
            // 检查是否为目录
            if (!tempDir.isDirectory()) {
                throw new RuntimeException("指定的路径不是目录: " + tempDirectory);
            }
            
            log.debug("临时目录配置完成: {} (操作系统: {})", tempDirectory, System.getProperty("os.name"));
            
        } catch (Exception e) {
            log.error("初始化临时目录失败: {}", tempDirectory, e);
            // 回退到系统默认临时目录
            String systemTempDir = System.getProperty("java.io.tmpdir");
            log.warn("回退到系统默认临时目录: {}", systemTempDir);
            tempDirectory = systemTempDir;
        }
        
        // 初始化CharArtProcessor的静态配置，确保一致性
        CharArtProcessor.setTempDirectoryConfig(this);
        log.debug("已初始化CharArtProcessor的临时目录配置");
    }
}