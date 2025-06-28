package com.doreamr233.charartconverter.model;

import lombok.Data;

import java.nio.file.Path;

/**
 * WebP帧处理结果类
 */
@Data
public class WebpFrameProcessResult {
    private final int frameIndex;
    private final Path charFramePath;

    public WebpFrameProcessResult(int frameIndex, Path charFramePath) {
        this.frameIndex = frameIndex;
        this.charFramePath = charFramePath;
    }

}