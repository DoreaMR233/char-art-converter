package com.doreamr233.charartconverter.exception;

/**
 * 服务异常类
 * <p>
 * 用于表示服务层处理过程中的异常情况，如字符画转换服务错误、WebP处理服务错误等。
 * 继承自BusinessException，便于统一异常处理。
 * </p>
 *
 * @author doreamr233
 */
public class ServiceException extends BusinessException {

    /**
     * 构造函数
     *
     * @param message 错误消息
     */
    public ServiceException(String message) {
        super("SERVICE_ERROR", message);
    }

    /**
     * 构造函数
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ServiceException(String message, Throwable cause) {
        super("SERVICE_ERROR", message, cause);
    }
    
    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public ServiceException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原始异常
     */
    public ServiceException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}