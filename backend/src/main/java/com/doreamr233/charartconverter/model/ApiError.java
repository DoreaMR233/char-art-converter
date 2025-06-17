package com.doreamr233.charartconverter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API错误响应模型
 * <p>
 * 用于统一异常处理中返回给客户端的错误信息格式。
 * 包含错误状态码、时间戳、错误消息和详细描述等信息。
 * </p>
 *
 * @author doreamr233
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    /**
     * HTTP状态码
     */
    private int status;
    
    /**
     * 错误发生的时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 错误的详细描述
     */
    private String details;
    
    /**
     * 错误类型
     */
    private String type;
}