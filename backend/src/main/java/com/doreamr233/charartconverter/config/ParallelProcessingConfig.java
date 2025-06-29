package com.doreamr233.charartconverter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 并行处理配置类
 * <p>
 * 该类用于配置字符画转换过程中的并行处理参数，包括线程池大小、
 * 进度更新间隔、任务超时时间等。通过@ConfigurationProperties注解
 * 从配置文件中读取相关配置项。
 * </p>
 *
 * @author doreamr233
 */
@Data
@Component
@ConfigurationProperties(prefix = "char-art.parallel")
public class ParallelProcessingConfig {
    
    /**
     * 最大并行帧数（同时处理的帧数上限）
     * 用于限制同时处理的动画帧数量，避免内存占用过高
     */
    private int maxFrameThreads = 4;
    
    /**
     * 线程池大小计算因子（CPU核心数的倍数）
     * 用于根据CPU核心数计算合适的线程池大小
     */
    private double threadPoolFactor = 0.5;
    
    /**
     * 最小线程数
     * 保证至少有一个线程可用于处理任务
     */
    private int minThreads = 1;
    
    /**
     * 进度更新间隔（毫秒）
     * 控制进度信息更新的频率，避免过于频繁的更新
     */
    private long progressUpdateInterval = 500L;
    
    /**
     * 像素处理进度报告间隔（每处理多少像素报告一次进度）
     * 用于控制像素级别的进度报告频率
     */
    private int pixelProgressInterval = 1000;
    
    /**
     * 任务执行超时时间（毫秒）
     * 单个任务的最大执行时间，超时后将被强制终止
     */
    private long taskTimeout = 60000L;
    
    /**
     * 进度监听器清理延迟（毫秒）
     * 进度监听器在任务完成后的清理延迟时间
     */
    private long progressCleanupDelay = 60000L;
    
    /**
     * 计算实际使用的线程数
     * <p>
     * 根据CPU核心数、配置的线程池因子、最小线程数和帧数来计算
     * 实际应该使用的线程数量。确保线程数不超过最大并行帧数限制。
     * </p>
     *
     * @param frameCount 需要处理的帧数
     * @return 计算得出的实际线程数
     */
    public int calculateThreadCount(int frameCount) {
        int cpuBasedThreads = Math.max((int) (Runtime.getRuntime().availableProcessors() * threadPoolFactor), minThreads);
        return Math.min(cpuBasedThreads, Math.min(frameCount, maxFrameThreads));
    }
}