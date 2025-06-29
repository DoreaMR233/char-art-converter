package com.doreamr233.charartconverter.service;

import com.doreamr233.charartconverter.config.TempDirectoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import cn.hutool.core.io.FileUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 临时文件清理服务测试类
 * <p>
 * 测试临时文件清理服务的各种功能，包括文件删除、目录清理等。
 * 使用与配置文件一致的临时目录结构进行测试，确保测试环境与实际运行环境保持一致。
 * </p>
 * <p>
 * 测试目录结构：{project.dir}/target/test-temp/temp
 * 这与配置文件中的 char-art.temp-directory=./temp 保持一致。
 * </p>
 *
 * @author doreamr233
 */
class TempFileCleanupServiceTest {

    @Mock
    private TempDirectoryConfig tempDirectoryConfig;

    @InjectMocks
    private TempFileCleanupService tempFileCleanupService;

    private Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 使用与配置文件一致的临时目录路径
        // 在测试目录下创建temp子目录，模拟实际配置
        Path testBaseDir = Paths.get(System.getProperty("user.dir"), "target", "test-temp");
        tempDir = testBaseDir.resolve("temp");
        
        // 确保测试目录存在
        FileUtil.mkdir(tempDir.toFile());
        
        when(tempDirectoryConfig.getTempDirectory()).thenReturn(tempDir.toString());
        
        // 设置测试配置
        ReflectionTestUtils.setField(tempFileCleanupService, "maxRetentionHours", 1);
        ReflectionTestUtils.setField(tempFileCleanupService, "cleanupEnabled", true);
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试目录
        if (FileUtil.exist(tempDir.toFile())) {
            FileUtil.del(tempDir.toFile());
        }
    }

    @Test
    void testCleanupTempFiles_WithOldFiles() {
        // 创建一个过期文件
        Path oldFile = tempDir.resolve("old_file.txt");
        FileUtil.touch(oldFile.toFile());
        
        // 设置文件为2小时前修改
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        oldFile.toFile().setLastModified(twoHoursAgo.toEpochMilli());
        
        // 创建一个新文件
        Path newFile = tempDir.resolve("new_file.txt");
        FileUtil.touch(newFile.toFile());
        
        // 执行清理
        tempFileCleanupService.cleanupTempFiles();
        
        // 验证结果
        assertFalse(FileUtil.exist(oldFile.toFile()), "过期文件应该被删除");
        assertTrue(FileUtil.exist(newFile.toFile()), "新文件应该保留");
    }

    @Test
    void testCleanupTempFiles_WithEmptyDirectories() {
        // 创建嵌套目录结构
        Path subDir = tempDir.resolve("subdir");
        FileUtil.mkdir(subDir.toFile());
        
        Path oldFile = subDir.resolve("old_file.txt");
        FileUtil.touch(oldFile.toFile());
        
        // 设置文件和目录为过期
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        oldFile.toFile().setLastModified(twoHoursAgo.toEpochMilli());
        subDir.toFile().setLastModified(twoHoursAgo.toEpochMilli());
        
        // 执行清理
        tempFileCleanupService.cleanupTempFiles();
        
        // 验证结果
        assertFalse(FileUtil.exist(oldFile.toFile()), "过期文件应该被删除");
        assertFalse(FileUtil.exist(subDir.toFile()), "空目录应该被删除");
        assertTrue(FileUtil.exist(tempDir.toFile()), "根目录应该保留");
    }

    @Test
    void testCleanupTempFiles_WhenDisabled() {
        // 禁用清理功能
        ReflectionTestUtils.setField(tempFileCleanupService, "cleanupEnabled", false);
        
        // 创建过期文件
        Path oldFile = tempDir.resolve("old_file.txt");
        FileUtil.touch(oldFile.toFile());
        
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        oldFile.toFile().setLastModified(twoHoursAgo.toEpochMilli());
        
        // 执行清理
        tempFileCleanupService.cleanupTempFiles();
        
        // 验证文件仍然存在
        assertTrue(FileUtil.exist(oldFile.toFile()), "禁用清理时文件应该保留");
    }

    @Test
    void testCleanupTempFiles_WithNonExistentDirectory() {
        // 设置不存在的目录
        when(tempDirectoryConfig.getTempDirectory()).thenReturn("/non/existent/path");
        
        // 执行清理不应该抛出异常
        assertDoesNotThrow(() -> tempFileCleanupService.cleanupTempFiles());
    }

    @Test
    void testGetMaxRetentionHours() {
        assertEquals(1, tempFileCleanupService.getMaxRetentionHours());
    }

    @Test
    void testIsCleanupEnabled() {
        assertTrue(tempFileCleanupService.isCleanupEnabled());
        
        ReflectionTestUtils.setField(tempFileCleanupService, "cleanupEnabled", false);
        assertFalse(tempFileCleanupService.isCleanupEnabled());
    }
}