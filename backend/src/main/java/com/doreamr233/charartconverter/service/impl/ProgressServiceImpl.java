package com.doreamr233.charartconverter.service.impl;

import com.doreamr233.charartconverter.model.ProgressInfo;
import com.doreamr233.charartconverter.service.ProgressService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度服务实现类
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    // 使用ConcurrentHashMap存储进度信息，线程安全
    private final Map<String, ProgressInfo> progressMap = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     * <p>
     * 实现基本进度信息的更新。
     * 如果指定ID的进度信息不存在，则创建一个新的进度信息对象。
     * 更新百分比、消息和时间戳，并将更新后的进度信息存入映射。
     * </p>
     */
    @Override
    public void updateProgress(String id, int percentage, String message) {
        // 确保百分比在0-100范围内
        int validPercentage = Math.max(0, Math.min(100, percentage));
        ProgressInfo progressInfo = new ProgressInfo(id, validPercentage, message);
        progressInfo.setCount(progressInfo.getPercentage()+1);
        progressMap.put(id, progressInfo);
        
        // 如果进度达到100%，设置一个定时任务在一段时间后清理该进度信息
        if (validPercentage >= 100) {
            scheduleCleanup(id);
        }
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * 实现详细进度信息的更新。
     * 除了基本信息外，还更新处理阶段、当前像素、总像素数和计数器。
     * 计数器用于跟踪进度更新的次数，可用于前端判断是否有新的进度更新。
     * </p>
     */
    @Override
    public void updateProgress(String id, int percentage, String message, String stage, int currentPixel, int totalPixels) {
        // 确保百分比在0-100范围内
        int validPercentage = Math.max(0, Math.min(100, percentage));
        ProgressInfo progressInfo = new ProgressInfo(id, validPercentage, message, stage, currentPixel, totalPixels);
        progressInfo.setCount(progressInfo.getPercentage()+1);
        progressMap.put(id, progressInfo);
        
        // 如果进度达到100%，设置一个定时任务在一段时间后清理该进度信息
        if (validPercentage >= 100) {
            scheduleCleanup(id);
        }
    }

    @Override
    public ProgressInfo getProgress(String id) {
        return progressMap.getOrDefault(id, new ProgressInfo(id, 0, "等待处理"));
    }
    
    /**
     * 定时清理已完成的进度信息
     * <p>
     * 每小时执行一次，清理完成超过30分钟的进度信息。
     * 通过移除已完成且超过指定时间的进度信息，防止内存泄漏。
     * 只清理百分比达到100%且时间戳早于30分钟前的进度信息。
     * </p>
     */
    private void scheduleCleanup(String id) {
        new Thread(() -> {
            try {
                Thread.sleep(60000); // 60秒后清理
                progressMap.remove(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 获取进度信息合集
     * @return 进度信息合集
     */
    public Map<String, ProgressInfo> getProgressMap() {
        return progressMap;
    }
}