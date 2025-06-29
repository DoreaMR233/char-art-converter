package com.doreamr233.charartconverter.model;

import lombok.Data;

import java.nio.file.Path;

/**
 * WebP帧处理结果类
 * <p>
 * 用于表示WebP动图中单个帧的处理结果。
 * 与FrameProcessResult类似，但专门用于WebP格式的动图处理，
 * 不包含延迟时间信息（WebP的延迟时间由Python服务处理）。
 * </p>
 *
 * @author doreamr233
 */
@Data
public class WebpFrameProcessResult {
    /**
     * 帧索引，表示该帧在WebP动图中的位置
     */
    private final int frameIndex;
    
    /**
     * 字符画帧文件的路径
     */
    private final Path charFramePath;

    /**
     * 构造函数
     * <p>
     * 创建一个WebP帧处理结果对象。
     * </p>
     *
     * @param frameIndex 帧索引，表示该帧在WebP动图中的位置
     * @param charFramePath 字符画帧文件的路径
     */
    public WebpFrameProcessResult(int frameIndex, Path charFramePath) {
        this.frameIndex = frameIndex;
        this.charFramePath = charFramePath;
    }

}