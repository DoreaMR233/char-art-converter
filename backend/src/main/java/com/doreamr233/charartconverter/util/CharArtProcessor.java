package com.doreamr233.charartconverter.util;

import com.doreamr233.charartconverter.exception.ServiceException;
import com.doreamr233.charartconverter.service.ProgressService;
import com.madgag.gif.fmsware.AnimatedGifEncoder;
import com.madgag.gif.fmsware.GifDecoder;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import static com.doreamr233.charartconverter.config.RedisConfig.CACHE_KEY_PREFIX;

/**
 * 字符画处理工具类
 * <p>
 * 提供将图像转换为字符画的核心功能。
 * 支持静态图像和动态图像（GIF、WebP）的处理，可以生成字符文本和字符画图像。
 * 处理过程中通过ProgressService更新和报告转换进度。
 * </p>
 *
 * @author doreamr233
 */
@Slf4j
public class CharArtProcessor {

    /**
     * 临时目录路径
     */
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * 字符集数组，包含不同密度级别的字符集
     * <p>
     * 索引0：低密度字符集，字符较少，主要是一些基本符号
     * 索引1：中密度字符集，字符数量适中，包含更多的符号和字母
     * 索引2：高密度字符集，字符数量最多，包含各种符号、字母和数字
     * </p>
     */
    private static final String[] CHAR_SETS = {
            " .:-=+*#%@", // 低密度
            " .,:;i1tfLCG08@", // 中密度
            " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$" // 高密度
    };

