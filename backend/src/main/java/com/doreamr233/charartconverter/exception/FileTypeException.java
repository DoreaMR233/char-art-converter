package com.doreamr233.charartconverter.exception;

/**
 * 文件类型异常类
 * <p>
 * 用于表示文件类型相关的异常情况，如不支持的文件格式、文件处理错误等。
 * 继承自BusinessException，便于统一异常处理。
 * </p>
 *
 * @author doreamr233
 */
public class FileTypeException extends BusinessException {

    /**
     * 构造函数
     *
     * @param message 错误消息
     */
    public FileTypeException(String message) {
        super("FILE_TYPE_ERROR", message);
    }

    /**
     * 构造函数
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public FileTypeException(String message, Throwable cause) {
        super("FILE_TYPE_ERROR", message, cause);
    }
}