package com.doreamr233.charartconverter.service.impl;

import com.doreamr233.charartconverter.model.ProgressInfo;
import com.doreamr233.charartconverter.service.ProgressService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进度服务实现类
 * <p>
 * 该类实现了ProgressService接口，提供了字符画转换进度的管理和跟踪功能。
 * 使用ConcurrentHashMap存储进度信息，确保在多线程环境下的线程安全。
 * 每个进度ID对应一个进度信息列表，支持进度的历史记录和实时更新。
 * 当转换任务完成时，会自动安排清理任务，防止内存泄漏。
 * </p>
 *
 * @author doreamr233
 */
@Getter
@Service
@Slf4j
public class ProgressServiceImpl implements ProgressService {

    /**
     * -- GETTER --
     *  获取进度信息合集
     *
     */
    // 使用ConcurrentHashMap存储进度信息，线程安全
    private final Map<String, List<ProgressInfo>> progressMap = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     * <p>
     * 实现基本进度信息的更新。
     * 如果指定ID的进度信息不存在，则创建一个新的进度信息对象。
     * 更新百分比、消息和时间戳，并将更新后的进度信息存入映射。
     * </p>
     */
    @Override
    public void updateProgress(String id, double percentage, String message) {
        // 使用默认阶段"初始化"
        updateProgress(id, percentage, message, "初始化", 0, 1,false);
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
    public void updateProgress(String id, double percentage, String message, String stage, int currentPixel, int totalPixels,boolean isDone) {
        ProgressInfo progressInfo = new ProgressInfo(id, percentage, message, stage, currentPixel, totalPixels,isDone);
        if(progressMap.containsKey(id)){
            progressMap.get(id).add(progressInfo);
        }else{
            CopyOnWriteArrayList<ProgressInfo> progressInfoList = new CopyOnWriteArrayList<>();
            progressInfoList.add(progressInfo);
            progressMap.put(id, progressInfoList);
        }
        
        // 如果进度达到100%，设置一个定时任务在一段时间后清理该进度信息
        if (isDone) {
            scheduleCleanup(id);
        }
    }

    @Override
    public ProgressInfo getProgress(String id) {
        CopyOnWriteArrayList<ProgressInfo> progressInfoList = (CopyOnWriteArrayList<ProgressInfo>) progressMap.getOrDefault(id, new CopyOnWriteArrayList<>());
        if(!progressInfoList.isEmpty()){
            return progressInfoList.get(progressInfoList.size()-1);
        }else{
            return new ProgressInfo(id, 0, "等待处理");
        }
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
     * {@inheritDoc}
     * <p>
     * 发送关闭事件，通知客户端连接即将关闭。
     * 创建一个特殊的进度信息对象，标记为已完成并包含关闭消息。
     * 将此进度信息添加到进度列表中，并安排清理任务。
     * </p>
     */
    @Override
    public void sendCloseEvent(String id) {
        log.info("发送关闭事件: {}", id);
        
        // 创建一个包含关闭信息的进度对象
        ProgressInfo closeInfo = new ProgressInfo(id, 100, "关闭链接中", "关闭连接", 0, 0, true);
        
        // 添加到进度列表
        if (progressMap.containsKey(id)) {
            progressMap.get(id).add(closeInfo);
        } else {
            CopyOnWriteArrayList<ProgressInfo> progressInfoList = new CopyOnWriteArrayList<>();
            progressInfoList.add(closeInfo);
            progressMap.put(id, progressInfoList);
        }
        
        // 安排清理任务
        scheduleCleanup(id);
    }

}