    /**
     * 检查WebP文件是否为动画
     * <p>
     * 通过检查WebP文件头中是否包含"ANIM"字符串来判断是否为动画WebP。
     * 这是一种简单但有效的方法，因为所有动画WebP文件的文件头中都包含这个标识。
     * </p>
     *
     * @param webpFile WebP文件路径
     * @return 如果是动画WebP返回true，否则返回false
     * @throws ServiceException 如果读取文件时发生错误
     */
    public static boolean isWebpAnimated(Path webpFile) {
        try (InputStream is = Files.newInputStream(webpFile)) {
            byte[] buffer = new byte[100]; // 读取前100个字节，足够检查文件头
            if (is.read(buffer) > 0) {
                String header = new String(buffer);
                return header.contains("ANIM"); // 检查是否包含ANIM标记
            }
        } catch (IOException e) {
            throw new ServiceException("读取WebP文件失败: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * 处理静态图像
     * <p>
     * 将静态图像转换为字符画，并根据需要生成字符画图像。
     * 处理流程包括：读取图像、转换为字符文本、缓存文本结果、生成字符画图像。
     * </p>
     *
     * @param imageBytes 图像字节数组
     * @param density 字符密度 (low, medium, high)
     * @param colorMode 颜色模式 (grayscale, color)
     * @param limitSize 是否限制字符画大小
     * @param progressId 进度ID
     * @param progressService 进度服务
     * @param filename 文件名，用于缓存键
     * @param redisTemplate Redis模板，用于缓存字符画文本
     * @return 处理后的图像字节数组
     * @throws ServiceException 如果处理过程中发生错误
     */
    public static byte[] processStaticImage(byte[] imageBytes, String density, String colorMode, boolean limitSize, String progressId, ProgressService progressService, String filename, RedisTemplate<String, String> redisTemplate) {
        List<Path> tempFiles = new ArrayList<>();

        try {
            // 更新进度
            progressService.updateProgress(progressId, 0, "开始处理图片", "初始化", 0, 1);

            // 将图像字节数组保存为临时文件
            // 尝试检测图像类型
            String imageType = detectImageType(imageBytes);
            log.debug("检测到图像类型: {}", imageType);
            
            Path tempImagePath = saveInputStreamToTempFile(new ByteArrayInputStream(imageBytes), "original_", imageType);
            tempFiles.add(tempImagePath);

            // 读取图像
            BufferedImage image = ImageIO.read(tempImagePath.toFile());
            if (image == null) {
                throw new ServiceException("无法读取图像文件");
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int totalPixels = width * height;

            // 更新进度
            progressService.updateProgress(progressId, 10, "图片读取完成", "图像读取", 1, totalPixels);

            // 获取密度级别
            int densityLevel = getDensityLevel(density);

            // 转换为字符文本
            String charText = convertImageToCharText(image, densityLevel, limitSize, progressId, totalPixels, 0, 1, 1, 10, progressService);

            // 将字符画文本存入Redis缓存
            if (redisTemplate != null && filename != null && !filename.isEmpty()) {
                String cacheKey = CACHE_KEY_PREFIX + filename;
                redisTemplate.opsForValue().set(cacheKey, charText);
                log.debug("已将字符画文本缓存到Redis: {}", cacheKey);
            }

            // 更新进度
            progressService.updateProgress(progressId, 60, "字符画文本生成完成", "文本生成", totalPixels, totalPixels);

            // 生成字符画图片
            Path charImagePath = createCharImageFile(charText, width, height, colorMode, image, progressId, 0, totalPixels, tempFiles, progressService);
            tempFiles.add(charImagePath);

            // 更新进度
            progressService.updateProgress(progressId, 90, "字符画图片生成完成", "图像生成", totalPixels, totalPixels);

            // 读取生成的图片
            byte[] resultBytes = Files.readAllBytes(charImagePath);

            // 更新进度
            progressService.updateProgress(progressId, 100, "处理完成", "完成", totalPixels, totalPixels);

            return resultBytes;
        } catch (IOException e) {
            throw new ServiceException("处理静态图像失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            cleanupTempFiles(tempFiles);
        }
    }

    /**
     * 处理GIF动画图像
     * <p>
     * 将GIF动画转换为字符画动画，并输出为GIF格式。
     * 处理流程包括：解码GIF、提取帧和延迟、处理每一帧、生成字符画图片、组合成新的GIF。
     * </p>
     *
     * @param imageBytes 图像字节数组
     * @param density 字符密度 (low, medium, high)
     * @param colorMode 颜色模式 (grayscale, color)
     * @param limitSize 是否限制字符画大小
     * @param progressId 进度ID
     * @param progressService 进度服务
     * @return 处理后的GIF动画字节数组
     * @throws ServiceException 如果处理过程中发生错误
     */
    public static byte[] processGif(byte[] imageBytes, String density, String colorMode, boolean limitSize, String progressId, ProgressService progressService) {
        List<Path> tempFiles = new ArrayList<>();

        try {
            // 更新进度
            progressService.updateProgress(progressId, 0, "开始处理GIF", "初始化", 0, 1);

            // 将图像字节数组保存为临时文件
            // 对于GIF处理，我们知道文件类型是gif
            Path tempImagePath = saveInputStreamToTempFile(new ByteArrayInputStream(imageBytes), "original_", "gif");
            tempFiles.add(tempImagePath);

            // 使用GifDecoder解码GIF
            GifDecoder gifDecoder = new GifDecoder();
            try (InputStream is = Files.newInputStream(tempImagePath)) {
                int status = gifDecoder.read(is);
                if (status != GifDecoder.STATUS_OK) {
                    throw new ServiceException("GIF解码失败，状态码: " + status);
                }
            } catch (IOException e) {
                throw new ServiceException("GIF读取失败: " + e.getMessage(), e);
            }

            // 获取GIF信息
            int frameCount = gifDecoder.getFrameCount();
            int[] delays = new int[frameCount];

            // 提取每一帧的延迟时间
            for (int i = 0; i < frameCount; i++) {
                delays[i] = gifDecoder.getDelay(i); // 获取每一帧的延迟时间（毫秒）
            }

            // 更新进度
            progressService.updateProgress(progressId, 10, "GIF解码完成，共" + frameCount + "帧", "GIF解码", 1, 1);

            // 获取第一帧的尺寸
            BufferedImage firstFrame = gifDecoder.getFrame(0);
            int width = firstFrame.getWidth();
            int height = firstFrame.getHeight();

            // 处理每一帧
            int densityLevel = getDensityLevel(density);

            // 计算总像素数（所有帧）
            int totalPixels = width * height * frameCount;

            // 创建GIF编码器
            AnimatedGifEncoder gifEncoder = new AnimatedGifEncoder();
            Path outputGifPath = createTempFile("output_", ".gif");
            tempFiles.add(outputGifPath);
            gifEncoder.start(outputGifPath.toString());
            gifEncoder.setRepeat(0); // 0表示无限循环

            // 处理每一帧
            for (int i = 0; i < frameCount; i++) {
                // 更新进度
                int progress = 10 + (i * 80 / frameCount);
                int currentFramePixels = width * height * i;
                progressService.updateProgress(progressId, progress, "处理GIF第" + (i + 1) + "/" + frameCount + "帧", "帧处理", currentFramePixels, totalPixels);

                // 获取当前帧
                BufferedImage frame = gifDecoder.getFrame(i);

                // 保存原始帧到临时文件（用于彩色模式）
                Path framePath = saveImageToTempFile(frame, "frame_" + i + "_", "png");
                tempFiles.add(framePath);

                // 生成字符画文本（传递进度ID和像素信息）
                int framePixelOffset = width * height * i;
                String frameText = convertImageToCharText(frame, densityLevel, limitSize, progressId, totalPixels, framePixelOffset, i + 1, frameCount, progress, progressService);

                // 生成字符画图片
                Path charFramePath = createCharImageFile(frameText, width, height, colorMode, frame, progressId, framePixelOffset, totalPixels, tempFiles, i + 1, frameCount, progress, progressService);
                tempFiles.add(charFramePath);

                // 读取生成的字符画图片
                BufferedImage charImage = ImageIO.read(charFramePath.toFile());

                // 设置帧延迟并添加到GIF编码器
                gifEncoder.setDelay(delays[i]);
                gifEncoder.addFrame(charImage);
            }

            // 完成GIF编码
            gifEncoder.finish();

            // 更新进度
            progressService.updateProgress(progressId, 90, "GIF编码完成", "GIF编码", totalPixels - 1, totalPixels);

            // 读取生成的GIF文件
            byte[] resultBytes = Files.readAllBytes(outputGifPath);

            // 更新进度
            progressService.updateProgress(progressId, 100, "处理完成", "完成", totalPixels, totalPixels);

            return resultBytes;
        } catch (IOException e) {
            throw new ServiceException("处理GIF动画图像失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            cleanupTempFiles(tempFiles);
        }
    }

    /**
     * 处理WebP动画
     * <p>
     * 该方法专门处理WebP格式的动画，将其转换为字符画动画并保存为WebP格式。
     * 处理流程包括：解码WebP动画、处理每一帧、生成字符画图片、组合成新的WebP动画。
     * </p>
     *
     * @param originalPath 原始WebP文件路径
     * @param density 字符密度 (low, medium, high)
     * @param colorMode 颜色模式 (grayscale, color)
     * @param limitSize 是否限制字符画大小
     * @param progressId 进度ID
     * @param progressService 进度服务
     * @param webpProcessorClient WebP处理客户端
     * @return 处理后的WebP动画字节数组
     * @throws ServiceException 如果处理过程中发生错误
     */
    public static byte[] processWebpAnimation(Path originalPath, String density, String colorMode, boolean limitSize, String progressId, ProgressService progressService, WebpProcessorClient webpProcessorClient) {
        List<Path> tempFiles = new ArrayList<>();

        try {
            // 更新进度
            progressService.updateProgress(progressId, 0, "开始处理WebP动画", "初始化", 0, 1);

            // 检查WebP处理服务是否可用
            if (!webpProcessorClient.isServiceAvailable()) {
                throw new ServiceException("WebP处理服务不可用，请确保Python服务已启动");
            }

            // 使用WebP处理服务解码WebP动画
            WebpProcessorClient.WebpProcessResult webpResult = webpProcessorClient.processWebp(originalPath.toFile());
            int frameCount = webpResult.getFrameCount();
            BufferedImage[] frames = webpResult.getFrames();
            int[] delays = webpResult.getDelays();

            progressService.updateProgress(progressId, 10, "WebP解码完成，共" + frameCount + "帧", "WebP解码", 1, 1);

            // 获取第一帧的尺寸
            BufferedImage firstFrame = frames[0];
            int width = firstFrame.getWidth();
            int height = firstFrame.getHeight();

            // 处理每一帧
            int densityLevel = getDensityLevel(density);

            // 计算总像素数（所有帧）
            int totalPixels = width * height * frameCount;

            // 存储每一帧的字符画图片路径
            List<String> charFramePaths = new ArrayList<>();

            for (int i = 0; i < frameCount; i++) {
                // 更新进度
                int progress = 10 + (i * 80 / frameCount);
                int currentFramePixels = width * height * i;
                progressService.updateProgress(progressId, progress, "处理WebP第" + (i + 1) + "/" + frameCount + "帧", "帧处理", currentFramePixels, totalPixels);

                // 获取当前帧
                BufferedImage frame = frames[i];

                // 生成字符画图片（传递进度ID和像素信息）
                int framePixelOffset = width * height * i;
                String frameText = convertImageToCharText(frame, densityLevel, limitSize, progressId, totalPixels, framePixelOffset, i + 1, frameCount, progress, progressService);

                Path charFramePath = createCharImageFile(frameText, width, height, colorMode, frame, progressId, framePixelOffset, totalPixels, tempFiles, i + 1, frameCount, progress, progressService);
                tempFiles.add(charFramePath);

                // 添加到路径列表
                charFramePaths.add(charFramePath.toString());
            }

            // 使用WebP处理服务创建WebP动画
            progressService.updateProgress(progressId, 90, "创建WebP动画", "WebP编码", totalPixels - 1, totalPixels);
            String webpOutputPath = webpProcessorClient.createWebpAnimation(charFramePaths, delays);

            // 读取生成的WebP文件
            tempFiles.add(Paths.get(webpOutputPath));
            byte[] resultBytes = Files.readAllBytes(Paths.get(webpOutputPath));

            progressService.updateProgress(progressId, 100, "WebP处理完成", "完成", totalPixels, totalPixels);

            return resultBytes;
        } catch (IOException e) {
            throw new ServiceException("处理WebP动画失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            cleanupTempFiles(tempFiles);
        }
    }

    /**
     * 将图像转换为字符画文本
     * <p>
     * 根据图像的灰度值，将每个像素映射为对应的字符，生成字符画文本。
     * 可以根据需要限制字符画的大小，避免生成过大的字符画。
     * </p>
     *
     * @param image 要转换的图像
     * @param densityLevel 密度级别 (0-低, 1-中, 2-高)
     * @param limitSize 是否限制字符画大小
     * @param progressId 进度ID
     * @param totalPixels 总像素数
     * @param pixelOffset 像素偏移量
     * @param nowFrame 当前帧
     * @param totalFrame 总帧数
     * @param totalPercentage 总进度百分比
     * @param progressService 进度服务
     * @return 生成的字符画文本
     */
    public static String convertImageToCharText(BufferedImage image, int densityLevel, boolean limitSize, String progressId, int totalPixels, int pixelOffset, int nowFrame, int totalFrame, int totalPercentage, ProgressService progressService) {
        int width = image.getWidth();
        int height = image.getHeight();

        // 计算缩放比例（如果需要限制大小）
        double scale = 1.0;
        if (limitSize) {
            // 限制最大字符数（宽度）
            int maxChars = 100;
            if (width > maxChars) {
                scale = (double) maxChars / width;
            }
        }

        // 计算缩放后的尺寸
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);

        // 使用临时文件进行图像缩放
        BufferedImage scaledImage = image;
        if (scale < 1.0) {
            try {
                // 创建临时文件用于存储缩放后的图像
                Path scaledImagePath = createTempFile("scaled_", ".png");

                // 使用Thumbnailator进行图像缩放并保存到临时文件
                Thumbnails.of(image)
                        .size(scaledWidth, scaledHeight)
                        .toFile(scaledImagePath.toFile());

                // 从临时文件读取缩放后的图像
                scaledImage = ImageIO.read(scaledImagePath.toFile());

                // 更新宽高为实际缩放后的尺寸
                scaledWidth = scaledImage.getWidth();
                scaledHeight = scaledImage.getHeight();

                // 删除临时文件
                Files.deleteIfExists(scaledImagePath);
            } catch (Exception e) {
                log.warn("使用Thumbnailator缩放图像失败: {}", e.getMessage());
                // 如果缩放失败，继续使用原始图像和计算的尺寸
            }
        }

        // 获取字符集
        String charSet = CHAR_SETS[densityLevel];

        // 转换为字符
        StringBuilder sb = new StringBuilder();
        int processedPixels = 0;
        int totalScaledPixels = scaledWidth * scaledHeight;

        for (int y = 0; y < scaledHeight; y++) {
            for (int x = 0; x < scaledWidth; x++) {
                // 获取像素颜色
                int rgb;
                if (scale < 1.0 && scaledImage != image) {
                    // 如果已缩放，直接获取缩放后图像的像素
                    rgb = scaledImage.getRGB(x, y);
                } else {
                    // 计算原图中对应的坐标
                    int origX = (int) (x / scale);
                    int origY = (int) (y / scale);

                    // 确保坐标不超出原图范围
                    origX = Math.min(origX, width - 1);
                    origY = Math.min(origY, height - 1);

                    rgb = image.getRGB(origX, origY);
                }

                int r = (rgb >> 16) & 0xFF;
                int g1 = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // 计算灰度值 (0-255)
                // 使用加权平均法计算灰度，更符合人眼感知
                int gray = (int)(0.299 * r + 0.587 * g1 + 0.114 * b);

                // 映射到字符集
                int charIndex = gray * (charSet.length() - 1) / 255;
                char c = charSet.charAt(charIndex);

                sb.append(c);

                // 更新进度（每处理一定数量的像素更新一次，避免过于频繁的更新）
                processedPixels++;
                if (processedPixels % 1000 == 0 || processedPixels == totalScaledPixels) {
                    // 计算当前处理的实际像素位置（考虑偏移量）
                    int currentPixel = pixelOffset + (int)((double)processedPixels / totalScaledPixels * (width * height));
                    progressService.updateProgress(progressId, totalPercentage, "生成字符画文本: " + currentPixel + "/" + totalPixels + " 像素", "文本转换（"+nowFrame+"/"+totalFrame+"）", currentPixel, totalPixels);
                }
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * 创建静态字符画图片文件（带进度更新）
     * <p>
     * 将生成的字符文本渲染为图片，可以选择保留原图的颜色信息或使用灰度模式。
     * 渲染过程包括：创建新的图片缓冲区、设置字体和颜色、绘制每个字符。
     * 整个过程中会更新进度信息，以便前端显示处理状态。
     * </p>
     * 
     * @param charText 字符画文本
     * @param originalWidth 原始宽度 (不再使用)
     * @param originalHeight 原始高度 (不再使用)
     * @param colorMode 颜色模式 (grayscale, color)
     * @param originalImage 原始图片，用于彩色模式获取颜色信息
     * @param progressId 进度ID，用于更新进度
     * @param pixelOffset 像素偏移量，用于计算当前处理的像素位置
     * @param totalPixels 总像素数
     * @param tempFiles 临时文件列表，用于跟踪和清理
     * @param progressService 进度服务
     * @return 字符画图片文件路径
     */
    public static Path createCharImageFile(String charText, int originalWidth, int originalHeight, String colorMode,
                                     BufferedImage originalImage, String progressId, int pixelOffset, int totalPixels,
                                     List<Path> tempFiles, ProgressService progressService) {
        try{
            // 计算字体大小和图片尺寸
            String[] lines = charText.split("\n");
            int lineCount = lines.length;
            int maxLineLength = 0;

            for (String line : lines) {
                maxLineLength = Math.max(maxLineLength, line.length());
            }

            // 设置基础字体大小 - 使用固定值以确保清晰可读
            int baseFontSize = 12; // 基础字体大小

            // 设置字体 - 使用粗体以增强颜色显示效果
            Font font = new Font(Font.MONOSPACED, "color".equalsIgnoreCase(colorMode) ? Font.BOLD : Font.PLAIN, baseFontSize);

            // 创建临时图形上下文以获取字体度量
            BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D tempG = tempImage.createGraphics();
            tempG.setFont(font);
            FontMetrics metrics = tempG.getFontMetrics(font);
            tempG.dispose();

            // 计算字符宽度和行高
            int charWidth = metrics.charWidth('M'); // 使用等宽字体的标准字符宽度
            int lineHeight = metrics.getHeight();

            // 计算图片的实际尺寸 - 基于字符画文本的尺寸
            int imageWidth = maxLineLength * charWidth;
            int imageHeight = lineCount * lineHeight;

            // 创建临时文件用于存储图像
            Path outputImagePath = createTempFile("char_image_", ".png");
            tempFiles.add(outputImagePath);

            // 分块处理图像以减少内存使用
            int blockHeight = 100; // 每次处理100行
            int numBlocks = (int) Math.ceil((double) lineCount / blockHeight);

            // 创建最终图像文件
            BufferedImage fullImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D fullG = fullImage.createGraphics();

            // 设置渲染质量
            fullG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            fullG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            fullG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            // 设置背景 - 对于彩色模式使用深灰色背景以增强对比度
            if ("color".equalsIgnoreCase(colorMode)) {
                fullG.setColor(new Color(30, 30, 30)); // 深灰色背景
            } else {
                fullG.setColor(Color.WHITE); // 灰度模式保持白色背景
            }
            fullG.fillRect(0, 0, imageWidth, imageHeight);

            // 设置字体
            fullG.setFont(font);

            // 计算起始位置 - 从左上角开始
            int startX = 0;
            int startY = metrics.getAscent(); // 只加上基线偏移

            // 根据颜色模式绘制字符
            boolean isColorMode = "color".equalsIgnoreCase(colorMode);

            // 计算总字符数用于进度更新
            int totalChars = 0;
            for (String line : lines) {
                totalChars += line.length();
            }
            int processedChars = 0;

            // 分块处理
            for (int block = 0; block < numBlocks; block++) {
                int startLine = block * blockHeight;
                int endLine = Math.min(startLine + blockHeight, lineCount);

                // 绘制当前块的字符
                for (int i = startLine; i < endLine && i < lines.length; i++) {
                    String line = lines[i];
                    for (int j = 0; j < line.length(); j++) {
                        char c = line.charAt(j);

                        // 计算当前字符的绘制位置
                        int x = startX + j * charWidth;
                        int y = startY + i * lineHeight;

                        if (isColorMode && originalImage != null) {
                            // 彩色模式：从原图获取对应位置的颜色
                            // 计算原图中对应的坐标 - 按比例映射
                            int origX = 0;
                            int origY = 0;

                            if (maxLineLength > 0 && lineCount > 0) {
                                origX = Math.min((int)(j * 1.0 / maxLineLength * originalImage.getWidth()), originalImage.getWidth() - 1);
                                origY = Math.min((int)(i * 1.0 / lineCount * originalImage.getHeight()), originalImage.getHeight() - 1);
                            }

                            // 获取原始颜色
                            Color pixelColor = new Color(originalImage.getRGB(origX, origY));

                            // 增强颜色饱和度和亮度
                            float[] hsb = Color.RGBtoHSB(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue(), null);
                            // 增加饱和度，但不超过1.0
                            hsb[1] = Math.min(1.0f, hsb[1] * 1.5f);
                            // 确保亮度适中，不会太暗或太亮
                            hsb[2] = Math.max(0.3f, Math.min(0.9f, hsb[2] * 1.2f));

                            Color enhancedColor = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
                            fullG.setColor(enhancedColor);
                        } else {
                            // 灰度模式：使用黑色
                            fullG.setColor(Color.BLACK);
                        }

                        if (isColorMode) { // 对于彩色模式的所有字符（包括空格）
                            // 先绘制背景色块
                            fullG.fillRect(x, y - metrics.getAscent(), charWidth, lineHeight);

                            // 对于非空格字符，绘制白色字符以增强可读性
                            if (c != ' ') {
                                fullG.setColor(Color.WHITE);
                                fullG.drawString(String.valueOf(c), x, y);
                            }
                        } else {
                            // 灰度模式：使用黑色字符
                            fullG.setColor(Color.BLACK);
                            fullG.drawString(String.valueOf(c), x, y);
                        }

                        // 更新进度
                        processedChars++;
                        if (progressId != null && (processedChars % 500 == 0 || processedChars == totalChars)) {
                            // 计算当前进度百分比（60-90%之间）
                            int progress = 60 + (processedChars * 30 / totalChars);
                            // 计算当前处理的实际像素位置（考虑偏移量）
                            int currentPixel = pixelOffset + (int)((double)processedChars / totalChars * Objects.requireNonNull(originalImage).getWidth() * originalImage.getHeight());
                            progressService.updateProgress(progressId, progress, "生成字符画图片: " + currentPixel + "/" + totalPixels + " 像素", "图像渲染", currentPixel, totalPixels);
                        }
                    }
                }
            }

            fullG.dispose();

            // 保存完整图像到文件
            ImageIO.write(fullImage, "png", outputImagePath.toFile());

            return outputImagePath;
        } catch (IOException e) {
            throw new ServiceException("创建静态字符画图片文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建动态字符画图片文件（带进度更新）
     * @param charText 字符画文本
     * @param originalWidth 原始宽度 (不再使用)
     * @param originalHeight 原始高度 (不再使用)
     * @param colorMode 颜色模式 (grayscale, color)
     * @param originalImage 原始图片，用于彩色模式获取颜色信息
     * @param progressId 进度ID，用于更新进度
     * @param pixelOffset 像素偏移量，用于计算当前处理的像素位置
     * @param totalPixels 总像素数
     * @param tempFiles 临时文件列表，用于跟踪和清理
     * @param nowFrame 当前处理的帧
     * @param totalFrame 总共需要处理的帧
     * @param totalPercentage 总进度百分比
     * @param progressService 进度服务
     * @return 字符画图片文件路径
     */
    public static Path createCharImageFile(String charText, int originalWidth, int originalHeight, String colorMode, 
                                     BufferedImage originalImage, String progressId, int pixelOffset, int totalPixels,
                                     List<Path> tempFiles, int nowFrame, int totalFrame, int totalPercentage, ProgressService progressService) {
        try{
            // 计算字体大小和图片尺寸
            String[] lines = charText.split("\n");
            int lineCount = lines.length;
            int maxLineLength = 0;

            for (String line : lines) {
                maxLineLength = Math.max(maxLineLength, line.length());
            }

            // 设置基础字体大小 - 使用固定值以确保清晰可读
            int baseFontSize = 12; // 基础字体大小

            // 设置字体 - 使用粗体以增强颜色显示效果
            Font font = new Font(Font.MONOSPACED, "color".equalsIgnoreCase(colorMode) ? Font.BOLD : Font.PLAIN, baseFontSize);

            // 创建临时图形上下文以获取字体度量
            BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D tempG = tempImage.createGraphics();
            tempG.setFont(font);
            FontMetrics metrics = tempG.getFontMetrics(font);
            tempG.dispose();

            // 计算字符宽度和行高
            int charWidth = metrics.charWidth('M'); // 使用等宽字体的标准字符宽度
            int lineHeight = metrics.getHeight();

            // 计算图片的实际尺寸 - 基于字符画文本的尺寸
            int imageWidth = maxLineLength * charWidth;
            int imageHeight = lineCount * lineHeight;

            // 创建临时文件用于存储图像
            Path outputImagePath = createTempFile("char_image_", ".png");
            tempFiles.add(outputImagePath);

            // 分块处理图像以减少内存使用
            int blockHeight = 100; // 每次处理100行
            int numBlocks = (int) Math.ceil((double) lineCount / blockHeight);

            // 创建最终图像文件
            BufferedImage fullImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D fullG = fullImage.createGraphics();

            // 设置渲染质量
            fullG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            fullG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            fullG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            // 设置背景 - 对于彩色模式使用深灰色背景以增强对比度
            if ("color".equalsIgnoreCase(colorMode)) {
                fullG.setColor(new Color(30, 30, 30)); // 深灰色背景
            } else {
                fullG.setColor(Color.WHITE); // 灰度模式保持白色背景
            }
            fullG.fillRect(0, 0, imageWidth, imageHeight);

            // 设置字体
            fullG.setFont(font);

            // 计算起始位置 - 从左上角开始
            int startX = 0;
            int startY = metrics.getAscent(); // 只加上基线偏移

            // 根据颜色模式绘制字符
            boolean isColorMode = "color".equalsIgnoreCase(colorMode);

            // 计算总字符数用于进度更新
            int totalChars = 0;
            for (String line : lines) {
                totalChars += line.length();
            }
            int processedChars = 0;

            // 分块处理
            for (int block = 0; block < numBlocks; block++) {
                int startLine = block * blockHeight;
                int endLine = Math.min(startLine + blockHeight, lineCount);

                // 绘制当前块的字符
                for (int i = startLine; i < endLine && i < lines.length; i++) {
                    String line = lines[i];
                    for (int j = 0; j < line.length(); j++) {
                        char c = line.charAt(j);

                        // 计算当前字符的绘制位置
                        int x = startX + j * charWidth;
                        int y = startY + i * lineHeight;

                        if (isColorMode && originalImage != null) {
                            // 彩色模式：从原图获取对应位置的颜色
                            // 计算原图中对应的坐标 - 按比例映射
                            int origX = 0;
                            int origY = 0;

                            if (maxLineLength > 0 && lineCount > 0) {
                                origX = Math.min((int)(j * 1.0 / maxLineLength * originalImage.getWidth()), originalImage.getWidth() - 1);
                                origY = Math.min((int)(i * 1.0 / lineCount * originalImage.getHeight()), originalImage.getHeight() - 1);
                            }

                            // 获取原始颜色
                            Color pixelColor = new Color(originalImage.getRGB(origX, origY));

                            // 增强颜色饱和度和亮度
                            float[] hsb = Color.RGBtoHSB(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue(), null);
                            // 增加饱和度，但不超过1.0
                            hsb[1] = Math.min(1.0f, hsb[1] * 1.5f);
                            // 确保亮度适中，不会太暗或太亮
                            hsb[2] = Math.max(0.3f, Math.min(0.9f, hsb[2] * 1.2f));

                            Color enhancedColor = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
                            fullG.setColor(enhancedColor);
                        } else {
                            // 灰度模式：使用黑色
                            fullG.setColor(Color.BLACK);
                        }

                        if (isColorMode) { // 对于彩色模式的所有字符（包括空格）
                            // 先绘制背景色块
                            fullG.fillRect(x, y - metrics.getAscent(), charWidth, lineHeight);

                            // 对于非空格字符，绘制白色字符以增强可读性
                            if (c != ' ') {
                                fullG.setColor(Color.WHITE);
                                fullG.drawString(String.valueOf(c), x, y);
                            }
                        } else {
                            // 灰度模式：使用黑色字符
                            fullG.setColor(Color.BLACK);
                            fullG.drawString(String.valueOf(c), x, y);
                        }

                        // 更新进度
                        processedChars++;
                        if (progressId != null && (processedChars % 500 == 0 || processedChars == totalChars)) {
                            // 计算当前处理的实际像素位置（考虑偏移量）
                            int currentPixel = pixelOffset + (int)((double)processedChars / totalChars * Objects.requireNonNull(originalImage).getWidth() * originalImage.getHeight());
                            progressService.updateProgress(progressId, totalPercentage, "生成字符画图片: " + currentPixel + "/" + totalPixels + " 像素", "图像渲染（第"+nowFrame+"/"+totalFrame+"帧）", currentPixel, totalPixels);
                        }
                    }
                }
            }

            fullG.dispose();

            // 保存完整图像到文件
            ImageIO.write(fullImage, "png", outputImagePath.toFile());

            return outputImagePath;
        } catch (IOException e) {
            throw new ServiceException("创建动态字符画图片文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取密度级别
     * <p>
     * 根据传入的密度字符串，返回对应的密度级别索引。
     * 密度级别用于选择CHAR_SETS数组中的字符集，不同密度的字符集包含不同数量的字符，
     * 影响最终字符画的细节表现。
     * </p>
     *
     * @param density 密度字符串，可选值为"low"、"medium"、"high"
     * @return 密度级别索引，0表示低密度，1表示中密度，2表示高密度
     */
    public static int getDensityLevel(String density) {
        switch (density.toLowerCase()) {
            case "low":
                return 0;
            case "high":
                return 2;
            case "medium":
            default:
                return 1;
        }
    }
    
    /**
     * 将InputStream保存为临时文件
     * <p>
     * 创建一个临时文件，并将输入流的内容写入该文件。
     * 用于将上传的图片数据保存到文件系统，以便后续处理。
     * 使用源文件的扩展名作为临时文件的扩展名，保持文件类型一致性。
     * </p>
     *
     * @param inputStream 要保存的输入流
     * @param prefix 临时文件名前缀
     * @param extension 文件扩展名（不包含点号）
     * @return 保存的临时文件路径
     * @throws ServiceException 当文件创建或写入过程中发生错误时抛出
     */
    public static Path saveInputStreamToTempFile(InputStream inputStream, String prefix, String extension) {
        try {
            // 确保扩展名格式正确（以点号开头）
            String formattedExtension = extension.startsWith(".") ? extension : "." + extension;
            
            // 创建临时文件，使用源文件的扩展名
            Path tempFile = createTempFile(prefix, formattedExtension);
            
            // 将输入流写入临时文件
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            log.debug("已保存临时文件: {}, 扩展名: {}", tempFile, formattedExtension);
            return tempFile;
        } catch (IOException e) {
            throw new ServiceException("保存临时文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将BufferedImage保存为临时文件
     * <p>
     * 创建一个临时文件，并将BufferedImage对象写入该文件。
     * 用于在处理过程中保存中间图像结果，如GIF的单帧或缩放后的图像。
     * 使用指定的扩展名作为临时文件的扩展名，保持文件类型一致性。
     * </p>
     *
     * @param image 要保存的BufferedImage对象
     * @param prefix 临时文件名前缀
     * @param extension 文件扩展名（不包含点号）
     * @return 保存的临时文件路径
     * @throws ServiceException 当文件创建或写入过程中发生错误时抛出
     */
    public static Path saveImageToTempFile(BufferedImage image, String prefix, String extension) {
        try {
            // 确保扩展名格式正确（以点号开头）
            String formattedExtension = extension.startsWith(".") ? extension : "." + extension;
            String formatName = extension.startsWith(".") ? extension.substring(1) : extension;
            
            // 创建临时文件
            Path tempFile = createTempFile(prefix, formattedExtension);
            
            // 将图像写入临时文件
            ImageIO.write(image, formatName, tempFile.toFile());
            
            log.debug("已保存图像到临时文件: {}, 扩展名: {}", tempFile, formattedExtension);
            return tempFile;
        } catch (IOException e) {
            throw new ServiceException("保存图像到临时文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建临时文件
     * <p>
     * 在系统临时目录中创建一个带有指定前缀和后缀的临时文件。
     * 文件名中包含UUID，确保唯一性，避免冲突。
     * 保留原始文件的扩展名，确保文件类型的一致性。
     * </p>
     *
     * @param prefix 临时文件名前缀
     * @param suffix 临时文件名后缀（包含点号）
     * @return 创建的临时文件路径
     * @throws ServiceException 当文件创建过程中发生错误时抛出
     */
    public static Path createTempFile(String prefix, String suffix) {
        try {
            // 确保后缀以点号开头
            String formattedSuffix = suffix.startsWith(".") ? suffix : "." + suffix;
            
            // 创建临时文件，使用UUID确保唯一性
            Path tempFile = Files.createTempFile(
                Paths.get(TEMP_DIR), 
                prefix + UUID.randomUUID().toString(), 
                formattedSuffix
            );
            
            log.debug("已创建临时文件: {}, 后缀: {}", tempFile, formattedSuffix);
            return tempFile;
        } catch (IOException e) {
            throw new ServiceException("创建临时文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检测图像类型
     * <p>
     * 通过分析图像字节数组的文件头来检测图像类型。
     * 支持检测常见的图像格式，如PNG、JPEG、GIF、BMP、WEBP等。
     * </p>
     *
     * @param imageBytes 图像字节数组
     * @return 检测到的图像类型（扩展名，不包含点号），如果无法检测则返回"png"作为默认值
     */
    private static String detectImageType(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 8) {
            return "png"; // 默认返回png
        }
        
        // 检查PNG文件头: 89 50 4E 47 0D 0A 1A 0A
        if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == (byte) 0x50 && 
            imageBytes[2] == (byte) 0x4E && imageBytes[3] == (byte) 0x47) {
            return "png";
        }
        
        // 检查JPEG文件头: FF D8 FF
        if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8 && 
            imageBytes[2] == (byte) 0xFF) {
            return "jpg";
        }
        
        // 检查GIF文件头: 47 49 46 38 (GIF8)
        if (imageBytes[0] == (byte) 0x47 && imageBytes[1] == (byte) 0x49 && 
            imageBytes[2] == (byte) 0x46 && imageBytes[3] == (byte) 0x38) {
            return "gif";
        }
        
        // 检查BMP文件头: 42 4D (BM)
        if (imageBytes[0] == (byte) 0x42 && imageBytes[1] == (byte) 0x4D) {
            return "bmp";
        }
        
        // 检查WebP文件头: 52 49 46 46 (RIFF) ... 57 45 42 50 (WEBP)
        if (imageBytes[0] == (byte) 0x52 && imageBytes[1] == (byte) 0x49 && 
            imageBytes[2] == (byte) 0x46 && imageBytes[3] == (byte) 0x46 && 
            imageBytes.length >= 12 && 
            imageBytes[8] == (byte) 0x57 && imageBytes[9] == (byte) 0x45 && 
            imageBytes[10] == (byte) 0x42 && imageBytes[11] == (byte) 0x50) {
            return "webp";
        }
        
        // 如果无法识别，默认返回png
        return "png";
    }

    /**
     * 清理临时文件
     * <p>
     * 删除处理过程中创建的所有临时文件。
     * 通常在finally块中调用，确保无论处理成功还是失败，都能清理临时文件，
     * 防止磁盘空间浪费。
     * </p>
     *
     * @param tempFiles 要清理的临时文件路径列表
     */
    public static void cleanupTempFiles(List<Path> tempFiles) {
        if (tempFiles == null || tempFiles.isEmpty()) {
            return;
        }

        for (Path tempFile : tempFiles) {
            try {
                if (tempFile != null && Files.exists(tempFile)) {
                    Files.delete(tempFile);
                    log.debug("已删除临时文件: {}", tempFile);
                }
            } catch (IOException e) {
                log.error("删除临时文件失败: {}, 错误: {}", tempFile, e.getMessage());
            }
        }
    }
}