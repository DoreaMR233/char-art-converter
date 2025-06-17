package com.doreamr233.charartconverter.exception;

import com.doreamr233.charartconverter.model.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;



/**
 * 全局异常处理器
 * <p>
 * 使用Spring的@ControllerAdvice注解，捕获并处理应用中的各种异常，
 * 将异常转换为统一格式的API错误响应返回给客户端。
 * </p>
 *
 * @author doreamr233
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param ex      业务异常
     * @param request Web请求
     * @return 包含错误信息的ResponseEntity
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, WebRequest request) {
        log.error("业务异常: {}", ex.getMessage(), ex);
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .details(request.getDescription(false))
                .type(ex.getErrorCode())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理文件类型异常
     *
     * @param ex      文件类型异常
     * @param request Web请求
     * @return 包含错误信息的ResponseEntity
     */
    @ExceptionHandler(FileTypeException.class)
    public ResponseEntity<ApiError> handleFileTypeException(FileTypeException ex, WebRequest request) {
        log.error("文件类型异常: {}", ex.getMessage(), ex);
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .details(request.getDescription(false))
                .type(ex.getErrorCode())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理服务异常
     *
     * @param ex      服务异常
     * @param request Web请求
     * @return 包含错误信息的ResponseEntity
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleServiceException(ServiceException ex, WebRequest request) {
        log.error("服务异常: {}", ex.getMessage(), ex);
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(ex.getMessage())
                .details(request.getDescription(false))
                .type(ex.getErrorCode())
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 已删除IO异常处理器，因为所有IO异常现在都会被转换为ServiceException

    /**
     * 处理文件上传大小超限异常
     *
     * @param ex      文件上传大小超限异常
     * @param request Web请求
     * @return 包含错误信息的ResponseEntity
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, WebRequest request) {
        log.error("文件上传大小超限: {}", ex.getMessage(), ex);
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("上传文件过大，请压缩后重试")
                .details(request.getDescription(false))
                .type("FILE_SIZE_ERROR")
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理所有未捕获的异常
     *
     * @param ex      异常
     * @param request Web请求
     * @return 包含错误信息的ResponseEntity
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAllUncaughtException(Exception ex, WebRequest request) {
        log.error("未捕获的异常: {}", ex.getMessage(), ex);
        
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("服务器内部错误，请稍后重试")
                .details(request.getDescription(false))
                .type("INTERNAL_SERVER_ERROR")
                .build();
        
        return new ResponseEntity<>(apiError, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}