package com.doreamr233.charartconverter.model;

import lombok.Data;

import java.nio.file.Path;

/**
 * 帧处理结果类
 * <p>
 * 用于表示动图（如GIF）中单个帧的处理结果。
 * 包含帧索引、字符画文件路径和帧延迟时间等信息。
 * </p>
 *
 * @author doreamr233
 */
@Data
public class FrameProcessResult {
    /**
     * 帧索引，表示该帧在动图中的位置
     */
    private final int frameIndex;
    
    /**
     * 字符画帧文件的路径
     */
    private final Path charFramePath;
    
    /**
     * 帧延迟时间（毫秒），控制动画播放速度
     */
    private final int delay;

    /**
     * 构造函数
     * <p>
     * 创建一个帧处理结果对象，包含帧的基本信息。
     * </p>
     *
     * @param frameIndex 帧索引，表示该帧在动图中的位置
     * @param charFramePath 字符画帧文件的路径
     * @param delay 帧延迟时间（毫秒）
     */
    public FrameProcessResult(int frameIndex, Path charFramePath, int delay) {
        this.frameIndex = frameIndex;
        this.charFramePath = charFramePath;
        this.delay = delay;
    }
}
