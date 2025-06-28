package com.doreamr233.charartconverter.event;

import com.doreamr233.charartconverter.enums.CloseReason;
import com.doreamr233.charartconverter.enums.EventType;
import com.doreamr233.charartconverter.model.ConvertResult;
import com.doreamr233.charartconverter.model.ProgressInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 进度更新事件
 * <p>
 * 当进度信息发生更新时触发的事件对象。
 * 包含进度ID和更新后的进度信息。
 * </p>
 *
 * @author doreamr233
 */
@Data
@NoArgsConstructor
public class ProgressUpdateEvent {
    
    /**
     * 进度ID，用于标识特定的转换任务
     */
    private String progressId;
    
    /**
     * 更新后的进度信息
     */
    private ProgressInfo progressInfo;
    
    /**
     * 转换结果信息（仅在CONVERT_RESULT事件类型时使用）
     */
    private ConvertResult convertResult;
    
    /**
     * 事件类型，用于区分普通进度更新和特殊事件
     */
    private EventType eventType;
    
    /**
     * 关闭原因，仅在CLOSE_EVENT事件类型时使用
     */
    private CloseReason closeReason;
    
    /**
     * 构造函数 - 用于进度更新事件
     */
    public ProgressUpdateEvent(String progressId, ProgressInfo progressInfo, EventType eventType) {
        this.progressId = progressId;
        this.progressInfo = progressInfo;
        this.eventType = eventType;
    }
    
    /**
     * 构造函数 - 用于关闭事件
     */
    public ProgressUpdateEvent(String progressId, ProgressInfo progressInfo, EventType eventType, CloseReason closeReason) {
        this.progressId = progressId;
        this.progressInfo = progressInfo;
        this.eventType = eventType;
        this.closeReason = closeReason;
    }
    
    /**
     * 构造函数 - 用于转换结果事件
     */
    public ProgressUpdateEvent(String progressId, ConvertResult convertResult, EventType eventType) {
        this.progressId = progressId;
        this.convertResult = convertResult;
        this.eventType = eventType;
    }
    
    /**
     * 全参数构造函数
     */
    public ProgressUpdateEvent(String progressId, ProgressInfo progressInfo, ConvertResult convertResult, EventType eventType) {
        this.progressId = progressId;
        this.progressInfo = progressInfo;
        this.convertResult = convertResult;
        this.eventType = eventType;
    }


    

}