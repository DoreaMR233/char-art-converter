package com.doreamr233.charartconverter.enums;

/**
 * 事件类型枚举
 * <p>
 * 定义SSE（Server-Sent Events）中使用的各种事件类型。
 * 用于区分不同类型的进度更新和状态通知，便于客户端进行相应的处理。
 * </p>
 *
 * @author doreamr233
 */
public enum EventType {
    /**
     * 普通进度更新
     * 表示转换任务的进度信息更新，包含当前完成百分比等信息
     */
    PROGRESS_UPDATE,
    
    /**
     * 转换结果事件
     * 表示转换任务已完成，包含最终的转换结果数据
     */
    CONVERT_RESULT,
    
    /**
     * 关闭事件
     * 表示SSE连接即将关闭，通知客户端连接结束的原因
     */
    CLOSE_EVENT
}