package com.doreamr233.charartconverter.service;

import com.doreamr233.charartconverter.config.TempDirectoryConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import cn.hutool.core.io.FileUtil;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 临时文件清理服务
 * <p>
 * 负责定期清理临时目录中的过期文件和文件夹。
 * 清理策略基于文件的最后修改时间，超过指定保留时间的文件将被删除。
 * </p>
 *
 * @author doreamr233
 */
@Service
@Slf4j
public class TempFileCleanupService {

    @Resource
    private TempDirectoryConfig tempDirectoryConfig;

    /**
     * 临时文件最大保留时间（小时），默认24小时
     * -- GETTER --
     *  获取当前配置的最大保留时间
     *

     */
    @Getter
    @Value("${char-art.temp-file.max-retention-hours:24}")
    private int maxRetentionHours;

    /**
     * 是否启用临时文件清理，默认启用
     * -- GETTER --
     *  检查清理功能是否启用
     *

     */
    @Getter
    @Value("${char-art.temp-file.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    /**
     * 执行临时文件清理任务
     * <p>
     * 扫描临时目录，删除超过最大保留时间的文件和空目录。
     * 使用原子计数器统计清理结果，确保线程安全。
     * </p>
     */
    public void cleanupTempFiles() {
        if (!cleanupEnabled) {
            log.debug("临时文件清理已禁用，跳过清理任务");
            return;
        }

        String tempDirPath = tempDirectoryConfig.getTempDirectory();
        File tempDir = new File(tempDirPath);

        if (!FileUtil.exist(tempDir) || !FileUtil.isDirectory(tempDir)) {
            log.warn("临时目录不存在或不是目录: {}", tempDirPath);
            return;
        }

        log.debug("开始清理临时文件，目录: {}，最大保留时间: {} 小时", tempDirPath, maxRetentionHours);

        Instant cutoffTime = Instant.now().minus(maxRetentionHours, ChronoUnit.HOURS);
        AtomicInteger deletedFiles = new AtomicInteger(0);
        AtomicInteger deletedDirs = new AtomicInteger(0);
        AtomicLong freedSpace = new AtomicLong(0);

        try {
            // 使用hutool遍历目录树删除过期文件
            List<File> allFiles = FileUtil.loopFiles(tempDir);
            for (File file : allFiles) {
                try {
                    if (file.isFile()) {
                        long lastModified = file.lastModified();
                        Instant fileModifiedTime = Instant.ofEpochMilli(lastModified);
                        if (fileModifiedTime.isBefore(cutoffTime)) {
                            long fileSize = file.length();
                            if (FileUtil.del(file)) {
                                deletedFiles.incrementAndGet();
                                freedSpace.addAndGet(fileSize);
                                log.debug("删除过期文件: {} (大小: {} 字节)", file.getPath(), fileSize);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("删除文件失败: {}, 错误: {}", file.getPath(), e.getMessage());
                }
            }
            
            // 删除空目录 - 需要单独处理，因为loopFiles主要用于文件
            deleteEmptyDirectories(tempDir, deletedDirs);

            log.info("临时文件清理完成 - 删除文件: {} 个，删除目录: {} 个，释放空间: {} 字节", 
                    deletedFiles.get(), deletedDirs.get(), freedSpace.get());

        } catch (Exception e) {
            log.error("清理临时文件时发生错误", e);
        }
    }

    /**
     * 递归删除空目录
     * <p>
     * 从指定目录开始，递归遍历所有子目录，删除其中的空目录。
     * 采用深度优先的方式，先处理子目录，再检查父目录是否为空。
     * 使用原子计数器记录删除的目录数量，确保线程安全。
     * </p>
     *
     * @param dir 要检查的目录
     * @param deletedDirs 删除目录计数器，用于统计删除的目录数量
     */
    private void deleteEmptyDirectories(File dir, AtomicInteger deletedDirs) {
        if (!dir.isDirectory()) {
            return;
        }
        
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteEmptyDirectories(child, deletedDirs);
                    // 递归处理后，如果目录变空了，删除它
                    if (FileUtil.isDirEmpty(child)) {
                        try {
                            if (FileUtil.del(child)) {
                                deletedDirs.incrementAndGet();
                                log.debug("删除空目录: {}", child.getPath());
                            }
                        } catch (Exception e) {
                            log.debug("删除目录失败: {}, 错误: {}", child.getPath(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

}