package com.doreamr233.charartconverter.model;

import lombok.Data;

import java.nio.file.Path;

/**
 * 帧处理结果类
 */
@Data
public class FrameProcessResult {
    private final int frameIndex;
    private final Path charFramePath;
    private final int delay;

    public FrameProcessResult(int frameIndex, Path charFramePath, int delay) {
        this.frameIndex = frameIndex;
        this.charFramePath = charFramePath;
        this.delay = delay;
    }
}
