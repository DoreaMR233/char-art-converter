package com.doreamr233.charartconverter.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 字符画服务接口
 * <p>
 * 该接口定义了将图像转换为字符画的核心功能。
 * 提供了图像转换和获取字符文本的方法，支持静态图像和GIF动图的处理。
 * 实现类负责处理图像转换的具体逻辑，包括图像处理、字符映射和结果缓存等。
 * </p>
 *
 * @author doreamr233
 */
public interface CharArtService {

    /**
     * 将图片转换为字符画
     * <p>
     * 接收上传的图片文件，将其转换为字符画，并返回转换后的图片字节数组。
     * 支持静态图像（如JPG、PNG）和动态图像（如GIF）的处理。
     * 转换过程中会通过进度ID更新处理进度信息。
     * 所有临时文件都会保存在指定的临时目录中。
     * </p>
     *
     * @param imageStream 图片输入流
     * @param filename 原始文件名
     * @param density 字符密度 (low, medium, high)
     * @param colorMode 颜色模式 (grayscale, color)
     * @param progressId 进度ID
     * @param limitSize 是否限制字符画的最大宽度和高度
     * @param tempDir 专用临时目录路径
     * @return 字符画图片的字节数组
     */
    byte[] convertToCharArt(InputStream imageStream, String filename, String density, String colorMode, String progressId, boolean limitSize, java.nio.file.Path tempDir);

    /**
     * 获取指定文件的字符画文本
     * <p>
     * 根据原始文件名获取已转换的字符文本。
     * 该方法通常在图像转换完成后调用，用于获取转换结果的文本表示。
     * 返回的文本可用于显示、复制或保存为文本文件。
     * </p>
     *
     * @param filename 原始文件名，用于标识特定的转换任务
     * @return 字符画文本，包含转换结果的文本表示和相关信息
     */
    Map<String,Object> getCharText(String filename);
}