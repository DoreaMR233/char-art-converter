package com.doreamr233.charartconverter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 并行处理配置类
 * 用于配置字符画转换过程中的并行处理参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "char-art.parallel")
public class ParallelProcessingConfig {
    
    /**
     * 最大并行帧数（同时处理的帧数上限）
     */
    private int maxFrameThreads = 4;
    
    /**
     * 线程池大小计算因子（CPU核心数的倍数）
     */
    private double threadPoolFactor = 0.5;
    
    /**
     * 最小线程数
     */
    private int minThreads = 1;
    
    /**
     * 进度更新间隔（毫秒）
     */
    private long progressUpdateInterval = 500L;
    
    /**
     * 像素处理进度报告间隔（每处理多少像素报告一次进度）
     */
    private int pixelProgressInterval = 1000;
    
    /**
     * 任务执行超时时间（毫秒）
     */
    private long taskTimeout = 60000L;
    
    /**
     * 进度监听器清理延迟（毫秒）
     */
    private long progressCleanupDelay = 60000L;
    
    /**
     * 计算实际使用的线程数
     * @param frameCount 帧数
     * @return 实际线程数
     */
    public int calculateThreadCount(int frameCount) {
        int cpuBasedThreads = Math.max((int) (Runtime.getRuntime().availableProcessors() * threadPoolFactor), minThreads);
        return Math.min(cpuBasedThreads, Math.min(frameCount, maxFrameThreads));
    }
}