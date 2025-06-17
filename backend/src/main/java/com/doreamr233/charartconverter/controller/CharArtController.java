package com.doreamr233.charartconverter.controller;

import cn.hutool.core.io.FileTypeUtil;
import com.doreamr233.charartconverter.exception.FileTypeException;
import com.doreamr233.charartconverter.exception.ServiceException;
import com.doreamr233.charartconverter.model.ProgressInfo;
import com.doreamr233.charartconverter.service.CharArtService;
import com.doreamr233.charartconverter.service.ProgressService;
import com.doreamr233.charartconverter.util.WebpProcessorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.doreamr233.charartconverter.util.CharArtProcessor;

/**
 * 字符画转换控制器
 * <p>
 * 提供字符画转换相关的REST API接口，包括图片转换、获取字符文本、进度监控和健康检查。
 * 该控制器处理前端的HTTP请求，调用相应的服务完成字符画转换功能。
 * </p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class CharArtController {

    /**
     * 字符画服务，用于处理图片到字符画的转换
     */
    private final CharArtService charArtService;
    
    /**
     * 进度服务，用于跟踪和报告转换进度
     */
    private final ProgressService progressService;
    
    /**
     * WebP处理客户端，用于处理WebP动图和健康检查
     */
    private final WebpProcessorClient webpProcessorClient;

    /**
     * 将图片转换为字符画
     * <p>
     * 接收上传的图片文件，根据指定参数将其转换为字符画，并返回转换后的图片数据。
     * 支持静态图片（如JPG、PNG）和动态图片（如GIF）的转换。
     * 转换过程是异步的，可以通过progressId参数跟踪转换进度。
     * </p>
     *
     * @param imageFile 上传的图片文件
     * @param density 字符密度，可选值为"low"、"medium"、"high"，默认为"medium"
     * @param colorMode 颜色模式，可选值为"color"、"grayscale"，默认为"grayscale"
     * @param limitSize 是否限制字符画的最大尺寸，默认为true
     * @param progressIdParam 进度ID，用于跟踪转换进度，如果不提供则自动生成
     * @return 包含转换后图片数据的HTTP响应
     */
    
    @PostMapping("/convert")
    public ResponseEntity<byte[]> convertImage(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(value = "density", defaultValue = "medium") String density,
            @RequestParam(value = "colorMode", defaultValue = "grayscale") String colorMode,
            @RequestParam(value = "limitSize", defaultValue = "true") boolean limitSize,
            @RequestParam(value = "progressId", required = false) String progressIdParam) {
        
        Path tempFile = null;
        try {
            String originalFilename = imageFile.getOriginalFilename();
            log.info("接收到图片转换请求: {}, 密度: {}, 颜色模式: {}, 限制尺寸: {}", 
                    originalFilename, density, colorMode, limitSize);
            
            // 使用前端传递的progressId，如果没有则生成一个新的
            String progressId = (progressIdParam != null && !progressIdParam.isEmpty()) 
                ? progressIdParam : String.valueOf(System.currentTimeMillis());
            
            log.info("使用进度ID: {}", progressId);

            // 判断文件类型
            String fileType = FileTypeUtil.getType(imageFile.getInputStream());
            log.info("传入的图片类型: {}", fileType);
            
            // 验证文件类型是否支持
            if (fileType == null || !(fileType.equals("jpg") || fileType.equals("jpeg") || 
                fileType.equals("png") || fileType.equals("gif") || fileType.equals("webp"))) {
                throw new FileTypeException("不支持的文件类型: " + fileType);
            }

            // 将上传的文件保存为临时文件，以便多次读取
            // 提取文件扩展名，确保临时文件具有正确的扩展名
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            } else if (fileType != null) {
                // 如果无法从文件名获取扩展名，则使用检测到的文件类型
                fileExtension = "." + fileType;
            } else {
                // 默认扩展名
                fileExtension = ".tmp";
            }
            
            log.info("使用文件扩展名: {}", fileExtension);
            tempFile = CharArtProcessor.createTempFile("upload_", fileExtension);
            imageFile.transferTo(tempFile.toFile());

            boolean isGif = originalFilename != null && originalFilename.toLowerCase().endsWith(".gif") && "gif".equals(fileType);
            boolean isWebp = originalFilename != null && originalFilename.toLowerCase().endsWith(".webp") && "webp".equals(fileType);
            boolean isAnimated = false;

            // 如果是webp格式，判断是否为动图
            if (isWebp) {
                isAnimated = CharArtProcessor.isWebpAnimated(tempFile);
                log.info("WebP图片是否为动图: {}", isAnimated);
            }
            
            // 执行转换
            byte[] result;
            try {
                result = charArtService.convertToCharArt(
                        Files.newInputStream(tempFile),
                        originalFilename,
                        density,
                        colorMode,
                        progressId,
                        limitSize
                );
            } catch (IOException e) {
                // 将IOException包装为ServiceException
                throw new ServiceException("读取临时文件失败: " + e.getMessage(), e);
            }
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG); // 默认为PNG
            
            // 根据文件扩展名设置适当的Content-Type
            if (fileExtension != null && !fileExtension.isEmpty()) {
                switch (fileExtension.toLowerCase()) {
                    case ".jpg":
                    case ".jpeg":
                        headers.setContentType(MediaType.IMAGE_JPEG);
                        break;
                    case ".png":
                        headers.setContentType(MediaType.IMAGE_PNG);
                        break;
                    case ".gif":
                        headers.setContentType(MediaType.IMAGE_GIF);
                        break;
                    case ".webp":
                        headers.setContentType(MediaType.valueOf("image/webp"));
                        break;
                }
            }
            
            // 如果是GIF或动态WEBP，覆盖设置为对应的内容类型
            if (isGif) {
                headers.setContentType(MediaType.IMAGE_GIF);
            } else if (isWebp && isAnimated) {
                headers.setContentType(MediaType.valueOf("image/webp"));
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(result);
            
        } catch (Exception e) {
            // 异常已由全局异常处理器处理，这里只需记录日志
            log.error("图片转换失败", e);
            // 重新抛出异常，交由全局异常处理器处理
            // 由于我们已经将IOException包装为ServiceException，这里不需要特殊处理IOException
            throw new ServiceException("图片转换失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // 这里只是记录警告，不需要抛出ServiceException
                    // 因为这是清理临时文件的操作，即使失败也不应影响主要业务流程
                    log.warn("删除临时文件失败: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * 获取字符画文本
     * <p>
     * 根据文件名从缓存中获取已转换的字符画文本。
     * 返回的Map包含两个字段：
     * - find: 布尔值，表示是否找到对应的字符画文本
     * - text: 字符串，找到时为字符画文本，未找到时为空字符串
     * </p>
     *
     * @param filename 文件名，用于在缓存中查找对应的字符画文本
     * @return 包含查找结果和字符画文本的HTTP响应
     */
    @GetMapping("/text")
    public ResponseEntity<Map<String,Object>> getCharText(@RequestParam("filename") String filename) throws Exception {
        log.info("接收到获取字符画文本请求: {}", filename);
        Map<String,Object> result = charArtService.getCharText(filename);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(result);
    }

    /**
     * 获取转换进度的SSE端点
     * <p>
     * 使用Server-Sent Events (SSE)技术，向客户端推送实时进度更新。
     * 客户端可以通过EventSource API连接到此端点，接收进度事件。
     * 发送的事件类型包括：
     * - init: 连接建立时发送的初始化事件
     * - progress: 进度更新事件，包含当前进度百分比、消息等信息
     * - heartbeat: 保持连接活跃的心跳事件
     * </p>
     * <p>
     * 当进度达到100%或发生错误时，连接会自动关闭。
     * 为防止连接中断，每10秒会发送一次心跳消息。
     * 如果5秒内没有新的进度更新，会重发最后一条进度信息。
     * </p>
     *
     * @param id 进度ID，用于标识要跟踪的特定转换任务
     * @return SSE发射器，用于向客户端推送事件
     */
    @GetMapping(value = "/progress/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getProgress(@PathVariable String id) {
        log.info("收到进度请求: {}", id);
        
        // 创建一个5分钟超时的SSE发射器
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        // 设置SSE特定的响应头
        emitter.onCompletion(() -> log.info("SSE连接完成: {}", id));
        emitter.onTimeout(() -> log.info("SSE连接超时: {}", id));
        emitter.onError((ex) -> log.error("SSE连接错误: {}, 错误: {}", id, ex.getMessage()));
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        // 在执行线程中添加心跳机制
        executor.execute(() -> {
            try {
                ProgressInfo initProgressInfo =  new ProgressInfo(id, 0, "连接已建立");
                log.info(initProgressInfo.toString());
                // 初始化连接
                emitter.send(SseEmitter.event().name("init").data(initProgressInfo, MediaType.APPLICATION_JSON));

                long lastHeartbeat = System.currentTimeMillis();
                long lastProgressInfoTimestamp = System.currentTimeMillis();

                while (true) {
                    ProgressInfo progressInfo = progressService.getProgress(id);

                    long now = System.currentTimeMillis();
                    long progressInfoTimestamp = progressInfo.getTimestamp();

                    if (progressInfo.getPercentage() >= 0 && progressInfoTimestamp > lastProgressInfoTimestamp) {
                        log.info(progressInfo.toString());
                        // 发送进度更新
                        emitter.send(SseEmitter.event().name("progress").data(progressInfo, MediaType.APPLICATION_JSON));
                        lastProgressInfoTimestamp = progressInfo.getTimestamp();

                        if (progressInfo.getPercentage() >= 100) {
                            emitter.complete();
                            break;
                        }
                    } else {
                        // 如果没有有效进度信息，发送默认进度
                        if (now - lastProgressInfoTimestamp > 5000) { // 5秒没有进度更新就发送上一条消息
                            log.info(progressInfo.toString());
                            emitter.send(SseEmitter.event().name("progress").data(progressInfo, MediaType.APPLICATION_JSON));
                            lastProgressInfoTimestamp = progressInfo.getTimestamp();
                        }
                    }

                    // 定期发送心跳消息保持连接
                    if (now - lastHeartbeat > 10000) { // 10秒发送一次心跳
                        emitter.send(SseEmitter.event().name("heartbeat").data("ping", MediaType.TEXT_PLAIN));
                        lastHeartbeat = now;
                    }
                    Thread.sleep(500);
                }
            } catch (Exception e) {
            log.error("处理SSE连接时出错: {}, 错误: {}", id, e.getMessage());
            emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });
        
        return emitter;
    }

    /**
     * 健康检查端点
     * <p>
     * 提供一个简单的健康检查API，用于监控服务是否正常运行。
     * 使用WebpProcessorClient的isServiceAvailable方法检查Flask服务是否正常。
     * 返回的Map包含三个字段：
     * - status: 服务状态，正常时为"UP"，异常时为"DOWN"
     * - message: 状态描述信息
     * - flaskService: Flask服务状态，正常时为"UP"，异常时为"DOWN"
     * </p>
     *
     * @return 包含服务状态信息的Map
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        
        // 检查Flask服务是否可用
        boolean flaskServiceAvailable = webpProcessorClient.isServiceAvailable();

        if(flaskServiceAvailable){
            response.put("status", "UP");
            response.put("message", "字符画转换服务正常运行");
        }else{
            response.put("status", "DOWN");
            response.put("message", "字符画转换服务正常异常");
        }

        log.info("健康检查: 主服务状态=UP, Flask服务状态={}", flaskServiceAvailable ? "UP" : "DOWN");
        
        return response;
    }




}