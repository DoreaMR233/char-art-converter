package com.doreamr233.charartconverter.listener;

import com.doreamr233.charartconverter.event.ProgressUpdateEvent;

/**
 * 进度监听器接口
 * <p>
 * 定义了处理进度更新事件的方法。
 * 实现此接口的类可以监听进度变化并做出相应的响应。
 * </p>
 *
 * @author doreamr233
 */
public interface ProgressListener {
    
    /**
     * 处理进度更新事件
     * <p>
     * 当进度信息发生更新时调用此方法。
     * 实现类应该根据事件类型和进度信息做出相应的处理。
     * </p>
     *
     * @param event 进度更新事件，包含进度ID、进度信息和事件类型
     */
    void onProgressUpdate(ProgressUpdateEvent event);
    
    /**
     * 获取监听器关注的进度ID
     * <p>
     * 返回此监听器关注的进度ID。
     * 只有匹配的进度ID的事件才会被传递给此监听器。
     * </p>
     *
     * @return 进度ID，如果返回null则监听所有进度
     */
    String getProgressId();
}