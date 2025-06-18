package com.doreamr233.charartconverter.util;

import cn.hutool.http.*;
import com.doreamr233.charartconverter.exception.ServiceException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;

/**
 * WebP处理服务客户端
 * <p>
 * 该工具类用于与Python WebP处理服务进行通信，
 * 将WebP动图发送到服务进行处理，并解析返回的结果。
 * </p>
 */
@Component
@Slf4j
public class WebpProcessorClient {

    /**
     * WebP处理服务的基本URL
     */
    @Value("${webp-processor.url}")
    private String serviceBaseUrl;

    /**
     * WebP处理服务的连接超时时间（毫秒）
     */
    @Value("${webp-processor.connection-timeout}")
    private int maxTimeout;

    /**
     * WebP处理服务的最大重试次数
     */
    @Value("${webp-processor.max-retries}")
    private int maxRetriesCount;

    /**
     * 执行带有重试机制的HTTP请求
     *
     * @param request HTTP请求
     * @return HTTP响应
     * @throws HttpException 如果请求失败
     */
    private HttpResponse executeWithRetry(HttpRequest request) throws HttpException {
        HttpResponse response = null;
        for (int i = 0; i <= maxRetriesCount; i++) {
            try {
                log.info("WebP处理服务第{}次尝试，共{}次", i+1,maxRetriesCount+1);
                // 执行 HTTP 请求
                response = request.execute();
                if (response.isOk()) {
                    return response;
                }
            } catch (Exception e) {
                // 发生异常时打印堆栈信息
                log.info("WebP处理服务第{}次尝试失败", i+1);
            }
        }
        throw new HttpException("请求超时并达到重试次数上限，无法获取到响应！");
    }

    /**
     * 检查WebP处理服务是否可用
     *
     * @return 服务是否可用
     */
    public boolean isServiceAvailable() {
        log.info("WebP处理服务的基本URL：{}", serviceBaseUrl);
        log.info("WebP处理服务的连接超时时间（毫秒）：{}", maxTimeout);
        log.info("WebP处理服务的最大重试次数：{}", maxRetriesCount);
        try {
            HttpResponse response = executeWithRetry(HttpUtil.createGet(serviceBaseUrl + "/api/health").timeout(maxTimeout));
            return response.getStatus() == HttpStatus.HTTP_OK;
        } catch (Exception e) {
            log.warn("WebP处理服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 处理WebP动图
     *
     * @param webpFile WebP文件
     * @return 处理结果，包含帧数组和延迟数组
     * @throws ServiceException 如果处理过程中发生错误
     */
    public WebpProcessResult processWebp(File webpFile) {
        log.info("开始处理WebP文件: {}", webpFile.getName());
        
        // 使用Hutool的HttpUtil上传文件
        Map<String, Object> formMap = new HashMap<>();
        formMap.put("image", webpFile);
        
        // 发送请求并获取响应
        HttpResponse response = executeWithRetry(HttpUtil.createPost(serviceBaseUrl + "/api/process-webp")
                .form(formMap)
                .timeout(maxTimeout)
        );
        
        if (response.getStatus() != HttpStatus.HTTP_OK) {
            throw new ServiceException("WebP处理服务返回错误: " + response.getStatus() + " " + response.body());
        }
        
        // 解析JSON响应
        return parseResponse(response.body());
    }
    
    /**
     * 解析服务响应
     *
     * @param responseBody 响应体
     * @return WebP处理结果
     * @throws ServiceException 如果解析过程中发生错误
     */
    private WebpProcessResult parseResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            int frameCount = jsonResponse.getInt("frameCount");
            JSONArray delaysArray = jsonResponse.getJSONArray("delays");
            JSONArray framesArray = jsonResponse.getJSONArray("frames");
            
            log.info("WebP处理成功，共{}帧", frameCount);
            
            // 提取延迟数组
            int[] delays = new int[frameCount];
            for (int i = 0; i < frameCount; i++) {
                delays[i] = delaysArray.getInt(i);
            }
            
            // 提取并解码帧
            BufferedImage[] frames = new BufferedImage[frameCount];
            for (int i = 0; i < frameCount; i++) {
                String base64Frame = framesArray.getString(i);
                byte[] frameBytes = Base64.getDecoder().decode(base64Frame);
                frames[i] = ImageIO.read(new ByteArrayInputStream(frameBytes));
            }
            
            return new WebpProcessResult(frameCount, delays, frames);
        } catch (Exception e) {
            log.error("解析WebP处理服务响应失败", e);
            throw new ServiceException("解析WebP处理服务响应失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * WebP处理结果类
     */
    @Getter
    public static class WebpProcessResult {
        private final int frameCount;
        private final int[] delays;
        private final BufferedImage[] frames;
        
        public WebpProcessResult(int frameCount, int[] delays, BufferedImage[] frames) {
            this.frameCount = frameCount;
            this.delays = delays;
            this.frames = frames;
        }

    }

    /**
     * 创建WebP动画
     *
     * @param frames 帧图片列表
     * @param delays 帧延迟列表（毫秒）
     * @return WebP文件路径
     * @throws ServiceException 如果处理过程中发生错误
     */
    public String createWebpAnimation(BufferedImage[] frames, int[] delays) {
        log.info("开始创建WebP动画，共{}帧", frames.length);
        
        // 将图片转换为Base64编码
        List<String> frameBase64List = new ArrayList<>();
        for (BufferedImage frame : frames) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(frame, "PNG", baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                frameBase64List.add(base64Image);
            } catch (IOException e) {
                log.error("图片转Base64失败", e);
                throw new ServiceException("图片转Base64失败: " + e.getMessage(), e);
            }
        }
        
        // 构建请求体JSON
        JSONObject requestBody = new JSONObject();
        requestBody.put("frameImages", frameBase64List);
        requestBody.put("delays", delays);
        
        // 使用Hutool的HttpUtil发送JSON请求
        HttpResponse response = executeWithRetry(HttpUtil.createPost(serviceBaseUrl + "/api/create-webp-animation")
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(maxTimeout));
        
        if (response.getStatus() != HttpStatus.HTTP_OK) {
            throw new ServiceException("WebP动画创建服务返回错误: " + response.getStatus() + " " + response.body());
        }
        
        // 解析JSON响应
        try {
            JSONObject jsonResponse = new JSONObject(response.body());
            String webpBase64 = jsonResponse.getString("webpBase64");
            log.info("WebP动画创建成功，已接收Base64数据");
            
            // 将Base64数据转换为临时文件
            byte[] webpData = Base64.getDecoder().decode(webpBase64);
            String tempDir = System.getProperty("java.io.tmpdir");
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String webpPath = tempDir + File.separator + timestamp + "_animation.webp";
            
            try (FileOutputStream fos = new FileOutputStream(webpPath)) {
                fos.write(webpData);
            }
            
            log.info("WebP动画已保存到临时文件: {}", webpPath);
            return webpPath;
        } catch (Exception e) {
            log.error("解析WebP动画创建服务响应失败", e);
            throw new ServiceException("解析WebP动画创建服务响应失败: " + e.getMessage(), e);
        }
    }
}