package com.doreamr233.charartconverter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 进度信息模型
 * <p>
 * 该类用于表示字符画转换过程中的进度信息，包括完成百分比、当前状态消息、
 * 处理阶段、当前处理的像素索引和总像素数等信息。
 * 使用Lombok注解简化了getter、setter、构造函数等代码。
 * </p>
 *
 * @author doreamr233
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressInfo {
    /**
     * 进度ID，用于唯一标识一个转换任务
     */
    private String id;
    
    /**
     * 完成百分比，范围0-100
     */
    private double percentage;
    
    /**
     * 进度消息，描述当前处理状态
     */
    private String message;
    
    /**
     * 时间戳，记录进度信息创建或更新的时间
     */
    private long timestamp;
    
    /**
     * 处理阶段名称，如"初始化"、"图片预处理"、"文本转换"等
     */
    private String stage;
    
    /**
     * 当前处理的像素索引，用于详细跟踪图像处理进度
     */
    private int currentPixel;
    
    /**
     * 总像素数，用于计算处理进度
     */
    private int totalPixels;

    /**
     * 项目是否完成
     */
    private boolean isDone;

    /**
     * 创建基本进度信息的构造函数
     * <p>
     * 创建一个包含基本进度信息的对象，自动设置时间戳为当前时间，
     * 阶段为"初始化"，像素计数为0，进度条状态值为-1。
     * </p>
     *
     * @param id 进度ID
     * @param percentage 完成百分比
     * @param message 进度消息
     */
    public ProgressInfo(String id, double percentage, String message) {
        this.id = id;
        this.percentage = percentage;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.stage = "初始化";
        this.currentPixel = 0;
        this.totalPixels = 0;
        this.isDone = false;
    }
    
    /**
     * 创建详细进度信息的构造函数
     * <p>
     * 创建一个包含详细进度信息的对象，包括处理阶段和像素处理进度，
     * 自动设置时间戳为当前时间，进度条状态值为-1。
     * </p>
     *
     * @param id 进度ID
     * @param percentage 完成百分比
     * @param message 进度消息
     * @param stage 处理阶段名称
     * @param currentPixel 当前处理的像素索引
     * @param totalPixels 总像素数
     */
    public ProgressInfo(String id, double percentage, String message, String stage, int currentPixel, int totalPixels,boolean isDone) {
        this.id = id;
        this.percentage = percentage;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.stage = stage;
        this.currentPixel = currentPixel;
        this.totalPixels = totalPixels;
        this.isDone = isDone;
    }
}