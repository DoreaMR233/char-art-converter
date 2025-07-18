package com.doreamr233.charartconverter.util;

import com.doreamr233.charartconverter.config.ParallelProcessingConfig;
import com.doreamr233.charartconverter.config.TempDirectoryConfig;
import com.doreamr233.charartconverter.exception.ServiceException;
import com.doreamr233.charartconverter.model.FrameProcessResult;
import com.doreamr233.charartconverter.model.WebpFrameProcessResult;
import com.doreamr233.charartconverter.model.WebpProcessResult;
import com.doreamr233.charartconverter.service.ProgressService;
import com.madgag.gif.fmsware.AnimatedGifEncoder;
import com.madgag.gif.fmsware.GifDecoder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;

import cn.hutool.core.io.FileUtil;



import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;

import com.luciad.imageio.webp.WebPImageReaderSpi;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

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

    // 静态初始化块：注册WebP ImageReader
    static {
        try {
            IIORegistry registry = IIORegistry.getDefaultInstance();
            registry.registerServiceProvider(new WebPImageReaderSpi());
            log.debug("WebP ImageReader 服务提供者已注册");
        } catch (Exception e) {
            log.warn("注册WebP ImageReader时出错: {}", e.getMessage());
        }
    }

    /**
     * -- SETTER --
     *  设置并行处理配置
     *
     */
    @Setter
    private static ParallelProcessingConfig parallelConfig;
    /**
     * -- SETTER --
     *  设置临时目录配置
     *
     */
    @Setter
    private static TempDirectoryConfig tempDirectoryConfig;

    /**
     * 获取临时目录路径
     * @return 临时目录路径
     */
    private static String getTempDir() {
        return tempDirectoryConfig != null ? tempDirectoryConfig.getTempDirectory() : System.getProperty("java.io.tmpdir");
    }

    /**
     * 字符集数组，包含不同密度级别的字符集
     * <p>
     * 索引0：低密度字符集，字符较少，主要是一些基本符号
     * 索引1：中密度字符集，字符数量适中，包含更多的符号和字母
     * 索引2：高密度字符集，字符数量最多，包含各种符号、字母和数字
     * </p>
     */
    private static final String[] CHAR_SETS = {
//            " .:-=+*#%@", // 低密度
//            " .,:;i1tfLCG08@", // 中密度
//            " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$" // 高密度
            ".:-=+*#%@", // 低密度
            ".,:;i1tfLCG08@", // 中密度
            ".'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$" // 高密度
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
        try (InputStream is = FileUtil.getInputStream(webpFile.toFile())) {
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
    public static byte[] processStaticImage(byte[] imageBytes, String density, String colorMode, boolean limitSize, String progressId, ProgressService progressService, String filename, RedisTemplate<String, String> redisTemplate, Path tempDir) {
        List<Path> tempFiles = new ArrayList<>();

        try {
            // 将图像字节数组保存为临时文件
            // 尝试检测图像类型
            String imageType = detectImageType(imageBytes);
            log.debug("检测到图像类型: {}", imageType);
            
            Path tempImagePath = createTempFileInDirectory(tempDir, "original_", "." + imageType);
            FileUtil.writeBytes(imageBytes, tempImagePath.toFile());
            tempFiles.add(tempImagePath);
            // 读取图像
            BufferedImage image = ImageIO.read(tempImagePath.toFile());

            if (image == null) {
                throw new ServiceException("无法读取图像文件");
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int totalPixels = width * height;
            int pregressStart = 40;
            int pregressEnd = 60;

            // 更新进度
            progressService.updateProgress(progressId, 40, "图片读取完成", "图像读取", 1, totalPixels,false);

            // 获取密度级别
            int densityLevel = getDensityLevel(density);

            // 转换为字符文本
            String charText = convertImageToCharText(image, densityLevel, limitSize, progressId, totalPixels, 0, 1, 1, pregressStart,pregressEnd,"文本生成", progressService,true, tempDir);

            // 将字符画文本存入Redis缓存
            if (redisTemplate != null && filename != null && !filename.isEmpty()) {
                String cacheKey = CACHE_KEY_PREFIX + filename;
                redisTemplate.opsForValue().set(cacheKey, charText);
                log.debug("已将字符画文本缓存到Redis: {}", cacheKey);
            }

            // 更新进度
            progressService.updateProgress(progressId, 60, "字符画文本生成完成", "文本生成", totalPixels, totalPixels,false);

            pregressStart = 60;
            pregressEnd = 80;

            // 生成字符画图片
            Path charImagePath = createCharImageFile(charText, colorMode, image, progressId, 0, totalPixels, tempFiles,1,pregressStart, pregressEnd,"图像生成",progressService,true, tempDir);
            tempFiles.add(charImagePath);

            // 更新进度
            progressService.updateProgress(progressId, 90, "字符画图片生成完成", "图像生成", totalPixels, totalPixels,false);

            // 读取生成的图片
            byte[] resultBytes = FileUtil.readBytes(charImagePath.toFile());

            // 更新进度
            progressService.updateProgress(progressId, 100, "处理完成", "完成", totalPixels, totalPixels,true);

            return resultBytes;
        } catch (IOException e) {
            // 清理临时目录
            deleteTempDirectory(tempDir);
            throw new ServiceException("处理静态图像失败: " + e.getMessage(), e);
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
    public static byte[] processGif(byte[] imageBytes, String density, String colorMode, boolean limitSize, String progressId, ProgressService progressService, Path tempDir) {
        List<Path> tempFiles = new ArrayList<>();

        try {

            // 将图像字节数组保存为临时文件
            // 对于GIF处理，我们知道文件类型是gif
            Path tempImagePath = createTempFileInDirectory(tempDir, "original_", ".gif");
            FileUtil.writeBytes(imageBytes, tempImagePath.toFile());
            tempFiles.add(tempImagePath);

            // 使用GifDecoder解码GIF
            GifDecoder gifDecoder = new GifDecoder();
            try (InputStream is = FileUtil.getInputStream(tempImagePath.toFile())) {
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
            progressService.updateProgress(progressId, 40, "GIF解码完成，共" + frameCount + "帧", "GIF解码", 1, 1,false);

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
            Path outputGifPath = createTempFileInDirectory(tempDir, "output_", ".gif");
            tempFiles.add(outputGifPath);
            gifEncoder.start(outputGifPath.toString());
            gifEncoder.setRepeat(0); // 0表示无限循环
            double pregressStart = 40;
            double pregressEnd = 80;
            double singlePregress = ((pregressEnd - pregressStart) / (double)frameCount);
            
            // 使用多线程并行处理帧
            processFramesInParallel(gifDecoder, frameCount, width, height, densityLevel, limitSize, 
                                  colorMode, progressId, totalPixels, tempFiles, delays, gifEncoder, 
                                  pregressStart, pregressEnd, singlePregress, progressService, tempDir);

            // 完成GIF编码
            gifEncoder.finish();

            // 更新进度
            progressService.updateProgress(progressId, 90, "GIF编码完成", "GIF编码", totalPixels - 1, totalPixels,false);

            // 读取生成的GIF文件
            byte[] resultBytes = FileUtil.readBytes(outputGifPath.toFile());

            // 更新进度
            progressService.updateProgress(progressId, 100, "处理完成", "完成", totalPixels, totalPixels,true);

            return resultBytes;
        } catch (Exception e) {
            // 清理临时目录
            deleteTempDirectory(tempDir);
            throw new ServiceException("处理GIF动画图像失败: " + e.getMessage(), e);
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
    public static byte[] processWebpAnimation(Path originalPath, String density, String colorMode, boolean limitSize, String progressId, ProgressService progressService, WebpProcessorClient webpProcessorClient, Path tempDir) {
        List<Path> tempFiles = new ArrayList<>();

        try {
            // 检查WebP处理服务是否可用
            if (!webpProcessorClient.isServiceAvailable()) {
                throw new ServiceException("WebP处理服务不可用，请确保Python服务已启动");
            }
            
            // 存储临时目录信息到WebpProcessorClient
            webpProcessorClient.storeTempDirectory(progressId, tempDir);
            
            // 使用WebP处理服务解码WebP动画，传递任务ID用于进度跟踪
            WebpProcessResult webpResult = webpProcessorClient.processWebp(originalPath.toFile(), progressId);
            int frameCount = webpResult.getFrameCount();
            BufferedImage[] frames = webpResult.getFrames();
            int[] delays = webpResult.getDelays();

            progressService.updateProgress(progressId, 40, "WebP解码完成，共" + frameCount + "帧", "WebP解码", 1, 1,false);

            // 获取第一帧的尺寸
            BufferedImage firstFrame = frames[0];
            int width = firstFrame.getWidth();
            int height = firstFrame.getHeight();

            // 处理每一帧
            int densityLevel = getDensityLevel(density);

            // 计算总像素数（所有帧）
            int totalPixels = width * height * frameCount;

            // 存储每一帧的字符画图片路径
            Path[] charFramePaths = new Path[frameCount];

            double pregressStart = 40;
            double pregressEnd = 80;
            double singlePregress = ((pregressEnd - pregressStart) / (double)frameCount);
            
            // 使用多线程处理WebP帧
            processWebpFramesInParallel(frames, frameCount, width, height, densityLevel, limitSize, 
                colorMode, progressId, totalPixels, tempFiles, charFramePaths, 
                pregressStart, pregressEnd, singlePregress, progressService, tempDir);

            // 使用WebP处理服务创建WebP动画
            progressService.updateProgress(progressId, 90, "创建WebP动画", "WebP编码", totalPixels - 1, totalPixels,false);
            // 使用相同的任务ID进行WebP动画创建，以便继续跟踪进度
            File webpOutputFile = webpProcessorClient.createWebpAnimationFromFiles(charFramePaths, delays, progressId);

            // 读取生成的WebP文件
            tempFiles.add(webpOutputFile.toPath());
            byte[] resultBytes = FileUtil.readBytes(webpOutputFile);

            progressService.updateProgress(progressId, 100, "WebP处理完成", "完成", totalPixels, totalPixels,true);

            return resultBytes;
        } catch (Exception e) {
            // 清理临时目录
            deleteTempDirectory(tempDir);
            throw new ServiceException("处理WebP动画失败: " + e.getMessage(), e);
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
     * @param pregressStart 子任务开始前进度百分比
     * @param pregressEnd 子任务结束后进度百分比
     * @param stageName 进度阶段
     * @param progressService 进度服务
     * @param isShowProgress 是否显示进度
     * @return 生成的字符画文本
     */
    public static String convertImageToCharText(BufferedImage image, int densityLevel, boolean limitSize, String progressId, int totalPixels,
                                                int pixelOffset, int nowFrame, int totalFrame, double pregressStart,double pregressEnd,
                                                String stageName, ProgressService progressService,boolean isShowProgress, Path tempDir) {
        int width = image.getWidth();
        int height = image.getHeight();

        // 计算缩放比例（如果需要限制大小）
        double scale = 1.0;
        if (limitSize) {
            // 限制最大字符数（宽度和高度）
            int maxChars = 100;
            int maxLines = 100;
            
            // 计算宽度和高度的缩放比例
            double scaleWidth = (width > maxChars) ? (double) maxChars / width : 1.0;
            double scaleHeight = (height > maxLines) ? (double) maxLines / height : 1.0;
            
            // 使用较小的缩放比例，以确保宽度和高度都不超过限制
            // 同时保持原图的长宽比例
            scale = Math.min(scaleWidth, scaleHeight);
        }

        // 计算缩放后的尺寸
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);

        // 使用临时文件进行图像缩放
        BufferedImage scaledImage = image;
        if (scale < 1.0) {
            try {
                // 创建临时文件用于存储缩放后的图像
                Path scaledImagePath = createTempFileInDirectory(tempDir, "scaled_", ".png");

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
                FileUtil.del(scaledImagePath.toFile());
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
                int progressInterval = parallelConfig != null ? parallelConfig.getPixelProgressInterval() : 1000;
            if ((processedPixels % progressInterval == 0 || processedPixels == totalScaledPixels) && isShowProgress ) {
                    // 计算当前处理的实际像素位置（考虑偏移量）
                    int currentPixel = pixelOffset + (int)((double)processedPixels / totalScaledPixels * (width * height));
                    double progress = pregressStart + ((double)nowFrame / totalFrame * (pregressEnd-pregressStart));
                    progressService.updateProgress(progressId, progress, "生成字符画文本: " + currentPixel + "/" + totalPixels + " 像素", stageName, currentPixel, totalPixels,false);
                }
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * 创建字符画图片文件（带进度更新）
     * @param charText 字符画文本
     * @param colorMode 颜色模式 (grayscale, color, colorBackground)
     * @param originalImage 原始图片，用于彩色模式获取颜色信息
     * @param progressId 进度ID，用于更新进度
     * @param pixelOffset 像素偏移量，用于计算当前处理的像素位置
     * @param totalPixels 总像素数
     * @param tempFiles 临时文件列表，用于跟踪和清理
     * @param totalFrame 总共需要处理的帧
     * @param pregressStart 子任务开始前进度百分比
     * @param pregressEnd 子任务结束后进度百分比
     * @param stageName 进度阶段
     * @param progressService 进度服务
     * @param isShowProgress 是否显示进度
     * @return 字符画图片文件路径
     */
    public static Path createCharImageFile(String charText, String colorMode, BufferedImage originalImage, String progressId, int pixelOffset,
                                           int totalPixels, List<Path> tempFiles, int totalFrame, double pregressStart, double pregressEnd,
                                           String stageName, ProgressService progressService,boolean isShowProgress, Path tempDir) {
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

            // 设置等宽字体 - 使用粗体以增强颜色显示效果
            Font font = new Font(Font.MONOSPACED, Font.BOLD, baseFontSize);

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

            // 创建输出图像文件
            Path outputImagePath = createTempFileInDirectory(tempDir, "char_image_", ".png");
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

            // 设置背景 - 使用白色背景以便彩色字符更加清晰可见
            fullG.setColor(Color.WHITE);
            fullG.fillRect(0, 0, imageWidth, imageHeight);

            // 设置字体
            fullG.setFont(font);

            // 计算起始位置 - 从左上角开始
            int startX = 0;
            int startY = metrics.getAscent(); // 只加上基线偏移

            // 根据颜色模式绘制字符
            boolean isColorMode = "color".equalsIgnoreCase(colorMode);
            boolean isColorBackgroundMode = "colorBackground".equalsIgnoreCase(colorMode);

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

                        // 计算原图中对应的坐标 - 按比例映射
                        int origX = 0;
                        int origY = 0;
                        Color enhancedColor = Color.BLACK;
                        
                        if ((isColorMode || isColorBackgroundMode) && originalImage != null) {
                            if (maxLineLength > 0) {
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

                            enhancedColor = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
                        }
                        
                        if (isColorMode) {
                            // 彩色字符模式：字符使用原图颜色
                            fullG.setColor(enhancedColor);
                            
                            // 对于非空格字符，直接使用颜色绘制字符
                            if (c != ' ') {
                                // 已经设置了颜色，直接绘制字符
                                fullG.drawString(String.valueOf(c), x, y);
                            } else {
                                // 对于空格，绘制背景色块
                                fullG.fillRect(x, y - metrics.getAscent(), charWidth, lineHeight);
                            }
                        } else if (isColorBackgroundMode) {
                            // 彩色背景模式：背景使用原图颜色，字符使用与背景相似但有对比度的颜色
                            // 先绘制背景
                            fullG.setColor(enhancedColor);
                            fullG.fillRect(x, y - metrics.getAscent(), charWidth, lineHeight);
                            
                            // 使用与背景颜色相似但有一定对比度的颜色绘制字符
                            Color charColor = getColor(enhancedColor);
                            fullG.setColor(charColor);
                            
                            // 绘制字符（非空格）
                            if (c != ' ') {
                                fullG.drawString(String.valueOf(c), x, y);
                            }
                        } else {
                            // 灰度模式：使用黑色字符
                            fullG.setColor(Color.BLACK);
                            fullG.drawString(String.valueOf(c), x, y);
                        }

                        // 更新进度
                        processedChars++;
                        if ((progressId != null && (processedChars % 500 == 0 || processedChars == totalChars)) && isShowProgress) {
                            double progress = (int)(pregressStart + ((double)processedChars / totalChars * (pregressEnd-pregressStart)));
                            // 计算当前处理的实际像素位置（考虑偏移量）
                            int currentPixel = pixelOffset + (int)((double)processedChars / totalChars * Objects.requireNonNull(originalImage).getWidth() * originalImage.getHeight());
                            String progressMessage = "生成字符画图片: " + currentPixel + "/" + totalPixels + " 像素";
                            progressService.updateProgress(progressId, progress, progressMessage, stageName, currentPixel, totalPixels,false);
                        }
                    }
                }
            }

            fullG.dispose();

            // 使用多线程保存完整图像到文件并更新进度
            writeImageWithProgress(fullImage, outputImagePath, progressId, progressService, totalPixels, pregressEnd);

            return outputImagePath;
        } catch (Exception e) {
            String errorType = totalFrame > 1 ? "动态" : "静态";
            throw new ServiceException("创建" + errorType + "字符画图片文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用多线程写入图像文件并定期更新进度
     * 
     * @param image 要保存的图像
     * @param outputPath 输出文件路径
     * @param progressId 进度ID
     * @param progressService 进度服务
     * @param totalPixels 总像素数
     * @param startProgress 开始进度百分比
     * @throws ServiceException 如果写入失败
     */
    private static void writeImageWithProgress(BufferedImage image, Path outputPath, String progressId, ProgressService progressService, int totalPixels, double startProgress) throws ServiceException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        
        try {
            // 提交图像写入任务
            Future<Void> writeTask = executor.submit(() -> {
                try {
                    ImageIO.write(image, "png", outputPath.toFile());
                } catch (Exception e) {
                    exceptionRef.set(e);
                }
                return null;
            });
            
            // 在写入过程中定期更新进度
            double currentProgress = startProgress;
            double targetProgress = startProgress+10; // 从目标进度开始
            double progressStep = 0.01;
            
            while (!writeTask.isDone()) {
                try {
                    // 等待0.5秒
                    long updateInterval = parallelConfig != null ? parallelConfig.getProgressUpdateInterval() : 500L;
                    Thread.sleep(updateInterval);
                    
                    // 更新进度
                    currentProgress = Math.min(currentProgress + progressStep, targetProgress);
                    progressService.updateProgress(progressId, currentProgress, "正在保存字符画图片...", "图像保存", totalPixels, totalPixels, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("图像写入被中断", e);
                }
            }
            
            // 等待任务完成
            writeTask.get(15, TimeUnit.SECONDS);
            
            // 检查是否有异常
            if (exceptionRef.get() != null) {
                throw new ServiceException("写入图像文件失败: " + exceptionRef.get().getMessage(), exceptionRef.get());
            }
            
            log.debug("多线程图像写入完成，文件路径: {}", outputPath);
            
        } catch (TimeoutException e) {
            throw new ServiceException("图像写入超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("图像写入被中断", e);
        } catch (ExecutionException e) {
            throw new ServiceException("图像写入执行失败: " + e.getMessage(), e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @NotNull
    private static Color getColor(Color enhancedColor) {
        double brightness = (enhancedColor.getRed() * 0.299 +
                          enhancedColor.getGreen() * 0.587 +
                          enhancedColor.getBlue() * 0.114) / 255;

        // 创建一个与背景相似但有一定对比度的颜色
        Color charColor;
        if (brightness > 0.5) {
            // 亮色背景使用稍暗的相似颜色
            charColor = new Color(
                Math.max(0, enhancedColor.getRed() - 80),
                Math.max(0, enhancedColor.getGreen() - 80),
                Math.max(0, enhancedColor.getBlue() - 80)
            );
        } else {
            // 暗色背景使用稍亮的相似颜色
            charColor = new Color(
                Math.min(255, enhancedColor.getRed() + 80),
                Math.min(255, enhancedColor.getGreen() + 80),
                Math.min(255, enhancedColor.getBlue() + 80)
            );
        }
        return charColor;
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
     * 创建基于文件名的临时目录
     * <p>
     * 在系统临时目录中创建一个以原始文件名（去除扩展名）为名称的子目录。
     * 如果目录已存在则直接返回，确保同一文件的所有临时文件都在同一目录下。
     * </p>
     *
     * @param originalFilename 原始文件名
     * @return 创建的临时目录路径
     * @throws ServiceException 当目录创建过程中发生错误时抛出
     */
    public static Path createTempDirectoryForFile(String originalFilename) {
        try {
            // 从文件名中提取基础名称（去除扩展名和特殊字符）
            String baseName = originalFilename;
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf("."));
            }
            
            // 清理文件名，移除不安全的字符
            baseName = baseName.replaceAll("[^a-zA-Z0-9\u4e00-\u9fa5_-]", "_");
            if (baseName.isEmpty()) {
                baseName = "unknown";
            }
            
            // 添加时间戳确保唯一性
            String dirName = baseName + "_" + System.currentTimeMillis();
            
            // 创建临时目录
            Path tempDir = Paths.get(getTempDir()).resolve(dirName);
            if (!FileUtil.exist(tempDir.toFile())) {
                FileUtil.mkdir(tempDir.toFile());
                log.debug("已创建临时目录: {}", tempDir);
            }
            
            return tempDir;
        } catch (Exception e) {
            throw new ServiceException("创建临时目录失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 在指定目录中创建临时文件
     * <p>
     * 在指定的目录中创建一个带有指定前缀和后缀的临时文件。
     * 文件名中包含UUID，确保唯一性，避免冲突。
     * </p>
     *
     * @param tempDir 临时目录路径
     * @param prefix 临时文件名前缀
     * @param suffix 临时文件名后缀（包含点号）
     * @return 创建的临时文件路径
     * @throws ServiceException 当文件创建过程中发生错误时抛出
     */
    public static Path createTempFileInDirectory(Path tempDir, String prefix, String suffix) {
        try {
            // 确保后缀以点号开头
            String formattedSuffix = suffix.startsWith(".") ? suffix : "." + suffix;
            
            // 创建临时文件，使用UUID确保唯一性
            String fileName = prefix + UUID.randomUUID() + formattedSuffix;
            Path tempFile = tempDir.resolve(fileName);
            FileUtil.touch(tempFile.toFile());
            
            log.debug("已在目录 {} 中创建临时文件: {}", tempDir, tempFile.getFileName());
            return tempFile;
        } catch (Exception e) {
            throw new ServiceException("在指定目录中创建临时文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除临时目录及其所有内容
     * <p>
     * 递归删除指定的临时目录及其所有子文件和子目录。
     * 用于清理转换完成后的临时文件。
     * </p>
     *
     * @param tempDir 要删除的临时目录路径
     */
    public static void deleteTempDirectory(Path tempDir) {
        if (tempDir == null || !FileUtil.exist(tempDir.toFile())) {
            return;
        }
        
        try {
            FileUtil.del(tempDir.toFile());
            log.debug("已删除临时目录: {}", tempDir);
        } catch (Exception e) {
            log.error("删除临时目录失败: {}", tempDir, e);
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
     * 并行处理GIF帧并编码到GIF
     * 整合了帧处理和GIF编码功能，使用统一的线程池提高效率，减少线程中断风险
     */
    private static void processFramesInParallel(GifDecoder gifDecoder, int frameCount, int width, int height,
                                                int densityLevel, boolean limitSize, String colorMode,
                                                String progressId, int totalPixels, List<Path> tempFiles,
                                                int[] delays, AnimatedGifEncoder gifEncoder,
                                                double pregressStart, double pregressEnd, double singlePregress,
                                                ProgressService progressService, Path tempDir) {
        // 优化线程数配置，避免过多线程导致资源竞争
        int threadCount = parallelConfig != null ? 
            parallelConfig.calculateThreadCount(frameCount) : 
            Math.min(Math.max(Runtime.getRuntime().availableProcessors() / 2, 1), Math.min(frameCount, 4));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicReference<Double> currentProgress = new AtomicReference<>(pregressStart);
        // 每帧的进度分为三个阶段：文本生成、图像转换、GIF编码
        double progressPerStage = singlePregress / 3.0;
        
        try {
            // 存储每帧的处理结果
            List<Future<FrameProcessResult>> futures = new ArrayList<>();
            
            // 提交所有帧的处理任务
            for (int i = 0; i < frameCount; i++) {
                final int frameIndex = i;
                Future<FrameProcessResult> future = executor.submit(() -> {
                    try {
                        // 检查线程中断状态
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("任务被中断");
                        }
                        
                        // 获取当前帧
                        BufferedImage frame = gifDecoder.getFrame(frameIndex);
                        
                        // 保存原始帧到临时文件（用于彩色模式）
                        Path framePath = createTempFileInDirectory(tempDir, "frame_" + frameIndex + "_", ".png");
                        ImageIO.write(frame, "png", framePath.toFile());
                        synchronized (tempFiles) {
                            tempFiles.add(framePath);
                        }
                        
                        // 更新进度：开始处理当前帧
                        progressService.updateProgress(progressId, currentProgress.get(), 
                            "处理GIF第" + (frameIndex + 1) + "/" + frameCount + "帧", "帧处理", 
                            (frameIndex + 1), frameCount, false);
                        
                        // 生成字符画文本
                        int framePixelOffset = width * height * frameIndex;
                        String frameText = convertImageToCharText(frame, densityLevel, limitSize, progressId, 
                            totalPixels, framePixelOffset, frameIndex + 1, frameCount, currentProgress.get(), 
                            currentProgress.get() + progressPerStage, "文本生成：第" + (frameIndex + 1) + "帧/共" + frameCount + "帧", 
                            progressService, false, tempDir);
                        
                        // 文本生成完成，更新进度
                        double newProgress = currentProgress.updateAndGet(current -> current + progressPerStage);
                        progressService.updateProgress(progressId, newProgress, 
                            "第" + (frameIndex + 1) + "帧字符画文本生成完成", "文本生成",
                                frameIndex + 1, frameCount, false);
                        
                        // 生成字符画图片
                        Path charFramePath = createCharImageFile(frameText, colorMode, frame, progressId, 
                            framePixelOffset, totalPixels, tempFiles, frameCount, currentProgress.get(), 
                            currentProgress.get() + progressPerStage, "图像生成：第" + (frameIndex + 1) + "帧/共" + frameCount + "帧", 
                            progressService, false, tempDir);
                        
                        synchronized (tempFiles) {
                            tempFiles.add(charFramePath);
                        }
                        
                        // 图像生成完成，更新进度
                        newProgress = currentProgress.updateAndGet(current -> current + progressPerStage);
                        progressService.updateProgress(progressId, newProgress, 
                            "第" + (frameIndex + 1) + "帧字符画图片生成完成", "图像生成",
                                frameIndex + 1, frameCount, false);
                        return new FrameProcessResult(frameIndex, charFramePath, delays[frameIndex]);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("处理第" + (frameIndex + 1) + "帧被中断", e);
                    } catch (Exception e) {
                        log.error("处理第{}帧时发生错误: {}", frameIndex + 1, e.getMessage(), e);
                        throw new RuntimeException("处理第" + (frameIndex + 1) + "帧失败: " + e.getMessage(), e);
                    }
                });
                futures.add(future);
            }

            // 按顺序收集结果并添加到GIF编码器（整合了原addFrameToGifWithProgress的功能）
            for (int i = 0; i < frameCount; i++) {
                try {
                    // 检查主线程中断状态
                    if (Thread.currentThread().isInterrupted()) {
                        // 取消所有未完成的任务
                        futures.forEach(f -> f.cancel(true));
                        throw new InterruptedException("主处理线程被中断");
                    }
                    
                    // 获取帧处理结果，增加超时时间
                    FrameProcessResult result = futures.get(i).get(60, TimeUnit.SECONDS);
                    
                    // 直接在主线程中添加帧到GIF编码器，避免额外的线程创建
                    try {
                        // 读取生成的字符画图片
                        BufferedImage charImage = ImageIO.read(result.getCharFramePath().toFile());
                        
                        // 检查中断状态
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("GIF编码被中断");
                        }
                        
                        // 使用多线程添加帧到GIF编码器（包含进度更新）
                        addFrameToGifWithProgress(gifEncoder, charImage, result.getDelay(), 
                            progressId, progressService, i + 1, frameCount, 
                            currentProgress.get(), progressPerStage);
                        
                        // 更新原子引用中的进度值
                        currentProgress.updateAndGet(current -> current + progressPerStage);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ServiceException("GIF帧编码被中断", e);
                    } catch (IOException e) {
                        throw new ServiceException("读取字符画图片失败: " + e.getMessage(), e);
                    } catch (Exception e) {
                        throw new ServiceException("GIF帧编码失败: " + e.getMessage(), e);
                    }
                    
                } catch (TimeoutException e) {
                    log.error("第 {} 帧处理超时", i + 1);
                    // 取消所有未完成的任务
                    futures.forEach(f -> f.cancel(true));
                    throw new ServiceException("第" + (i + 1) + "帧处理超时", e);
                } catch (ExecutionException e) {
                    log.error("第 {} 帧处理失败: {}", i + 1, e.getCause().getMessage());
                    throw new ServiceException("第" + (i + 1) + "帧处理失败: " + e.getCause().getMessage(), e.getCause());
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("GIF帧并行处理被中断", e);
        } catch (Exception e) {
            // 确保在异常情况下进度也能达到预期的结束值
            progressService.updateProgress(progressId, pregressEnd, "处理完成", "完成", frameCount, frameCount, false);
            throw e;
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("强制关闭线程池");
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("线程池无法正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 多线程处理WebP帧
     * 仿照processFramesInParallel方法，使用线程池并行处理WebP动画的每一帧
     */
    private static void processWebpFramesInParallel(BufferedImage[] frames, int frameCount, int width, int height,
                                                    int densityLevel, boolean limitSize, String colorMode,
                                                    String progressId, int totalPixels, List<Path> tempFiles,
                                                    Path[] charFramePaths, double pregressStart, double pregressEnd,
                                                    double singlePregress, ProgressService progressService, Path tempDir) {
        // 优化线程数配置，避免过多线程导致资源竞争
        int threadCount = parallelConfig != null ? 
            parallelConfig.calculateThreadCount(frameCount) : 
            Math.min(Math.max(Runtime.getRuntime().availableProcessors() / 2, 1), Math.min(frameCount, 4));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicReference<Double> currentProgress = new AtomicReference<>(pregressStart);
        // 每帧的进度分为两个阶段：文本生成、图像转换（WebP编码在后续单独处理）
        double progressPerStage = singlePregress / 2.0;
        
        try {
            // 存储每帧的处理结果
            List<Future<WebpFrameProcessResult>> futures = new ArrayList<>();
            
            // 提交所有帧的处理任务
            for (int i = 0; i < frameCount; i++) {
                final int frameIndex = i;
                Future<WebpFrameProcessResult> future = executor.submit(() -> {
                    try {
                        // 检查线程中断状态
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("任务被中断");
                        }
                        
                        // 获取当前帧
                        BufferedImage frame = frames[frameIndex];
                        
                        // 更新进度：开始处理当前帧
                        progressService.updateProgress(progressId, currentProgress.get(), 
                            "处理WebP第" + (frameIndex + 1) + "/" + frameCount + "帧", "帧处理", 
                            (frameIndex + 1), frameCount, false);
                        
                        // 生成字符画文本
                        int framePixelOffset = width * height * frameIndex;
                        String frameText = convertImageToCharText(frame, densityLevel, limitSize, progressId, 
                            totalPixels, framePixelOffset, frameIndex + 1, frameCount, currentProgress.get(), 
                            currentProgress.get() + progressPerStage, "文本生成：第" + (frameIndex + 1) + "帧/共" + frameCount + "帧", 
                            progressService, false, tempDir);
                        
                        // 文本生成完成，更新进度
                        double newProgress = currentProgress.updateAndGet(current -> current + progressPerStage);
                        progressService.updateProgress(progressId, newProgress, 
                            "第" + (frameIndex + 1) + "帧字符画文本生成完成", "文本生成",
                                frameIndex + 1, frameCount, false);
                        
                        // 生成字符画图片
                        Path charFramePath = createCharImageFile(frameText, colorMode, frame, progressId, 
                            framePixelOffset, totalPixels, tempFiles, frameCount, currentProgress.get(), 
                            currentProgress.get() + progressPerStage, "图像生成：第" + (frameIndex + 1) + "帧/共" + frameCount + "帧", 
                            progressService, false, tempDir);
                        
                        synchronized (tempFiles) {
                            tempFiles.add(charFramePath);
                        }
                        
                        // 图像生成完成，更新进度
                        newProgress = currentProgress.updateAndGet(current -> current + progressPerStage);
                        progressService.updateProgress(progressId, newProgress, 
                            "第" + (frameIndex + 1) + "帧字符画图片生成完成", "图像生成",
                                frameIndex + 1, frameCount, false);
                        
                        // 释放当前帧的内存
                        frames[frameIndex] = null;
                        
                        return new WebpFrameProcessResult(frameIndex, charFramePath);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("处理第" + (frameIndex + 1) + "帧被中断", e);
                    } catch (Exception e) {
                        log.error("处理第{}帧时发生错误: {}", frameIndex + 1, e.getMessage(), e);
                        throw new RuntimeException("处理第" + (frameIndex + 1) + "帧失败: " + e.getMessage(), e);
                    }
                });
                futures.add(future);
            }

            // 按顺序收集结果
            for (int i = 0; i < frameCount; i++) {
                try {
                    // 检查主线程中断状态
                    if (Thread.currentThread().isInterrupted()) {
                        // 取消所有未完成的任务
                        futures.forEach(f -> f.cancel(true));
                        throw new InterruptedException("主处理线程被中断");
                    }
                    
                    // 获取帧处理结果，增加超时时间
                    WebpFrameProcessResult result = futures.get(i).get(60, TimeUnit.SECONDS);
                    
                    // 将结果存储到数组中
                    charFramePaths[result.getFrameIndex()] = result.getCharFramePath();
                    
                    // WebP编码进度更新（这里只是标记，实际编码在后续进行）
                    progressService.updateProgress(progressId, currentProgress.get(), 
                        "第" + (i + 1) + "帧处理完成", "帧处理完成",
                            i + 1, frameCount, false);
                    
                } catch (TimeoutException e) {
                    log.error("第{}帧处理超时", i + 1);
                    // 取消所有未完成的任务
                    futures.forEach(f -> f.cancel(true));
                    throw new ServiceException("第" + (i + 1) + "帧处理超时", e);
                } catch (ExecutionException e) {
                    log.error("第{}帧处理失败: {}", i + 1, e.getCause().getMessage());
                    throw new ServiceException("第" + (i + 1) + "帧处理失败: " + e.getCause().getMessage(), e.getCause());
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("WebP帧并行处理被中断", e);
        } catch (Exception e) {
            // 确保在异常情况下进度也能达到预期的结束值
            progressService.updateProgress(progressId, pregressEnd, "处理完成", "完成", frameCount, frameCount, false);
            throw e;
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("强制关闭WebP处理线程池");
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("WebP处理线程池无法正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            // 手动触发垃圾回收，释放已处理帧的内存
            System.gc();
        }
    }

    /**
     * 使用多线程将帧添加到GIF编码器，并定期更新进度
     * 
     * @param gifEncoder GIF编码器
     * @param charImage 字符画图片
     * @param delay 帧延迟
     * @param progressId 进度ID
     * @param progressService 进度服务
     * @param frameNumber 当前帧号
     * @param totalFrames 总帧数
     * @param startProgress 起始进度
     * @param progressPerStage 每阶段进度增量
     * @throws ServiceException 如果添加帧失败
     */
    private static void addFrameToGifWithProgress(AnimatedGifEncoder gifEncoder, BufferedImage charImage, 
                                                 int delay, String progressId, ProgressService progressService,
                                                 int frameNumber, int totalFrames, double startProgress, 
                                                 double progressPerStage) throws ServiceException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        
        try {
            // 提交GIF帧添加任务
            Future<Void> addFrameTask = executor.submit(() -> {
                try {
                    synchronized (gifEncoder) {
                        gifEncoder.setDelay(delay);
                        gifEncoder.addFrame(charImage);
                    }
                } catch (Exception e) {
                    exceptionRef.set(e);
                }
                return null;
            });
            
            // 在添加过程中定期更新进度
            double currentProgress = startProgress;
            double targetProgress = startProgress + progressPerStage;
            double progressStep = progressPerStage / 20.0; // 分20步更新进度
            
            while (!addFrameTask.isDone()) {
                try {
                    // 等待500毫秒
                    long updateInterval = parallelConfig != null ? parallelConfig.getProgressUpdateInterval() : 500L;
                    Thread.sleep(updateInterval);
                    
                    // 更新进度
                    currentProgress = Math.min(currentProgress + progressStep, targetProgress);
                    progressService.updateProgress(progressId, currentProgress, 
                        "正在编码第" + frameNumber + "/" + totalFrames + "帧到GIF...", 
                        "GIF编码", frameNumber, totalFrames, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("GIF帧添加被中断", e);
                }
            }
            
            // 等待任务完成
            addFrameTask.get(30, TimeUnit.SECONDS);
            
            // 检查是否有异常
            if (exceptionRef.get() != null) {
                throw new ServiceException("添加GIF帧失败: " + exceptionRef.get().getMessage(), exceptionRef.get());
            }
            
            // 最终进度更新
            progressService.updateProgress(progressId, targetProgress, 
                "第" + frameNumber + "帧编码到GIF完成", "GIF编码", 
                frameNumber, totalFrames, false);
            
            log.debug("多线程GIF帧添加完成，帧号: {}", frameNumber);
            
        } catch (TimeoutException e) {
            throw new ServiceException("GIF帧添加超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("GIF帧添加被中断", e);
        } catch (ExecutionException e) {
            throw new ServiceException("GIF帧添加执行失败: " + e.getMessage(), e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}