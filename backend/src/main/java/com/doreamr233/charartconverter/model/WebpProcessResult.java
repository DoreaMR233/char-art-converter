package com.doreamr233.charartconverter.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

/**
 * WebP处理结果类
 */
@Data
@Getter
@Setter
public class WebpProcessResult {
    private final int frameCount;
    private final int[] delays;
    private final BufferedImage[] frames;
    private String oriTaskId;
    private String webPTaskId;

    public WebpProcessResult(int frameCount, int[] delays, BufferedImage[] frames) {
        this.frameCount = frameCount;
        this.delays = delays;
        this.frames = frames;
    }

    public WebpProcessResult(int frameCount, int[] delays, BufferedImage[] frames, String oriTaskId, String webPTaskId) {
        this.frameCount = frameCount;
        this.delays = delays;
        this.frames = frames;
        this.oriTaskId = oriTaskId;
        this.webPTaskId = webPTaskId;
    }
}
