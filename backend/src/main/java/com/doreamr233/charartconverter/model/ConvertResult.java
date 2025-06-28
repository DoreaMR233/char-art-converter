package com.doreamr233.charartconverter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转换结果模型
 * <p>
 * 该类用于表示字符画转换完成后的结果信息，包括转换ID、文件路径、
 * 内容类型、转换完成时间等信息。
 * 使用Lombok注解简化了getter、setter、构造函数等代码。
 * </p>
 *
 * @author doreamr233
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConvertResult {
    /**
     * 转换ID，用于唯一标识一个转换任务
     */
    private String id;
    
    /**
     * 转换后的文件路径
     */
    private String filePath;
    
    /**
     * 文件内容类型，如"text/plain"、"image/png"等
     */
    private String contentType;
    
    /**
     * 转换完成时间戳
     */
    private long timestamp;
    
    /**
     * 转换状态，如"success"、"failed"等
     */
    private String status;
    
    /**
     * 转换结果描述信息
     */
    private String message;

    /**
     * 创建基本转换结果的构造函数
     * <p>
     * 创建一个包含基本转换结果信息的对象，自动设置时间戳为当前时间，
     * 状态为"success"。
     * </p>
     *
     * @param id 转换ID
     * @param filePath 转换后的文件路径
     * @param contentType 文件内容类型
     */
    public ConvertResult(String id, String filePath, String contentType) {
        this.id = id;
        this.filePath = filePath;
        this.contentType = contentType;
        this.timestamp = System.currentTimeMillis();
        this.status = "success";
        this.message = "转换完成";
    }
    
    /**
     * 创建详细转换结果的构造函数
     * <p>
     * 创建一个包含详细转换结果信息的对象，包括状态和描述信息，
     * 自动设置时间戳为当前时间。
     * </p>
     *
     * @param id 转换ID
     * @param filePath 转换后的文件路径
     * @param contentType 文件内容类型
     * @param status 转换状态
     * @param message 转换结果描述信息
     */
    public ConvertResult(String id, String filePath, String contentType, String status, String message) {
        this.id = id;
        this.filePath = filePath;
        this.contentType = contentType;
        this.timestamp = System.currentTimeMillis();
        this.status = status;
        this.message = message;
    }
}