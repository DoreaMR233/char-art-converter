package com.doreamr233.charartconverter.enums;

/**
 * 关闭原因枚举
 */
public enum CloseReason {
    TASK_COMPLETED,     // 任务正常完成
    HEARTBEAT_TIMEOUT,  // 心跳超时
    ERROR_OCCURRED;      // 发生错误

    /**
     * 解析关闭原因字符串为枚举值
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
     * 解析关闭原因字符串为枚举值(用于python端)
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
