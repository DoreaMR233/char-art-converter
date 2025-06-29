package com.doreamr233.charartconverter.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

/**
 * WebP处理结果类
 * <p>
 * 用于表示WebP动图的完整处理结果，包含所有帧的图像数据、
 * 延迟时间数组、帧数量以及相关的任务ID信息。
 * 该类主要用于WebP动图的解析和后续的字符画转换处理。
 * </p>
 *
 * @author doreamr233
 */
@Data
@Getter
@Setter
public class WebpProcessResult {
    /**
     * 帧数量，表示WebP动图包含的总帧数
     */
    private final int frameCount;
    
    /**
     * 延迟时间数组，每个元素对应一帧的显示延迟时间（毫秒）
     */
    private final int[] delays;
    
    /**
     * 帧图像数组，包含WebP动图的所有帧图像数据
     */
    private final BufferedImage[] frames;
    
    /**
     * 原始任务ID，用于关联原始的转换任务
     */
    private String oriTaskId;
    
    /**
     * WebP任务ID，用于标识WebP处理任务
     */
    private String webPTaskId;

    /**
     * 基本构造函数
     * <p>
     * 创建一个WebP处理结果对象，包含基本的帧信息。
     * </p>
     *
     * @param frameCount 帧数量
     * @param delays 延迟时间数组
     * @param frames 帧图像数组
     */
    public WebpProcessResult(int frameCount, int[] delays, BufferedImage[] frames) {
        this.frameCount = frameCount;
        this.delays = delays;
        this.frames = frames;
    }

    /**
     * 完整构造函数
     * <p>
     * 创建一个WebP处理结果对象，包含完整的帧信息和任务ID。
     * </p>
     *
     * @param frameCount 帧数量
     * @param delays 延迟时间数组
     * @param frames 帧图像数组
     * @param oriTaskId 原始任务ID
     * @param webPTaskId WebP任务ID
     */
    public WebpProcessResult(int frameCount, int[] delays, BufferedImage[] frames, String oriTaskId, String webPTaskId) {
        this.frameCount = frameCount;
        this.delays = delays;
        this.frames = frames;
        this.oriTaskId = oriTaskId;
        this.webPTaskId = webPTaskId;
    }
}
