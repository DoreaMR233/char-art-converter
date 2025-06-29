package com.doreamr233.charartconverter.listener;

import com.doreamr233.charartconverter.event.ProgressUpdateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * SSE进度监听器
 * <p>
 * 基于阻塞队列的SSE监听器，用于接收进度更新事件并通过SSE推送给客户端。
 * 使用阻塞队列避免了忙等待，提高了性能和响应性。
 * </p>
 *
 * @author doreamr233
 */
@Slf4j
@Getter
public class SseProgressListener implements ProgressListener {
    
    /**
     * 关注的进度ID
     */
    private final String progressId;
    
    /**
     * SSE发射器
     */
    private final SseEmitter emitter;
    
    /**
     * 事件队列，用于存储待处理的进度更新事件
     */
    private final BlockingQueue<ProgressUpdateEvent> eventQueue = new LinkedBlockingQueue<>();
    
    /**
     * 监听器是否处于活跃状态
     * -- GETTER --
     *  检查监听器是否处于活跃状态
     *

     */
    private volatile boolean active = true;
    
    /**
     * 构造函数
     *
     * @param progressId 关注的进度ID
     * @param emitter SSE发射器
     */
    public SseProgressListener(String progressId, SseEmitter emitter) {
        this.progressId = progressId;
        this.emitter = emitter;
        
        // 设置SSE发射器的回调
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成: {}", progressId);
            this.active = false;
        });
        
        emitter.onTimeout(() -> {
            log.debug("SSE连接超时: {}", progressId);
            this.active = false;
        });
        
        emitter.onError((ex) -> {
            // 检查是否是正常的任务完成导致的连接关闭
            if (ex instanceof java.io.IOException && ex.getMessage() != null && 
                (ex.getMessage().contains("Broken pipe") || ex.getMessage().contains("Connection reset") || 
                 ex.getMessage().contains("Connection closed"))) {
                log.debug("SSE连接正常关闭: {}, 原因: {}", progressId, ex.getMessage());
            } else {
                log.error("SSE连接错误: {}, 错误: {}", progressId, ex.getMessage());
            }
            this.active = false;
        });
    }
    
    /**
     * 处理进度更新事件
     */
    @Override
    public void onProgressUpdate(ProgressUpdateEvent event) {
        if (active && progressId.equals(event.getProgressId())) {
            try {
                // 将事件添加到队列中
                eventQueue.offer(event, 1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("添加事件到队列时被中断: {}", progressId);
            }
        }
    }
    
    /**
     * 等待并获取下一个事件
     * <p>
     * 阻塞等待直到有新事件或超时。
     * </p>
     *
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 进度更新事件，如果超时则返回null
     * @throws InterruptedException 如果等待被中断
     */
    public ProgressUpdateEvent waitForEvent(long timeout, TimeUnit unit) throws InterruptedException {
        return eventQueue.poll(timeout, unit);
    }
    
    /**
     * 检查是否有待处理的事件
     *
     * @return 如果有待处理事件则返回true
     */
    public boolean hasEvents() {
        return !eventQueue.isEmpty();
    }
    
    /**
     * 停用监听器
     */
    public void deactivate() {
        this.active = false;
        // 清空队列
        eventQueue.clear();
    }

}