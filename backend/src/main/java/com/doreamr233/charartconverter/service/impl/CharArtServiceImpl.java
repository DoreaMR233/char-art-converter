package com.doreamr233.charartconverter.service.impl;

import com.doreamr233.charartconverter.config.RedisConfig;
import com.doreamr233.charartconverter.exception.ServiceException;
import com.doreamr233.charartconverter.service.CharArtService;
import com.doreamr233.charartconverter.service.ProgressService;
import com.doreamr233.charartconverter.util.CharArtProcessor;
import com.doreamr233.charartconverter.util.WebpProcessorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字符画转换服务实现类
 * <p>
 * 该类实现了CharArtService接口，提供了将图像转换为字符画的核心功能。
 * 支持静态图像（如JPG、PNG）和动态图像（如GIF）的处理，可以生成字符文本和字符画图像。
 * 使用Redis缓存字符画文本结果，提高重复请求的响应速度。
 * 处理过程中通过ProgressService更新和报告转换进度。
 * </p>
 *
 * @author doreamr233
 */
@Service
@Slf4j
public class CharArtServiceImpl implements CharArtService {

    /**
     * 进度服务，用于更新和报告转换进度
     */
    @Resource
    private ProgressService progressService;
    
    /**
     * Redis模板，用于缓存字符画文本结果
     */
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    
    /**
     * WebP处理客户端，用于处理WebP动图
     */
    @Resource
    private WebpProcessorClient webpProcessorClient;


    /**
     * {@inheritDoc}
     * <p>
     * 实现将图片转换为字符画的核心方法。
     * 根据文件名判断是静态图像还是GIF动图，并调用相应的处理方法。
     * 整个处理过程中会创建多个临时文件，并在处理完成后清理。
     * 通过progressService更新处理进度，使前端能够实时显示转换状态。
     * </p>
     * 
     * @param imageStream 图片输入流，包含要转换的图像数据
     * @param filename 文件名，用于判断图像类型和缓存结果
     * @param density 字符密度，可选值为"low"、"medium"、"high"
     * @param colorMode 颜色模式，可选值为"color"、"grayscale"
     * @param progressId 进度ID，用于跟踪和报告转换进度
     * @param limitSize 是否限制字符画的最大尺寸
     * @return 转换后的字符画图片字节数组
     */
    @Override
    public byte[] convertToCharArt(InputStream imageStream, String filename, String density, String colorMode, String progressId, boolean limitSize) {
        List<Path> tempFiles = new ArrayList<>();
        try {
            // 判断图片类型
            boolean isGif = filename != null && filename.toLowerCase().endsWith(".gif");
            boolean isWebp = filename != null && filename.toLowerCase().endsWith(".webp");
            boolean isAnimated = false;
            Path tempWebpPath = null;
            
            // 如果是webp格式，需要判断是否为动图
            if (isWebp) {
                // 从文件名中提取扩展名
                String extension = "webp"; // 默认扩展名
                if (filename.contains(".")) {
                    extension = filename.substring(filename.lastIndexOf(".") + 1);
                }
                
                // 保存输入流到临时文件，以便检查是否为动图
                log.info("使用文件扩展名: {}", extension);
                tempWebpPath = CharArtProcessor.saveInputStreamToTempFile(imageStream, "check_webp_", extension);
                tempFiles.add(tempWebpPath);
                
                // 检查webp是否为动图
                isAnimated = CharArtProcessor.isWebpAnimated(tempWebpPath);
                log.info("WebP图片是否为动图: {}", isAnimated);
                
                // 重新获取文件输入流，因为之前的输入流已经被读取
                imageStream = Files.newInputStream(tempWebpPath);
            }
            
            // 更新进度
            progressService.updateProgress(progressId, 30, "已收到图片，准备开始转换", "接收", 0, 0,false);
            
            // 读取输入流到字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = imageStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] imageBytes = baos.toByteArray();
            
            if (isGif) {
                log.info("处理GIF：{}", filename);
                return CharArtProcessor.processGif(imageBytes, density, colorMode, limitSize, progressId, progressService);
            } else if (isWebp && isAnimated) {
                log.info("处理WebP动图：{}", filename);
                return CharArtProcessor.processWebpAnimation(tempWebpPath, density, colorMode, limitSize, progressId, progressService, webpProcessorClient);
            } else {
                log.info("处理静态图片：{}", filename);
                return CharArtProcessor.processStaticImage(imageBytes, density, colorMode, limitSize, progressId, progressService, filename, redisTemplate);
            }
        } catch (Exception e) {
            log.error("转换字符画失败", e);
            throw new ServiceException("转换字符画失败: " + e.getMessage(), e);
        } finally {
            // 清理所有临时文件
            CharArtProcessor.cleanupTempFiles(tempFiles);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现获取字符文本的方法。
     * 从Redis缓存中获取指定文件名对应的字符画文本。
     * 如果缓存中存在该文本，则返回文本内容；否则返回空文本并标记为未找到。
     * 返回的结果是一个Map，包含find和text两个键，分别表示是否找到文本和文本内容。
     * </p>
     * 
     * @param filename 原始文件名，用于构建缓存键
     * @return 包含查找状态和字符文本的Map，格式为{"find":boolean, "text":String}
     */
    @Override
    public Map<String,Object> getCharText(String filename) {
        Map<String,Object> result = new HashMap<>();
        // 从Redis缓存获取字符画文本
        String cacheKey = RedisConfig.CACHE_KEY_PREFIX + filename;
        String cachedText = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedText != null && !cachedText.isEmpty()) {
            log.debug("从Redis缓存获取字符画文本: {}", filename);
            // 返回JSON格式，find=true表示找到了字符画文本
            result.put("find",true);
            result.put("text",cachedText);
        } else {
            log.debug("Redis缓存中未找到字符画文本: {}", filename);
            // 返回JSON格式，find=false表示未找到字符画文本
            result.put("find",false);
            result.put("text","");
        }
        return result;
    }
}
