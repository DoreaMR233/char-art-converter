package com.doreamr233.charartconverter.exception;

import lombok.Getter;

/**
 * 业务异常类
 * <p>
 * 用于表示业务逻辑处理过程中的异常情况。
 * 包含错误码和错误消息，便于统一异常处理和客户端错误提示。
 * </p>
 *
 * @author doreamr233
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 构造函数
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        this("BUSINESS_ERROR", message);
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原始异常
     */
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}