package com.doreamr233.charartconverter.service.impl;

import com.doreamr233.charartconverter.config.ParallelProcessingConfig;
import com.doreamr233.charartconverter.enums.CloseReason;
import com.doreamr233.charartconverter.enums.EventType;
import com.doreamr233.charartconverter.event.ProgressUpdateEvent;
import com.doreamr233.charartconverter.listener.ProgressListener;
import com.doreamr233.charartconverter.model.ConvertResult;
import com.doreamr233.charartconverter.model.ProgressInfo;
import com.doreamr233.charartconverter.service.ProgressService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
    
    @Resource
    private ParallelProcessingConfig parallelConfig;

    /**
     * 进度信息存储映射，用于存储普通进度更新
     * 线程安全的ConcurrentHashMap，键为进度ID，值为进度信息列表
     */
    private final Map<String, List<ProgressInfo>> progressMap = new ConcurrentHashMap<>();
    
    /**
     * 事件信息存储映射，用于存储特殊事件（如转换结果、关闭事件等）
     * 线程安全的ConcurrentHashMap，键为进度ID，值为事件信息列表
     */
    private final Map<String, List<ConvertResult>> eventMap = new ConcurrentHashMap<>();
    
    /**
     * 进度监听器列表
     * 线程安全的CopyOnWriteArrayList，存储所有注册的进度监听器
     */
    private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * 临时目录清理回调函数
     * 用于在特定条件下清理临时目录
     */
    private Consumer<String> tempDirectoryCleanupCallback;

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
    public void updateProgress(String id, double percentage, String message, String stage, int currentPixel, int totalPixels, boolean isDone) {
        ProgressInfo progressInfo = new ProgressInfo(id, percentage, message, stage, currentPixel, totalPixels, isDone);
        
        // 添加到进度信息映射
        if (progressMap.containsKey(id)) {
            progressMap.get(id).add(progressInfo);
        } else {
            CopyOnWriteArrayList<ProgressInfo> progressInfoList = new CopyOnWriteArrayList<>();
            progressInfoList.add(progressInfo);
            progressMap.put(id, progressInfoList);
        }
        
        // 通知监听器
        notifyListeners(id, progressInfo, EventType.PROGRESS_UPDATE);
        
        // 如果进度达到100%，设置一个定时任务在一段时间后清理该进度信息
        if (isDone) {
            scheduleCleanup(id);
        }
    }

    @Override
    public ProgressInfo getProgress(String id) {
        List<ProgressInfo> progressInfoList = progressMap.getOrDefault(id, new CopyOnWriteArrayList<>());
        if (!progressInfoList.isEmpty()) {
            return progressInfoList.get(progressInfoList.size() - 1);
        } else {
            return new ProgressInfo(id, 0, "等待处理");
        }
    }
    
    @Override
    public ConvertResult getLatestEvent(String id) {
        List<ConvertResult> eventInfoList = eventMap.getOrDefault(id, new CopyOnWriteArrayList<>());
        if (!eventInfoList.isEmpty()) {
            return eventInfoList.get(eventInfoList.size() - 1);
        }
        return null;
    }
    
    @Override
    public boolean hasNewEvent(String id, long lastEventTimestamp) {
        ConvertResult latestEvent = getLatestEvent(id);
        return latestEvent != null && latestEvent.getTimestamp() > lastEventTimestamp;
    }

    /**
     * 安排清理任务
     * <p>
     * 创建一个新线程，在配置的延迟时间后清理指定ID的进度信息和事件信息。
     * 这个方法用于防止内存泄漏，确保已完成的任务信息能够及时清理。
     * 清理延迟时间由ParallelProcessingConfig配置，默认为60秒。
     * </p>
     *
     * @param id 要清理的进度ID
     */
    private void scheduleCleanup(String id) {
        new Thread(() -> {
            try {
                long cleanupDelay = parallelConfig != null ? parallelConfig.getProgressCleanupDelay() : 60000L;
                Thread.sleep(cleanupDelay); // 配置的延迟时间后清理
                progressMap.remove(id);
                eventMap.remove(id);
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
     * 将此进度信息添加到事件列表中，并安排清理任务。
     * </p>
     */
    @Override
    public void sendCloseEvent(String id) {
        sendCloseEvent(id, CloseReason.TASK_COMPLETED);
    }
    
    /**
     * 发送带关闭原因的关闭事件
     *
     * @param id 进度ID
     * @param closeReason 关闭原因
     */
    @Override
    public void sendCloseEvent(String id, CloseReason closeReason) {
        String reasonMessage = getCloseReasonMessage(closeReason);
        log.debug("发送关闭事件: {}, 原因: {}", id, reasonMessage);
        
        // 在特定关闭原因下清理临时目录
        if (tempDirectoryCleanupCallback != null && 
            (closeReason == CloseReason.HEARTBEAT_TIMEOUT || closeReason == CloseReason.ERROR_OCCURRED)) {
            try {
                tempDirectoryCleanupCallback.accept(id);
                log.debug("已调用临时目录清理回调: {}, 原因: {}", id, reasonMessage);
            } catch (Exception e) {
                log.error("临时目录清理回调执行失败: {}, 原因: {}", id, e.getMessage(), e);
            }
        }
        
        // 创建一个包含关闭信息的进度对象
        ProgressInfo closeInfo = new ProgressInfo(id, 100, reasonMessage, "关闭连接", 0, 0, true);
        
        // 添加到事件信息映射
        if (progressMap.containsKey(id)) {
            progressMap.get(id).add(closeInfo);
        } else {
            CopyOnWriteArrayList<ProgressInfo> eventInfoList = new CopyOnWriteArrayList<>();
            eventInfoList.add(closeInfo);
            progressMap.put(id, eventInfoList);
        }
        
        // 安排清理任务
        scheduleCleanup(id);
        
        // 通知监听器
        notifyListeners(id, closeInfo, EventType.CLOSE_EVENT, closeReason);
        
        log.debug("关闭事件已发送: {}, 原因: {}", id, reasonMessage);
    }
    
    /**
     * 根据关闭原因获取对应的消息
     * <p>
     * 将CloseReason枚举值转换为用户友好的中文消息。
     * 用于在发送关闭事件时提供清晰的关闭原因说明。
     * </p>
     *
     * @param closeReason 关闭原因枚举值
     * @return 对应的中文消息
     */
    private String getCloseReasonMessage(CloseReason closeReason) {
        switch (closeReason) {
            case TASK_COMPLETED:
                return "任务已完成";
            case HEARTBEAT_TIMEOUT:
                return "连接超时关闭";
            case ERROR_OCCURRED:
                return "发生错误，连接关闭";
            default:
                return "连接关闭";
        }
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * 发送转换结果事件，通知客户端转换已完成并提供结果数据。
     * 创建一个包含转换结果信息的进度对象，标记为已完成并包含结果文件路径和内容类型。
     * 将此进度信息添加到事件列表中，并安排清理任务。
     * </p>
     */
    @Override
    public void sendConvertResultEvent(String id, String filePath, String contentType) {
        log.debug("发送转换结果事件: {}, 文件路径: {}, 内容类型: {}", id, filePath, contentType);
        
        // 创建转换结果对象
        ConvertResult convertResult = new ConvertResult(id, filePath, contentType);
        log.debug("转换结果: {}", convertResult);
        // 添加到事件信息映射
        if (eventMap.containsKey(id)) {
            eventMap.get(id).add(convertResult);
        } else {
            CopyOnWriteArrayList<ConvertResult> eventInfoList = new CopyOnWriteArrayList<>();
            eventInfoList.add(convertResult);
            eventMap.put(id, eventInfoList);
        }
        
        // 安排清理任务
        scheduleCleanup(id);
        
        // 通知监听器 - 使用ConvertResult构造函数
        notifyListenersWithConvertResult(id, convertResult);
    }
    
    /**
     * 添加进度监听器
     */
    @Override
    public void addProgressListener(ProgressListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            log.debug("添加进度监听器: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 移除进度监听器
     */
    @Override
    public void removeProgressListener(ProgressListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            log.debug("移除进度监听器: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 移除指定进度ID的所有监听器
     */
    @Override
    public void removeListenersForProgress(String progressId) {
        listeners.removeIf(listener -> progressId.equals(listener.getProgressId()));
        log.debug("移除进度ID {} 的所有监听器", progressId);
    }
    
    /**
     * 通知所有相关的监听器
     * <p>
     * 创建进度更新事件并通知所有关注该进度ID的监听器。
     * 监听器可以关注所有进度（progressId为null）或特定进度ID。
     * 如果通知过程中发生异常，会记录错误日志但不会中断其他监听器的通知。
     * </p>
     *
     * @param progressId 进度ID
     * @param progressInfo 进度信息
     * @param eventType 事件类型
     */
    private void notifyListeners(String progressId, ProgressInfo progressInfo, EventType eventType) {
        ProgressUpdateEvent event = new ProgressUpdateEvent(progressId, progressInfo, eventType);
        
        for (ProgressListener listener : listeners) {
            // 如果监听器关注所有进度或关注特定进度ID
            if (listener.getProgressId() == null || progressId.equals(listener.getProgressId())) {
                try {
                    listener.onProgressUpdate(event);
                } catch (Exception e) {
                    log.error("通知监听器时发生错误: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 通知所有相关的监听器（带关闭原因）
     * <p>
     * 创建包含关闭原因的进度更新事件并通知所有关注该进度ID的监听器。
     * 这是notifyListeners方法的重载版本，专门用于处理关闭事件。
     * 监听器可以根据关闭原因执行不同的处理逻辑。
     * </p>
     * 
     * @param progressId 进度ID
     * @param progressInfo 进度信息
     * @param eventType 事件类型
     * @param closeReason 关闭原因
     */
    private void notifyListeners(String progressId, ProgressInfo progressInfo, EventType eventType, CloseReason closeReason) {
        ProgressUpdateEvent event = new ProgressUpdateEvent(progressId, progressInfo, eventType, closeReason);
        
        for (ProgressListener listener : listeners) {
            // 如果监听器关注所有进度或关注特定进度ID
            if (listener.getProgressId() == null || progressId.equals(listener.getProgressId())) {
                try {
                    listener.onProgressUpdate(event);
                } catch (Exception e) {
                    log.error("通知监听器时发生错误: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 通知所有相关的监听器 - 转换结果事件专用
     * <p>
     * 创建包含转换结果的进度更新事件并通知所有关注该进度ID的监听器。
     * 这是专门用于转换结果事件的通知方法，使用ConvertResult对象而不是ProgressInfo。
     * 监听器可以从事件中获取转换结果的详细信息，如文件路径和内容类型。
     * </p>
     *
     * @param progressId    进度ID
     * @param convertResult 转换结果信息
     */
    private void notifyListenersWithConvertResult(String progressId, ConvertResult convertResult) {
        ProgressUpdateEvent event = new ProgressUpdateEvent(progressId, convertResult, EventType.CONVERT_RESULT);
        
        for (ProgressListener listener : listeners) {
            // 如果监听器关注所有进度或关注特定进度ID
            if (listener.getProgressId() == null || progressId.equals(listener.getProgressId())) {
                try {
                    listener.onProgressUpdate(event);
                } catch (Exception e) {
                    log.error("通知监听器时发生错误: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * 设置临时目录清理回调函数。
     * 当收到错误关闭事件或心跳超时事件时，会调用此回调进行清理。
     * </p>
     */
    @Override
    public void setTempDirectoryCleanupCallback(Consumer<String> cleanupCallback) {
        this.tempDirectoryCleanupCallback = cleanupCallback;
        log.debug("已设置临时目录清理回调");
    }
}