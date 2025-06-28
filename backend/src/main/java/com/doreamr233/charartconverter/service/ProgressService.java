package com.doreamr233.charartconverter.service;

import com.doreamr233.charartconverter.enums.CloseReason;
import com.doreamr233.charartconverter.event.ProgressUpdateEvent;
import com.doreamr233.charartconverter.model.ConvertResult;
import com.doreamr233.charartconverter.model.ProgressInfo;
import com.doreamr233.charartconverter.listener.ProgressListener;


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
     * 获取指定ID的最新事件信息
     * <p>
     * 根据给定的进度ID获取对应的最新事件信息对象。
     * 如果指定ID的事件信息不存在，则返回null。
     * </p>
     *
     * @param id 进度ID，用于唯一标识一个转换任务
     * @return 事件信息对象，如果不存在则返回null
     */
    ConvertResult getLatestEvent(String id);
    
    /**
     * 检查指定ID是否有新的事件信息
     * <p>
     * 根据给定的进度ID和时间戳检查是否有新的事件信息。
     * </p>
     *
     * @param id 进度ID，用于唯一标识一个转换任务
     * @param lastEventTimestamp 上次获取事件的时间戳
     * @return 如果有新事件则返回true，否则返回false
     */
    boolean hasNewEvent(String id, long lastEventTimestamp);
    
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
    
    /**
     * 发送带关闭原因的关闭事件
     * <p>
     * 创建并发送一个包含关闭信息和关闭原因的进度对象，通知客户端连接即将关闭。
     * 该方法会将进度设置为100%，并标记为已完成。
     * 调用此方法后，会自动安排清理任务，在一段时间后移除相关进度信息。
     * </p>
     *
     * @param id 进度ID，用于唯一标识要关闭的转换任务
     * @param closeReason 关闭原因，用于区分不同类型的关闭事件
     */
    void sendCloseEvent(String id, CloseReason closeReason);
    
    /**
     * 发送转换结果事件
     * <p>
     * 发送包含转换结果信息的进度对象，通知客户端转换已完成并提供结果数据。
     * 该方法会将进度设置为100%，标记为已完成，并包含结果文件路径和内容类型。
     * </p>
     *
     * @param id 进度ID，用于唯一标识转换任务
     * @param filePath 结果文件的相对路径
     * @param contentType 结果文件的内容类型
     */
    void sendConvertResultEvent(String id, String filePath, String contentType);
    
    /**
     * 添加进度监听器
     * <p>
     * 注册一个进度监听器，用于接收进度更新事件。
     * 监听器将根据其关注的进度ID接收相应的事件通知。
     * </p>
     *
     * @param listener 进度监听器
     */
    void addProgressListener(ProgressListener listener);
    
    /**
     * 移除进度监听器
     * <p>
     * 取消注册指定的进度监听器。
     * </p>
     *
     * @param listener 要移除的进度监听器
     */
    void removeProgressListener(ProgressListener listener);
    
    /**
     * 移除指定进度ID的所有监听器
     * <p>
     * 取消注册所有关注指定进度ID的监听器。
     * 通常在进度完成或取消时调用。
     * </p>
     *
     * @param progressId 进度ID
     */
    void removeListenersForProgress(String progressId);

}