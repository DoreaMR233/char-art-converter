package com.doreamr233.charartconverter.config;

import com.doreamr233.charartconverter.service.TempFileCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 定时任务配置类
 * <p>
 * 配置和管理应用程序的定时任务，包括临时文件清理等。
 * 使用Spring的@Scheduled注解实现定时任务调度。
 * </p>
 *
 * @author doreamr233
 */
@Configuration
@Slf4j
public class SchedulerConfig {

    /**
     * 定时任务执行器
     * <p>
     * 负责执行各种定时任务，如临时文件清理等。
     * </p>
     */
    @Component
    @Slf4j
    public static class ScheduledTasks {

        /**
         * 临时文件清理服务
         * 提供临时文件和目录的清理功能
         */
        @Resource
        private TempFileCleanupService tempFileCleanupService;

        /**
         * 临时文件清理定时任务
         * <p>
         * 每小时执行一次临时文件清理任务，删除超过最大保留时间的文件和目录。
         * 使用cron表达式 "0 0 * * * ?" 表示每小时的整点执行。
         * </p>
         * 
         * Cron表达式说明:
         * - 秒: 0 (第0秒)
         * - 分: 0 (第0分)
         * - 时: * (每小时)
         * - 日: * (每天)
         * - 月: * (每月)
         * - 周: ? (不指定)
         */
        @Scheduled(cron = "0 0 * * * ?")
        public void cleanupTempFiles() {
            try {
                log.debug("开始执行定时清理临时文件任务");
                tempFileCleanupService.cleanupTempFiles();
                log.debug("定时清理临时文件任务执行完成");
            } catch (Exception e) {
                log.error("执行定时清理临时文件任务时发生错误", e);
            }
        }

        /**
         * 应用启动后延迟执行的清理任务
         * <p>
         * 在应用启动5分钟后执行一次清理任务，然后每小时执行一次。
         * 这样可以在应用启动后及时清理可能存在的历史临时文件。
         * </p>
         */
        @Scheduled(initialDelay = 5 * 60 * 1000, fixedRate = 60 * 60 * 1000)
        public void initialCleanupTempFiles() {
            try {
                log.debug("开始执行初始化清理临时文件任务");
                tempFileCleanupService.cleanupTempFiles();
                log.debug("初始化清理临时文件任务执行完成");
            } catch (Exception e) {
                log.error("执行初始化清理临时文件任务时发生错误", e);
            }
        }
    }
}