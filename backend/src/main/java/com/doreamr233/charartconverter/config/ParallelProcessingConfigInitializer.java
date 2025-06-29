package com.doreamr233.charartconverter.config;

import com.doreamr233.charartconverter.util.CharArtProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 并行处理配置初始化器
 * <p>
 * 该类负责在Spring Boot应用启动完成后，将并行处理配置注入到
 * CharArtProcessor工具类中。通过监听ApplicationReadyEvent事件
 * 来确保在所有Bean初始化完成后执行配置注入操作。
 * </p>
 *
 * @author doreamr233
 */
@Slf4j
@Component
public class ParallelProcessingConfigInitializer {
    
    /**
     * 并行处理配置对象
     * 包含线程池大小、进度更新间隔等配置参数
     */
    @Resource
    private ParallelProcessingConfig parallelConfig;
    
    /**
     * 应用启动完成后初始化配置
     * <p>
     * 监听ApplicationReadyEvent事件，在应用完全启动后将并行处理配置
     * 注入到CharArtProcessor中，并记录配置信息到日志。
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeConfig() {
        CharArtProcessor.setParallelConfig(parallelConfig);
        log.debug("并行处理配置初始化完成: 最大帧线程数={}, 线程池因子={}, 最小线程数={}, 进度更新间隔={}ms, 像素进度间隔={}", 
                parallelConfig.getMaxFrameThreads(),
                parallelConfig.getThreadPoolFactor(),
                parallelConfig.getMinThreads(),
                parallelConfig.getProgressUpdateInterval(),
                parallelConfig.getPixelProgressInterval());
    }
}