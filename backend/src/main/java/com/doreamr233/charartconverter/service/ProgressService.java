package com.doreamr233.charartconverter.service;

import com.doreamr233.charartconverter.model.ProgressInfo;


/**
 * 进度服务接口
 * <p>
 * 该接口定义了处理和管理字符画转换进度信息的方法。
 * 提供了更新进度、获取进度信息以及获取所有进度信息的功能。
 * 用于在字符画转换过程中实时跟踪和报告处理进度。
 * </p>
 *
 * @author doreamr233
 */
public interface ProgressService {

    /**
     * 更新基本进度信息
     * <p>
     * 使用给定的ID、百分比和消息更新进度信息。
     * 如果进度ID不存在，则创建新的进度信息；如果已存在，则更新现有的进度信息。
     * </p>
     *
     * @param id 进度ID，用于唯一标识一个转换任务
     * @param percentage 完成百分比，范围0-100
     * @param message 进度消息，描述当前处理状态
     */
    void updateProgress(String id, double percentage, String message);

    /**
     * 更新详细进度信息
     * <p>
     * 使用给定的ID、百分比、消息、处理阶段和像素处理进度更新进度信息。
     * 提供比基本更新方法更详细的进度跟踪，特别适用于图像处理等需要精确进度报告的场景。
     * </p>
     *
     * @param id 进度ID，用于唯一标识一个转换任务
     * @param percentage 完成百分比，范围0-100
     * @param message 进度消息，描述当前处理状态
     * @param stage 处理阶段名称，如"初始化"、"图片预处理"、"文本转换"等
     * @param currentPixel 当前处理的像素索引，用于详细跟踪图像处理进度
     * @param totalPixels 总像素数，用于计算处理进度
     * @param isDone 项目是否完成
     */
    void updateProgress(String id, double percentage, String message, String stage, int currentPixel, int totalPixels,boolean isDone);

    /**
     * 获取指定ID的进度信息
     * <p>
     * 根据给定的进度ID获取对应的进度信息对象。
     * 如果指定ID的进度信息不存在，则返回一个默认的进度信息对象，表示等待处理状态。
     * </p>
     *
     * @param id 进度ID，用于唯一标识一个转换任务
     * @return 进度信息对象，包含当前处理进度的详细信息
     */
    ProgressInfo getProgress(String id);
    
    /**
     * 发送关闭事件
     * <p>
     * 创建并发送一个包含关闭信息的进度对象，通知客户端连接即将关闭。
     * 该方法会将进度设置为100%，并标记为已完成。
     * 调用此方法后，会自动安排清理任务，在一段时间后移除相关进度信息。
     * </p>
     *
     * @param id 进度ID，用于唯一标识要关闭的转换任务
     */
    void sendCloseEvent(String id);
}