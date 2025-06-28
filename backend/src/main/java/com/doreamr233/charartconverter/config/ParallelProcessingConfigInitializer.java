package com.doreamr233.charartconverter.config;

import com.doreamr233.charartconverter.util.CharArtProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 并行处理配置初始化器
 * 在应用启动后将配置注入到CharArtProcessor中
 */
@Slf4j
@Component
public class ParallelProcessingConfigInitializer {
    
    @Autowired
    private ParallelProcessingConfig parallelConfig;
    
    /**
     * 应用启动完成后初始化配置
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeConfig() {
        CharArtProcessor.setParallelConfig(parallelConfig);
        log.info("并行处理配置已初始化: 最大帧线程数={}, 线程池因子={}, 最小线程数={}, 进度更新间隔={}ms, 像素进度间隔={}", 
                parallelConfig.getMaxFrameThreads(),
                parallelConfig.getThreadPoolFactor(),
                parallelConfig.getMinThreads(),
                parallelConfig.getProgressUpdateInterval(),
                parallelConfig.getPixelProgressInterval());
    }
}