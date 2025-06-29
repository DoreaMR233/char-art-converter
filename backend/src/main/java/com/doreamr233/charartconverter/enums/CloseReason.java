package com.doreamr233.charartconverter.enums;

/**
 * 关闭原因枚举
 * <p>
 * 定义SSE连接关闭的各种原因，用于标识连接结束的具体情况。
 * 不同的关闭原因对应不同的日志级别和处理逻辑。
 * </p>
 *
 * @author doreamr233
 */
public enum CloseReason {
    /**
     * 任务正常完成
     * 表示转换任务已成功完成，连接正常关闭
     */
    TASK_COMPLETED,
    
    /**
     * 心跳超时
     * 表示客户端长时间未响应心跳，连接因超时而关闭
     */
    HEARTBEAT_TIMEOUT,
    
    /**
     * 发生错误
     * 表示在处理过程中发生了错误，连接异常关闭
     */
    ERROR_OCCURRED;

    /**
     * 解析关闭原因字符串为枚举值
     * <p>
     * 将字符串形式的关闭原因转换为对应的枚举值。
     * 如果输入为null或不匹配任何已知值，则返回默认的TASK_COMPLETED。
     * </p>
     *
     * @param closeReason 关闭原因字符串
     * @return 对应的CloseReason枚举值
     */
    public static CloseReason parseCloseReason(String closeReason) {
        if (closeReason == null) {
            return CloseReason.TASK_COMPLETED;
        }

        switch (closeReason) {
            case "ERROR_OCCURRED":
                return CloseReason.ERROR_OCCURRED;
            case "HEARTBEAT_TIMEOUT":
                return CloseReason.HEARTBEAT_TIMEOUT;
            case "TASK_COMPLETED":
            default:
                return CloseReason.TASK_COMPLETED;
        }
    }

    /**
     * 解析关闭原因字符串为枚举值(用于Python端)
     * <p>
     * 专门用于解析来自Python WebP处理服务的关闭原因字符串。
     * Python端使用不同的字符串格式（如"error"、"timeout"、"completed"），
     * 此方法将这些格式转换为Java枚举值。
     * </p>
     *
     * @param closeReason Python端发送的关闭原因字符串
     * @return 对应的CloseReason枚举值
     */
    public static CloseReason parseCloseReasonByPython(String closeReason) {
        if (closeReason == null) {
            return CloseReason.TASK_COMPLETED;
        }

        switch (closeReason) {
            case "error":
                return CloseReason.ERROR_OCCURRED;
            case "timeout":
                return CloseReason.HEARTBEAT_TIMEOUT;
            case "completed":
            default:
                return CloseReason.TASK_COMPLETED;
        }
    }
}
