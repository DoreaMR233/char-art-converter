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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
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
     * WebP处理服务是否启用
     */
    @Value("${webp-processor.enabled}")
    private boolean isWebpProcessorEnabled;

    /**
     * 默认字符密度，可选值为"low"、"medium"、"high"
     */
    @Value("${char-art.default-density}")
    private final static String defaultDensity = "medium";

    /**
     * 默认颜色模式，可选值为"color"、"grayscale"
     */
    @Value("${char-art.default-color-mode}")
    private final static String defaultColorMode = "grayscale";

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
    public ResponseEntity<Map<String, String>> convertImage(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(value = "density", defaultValue = defaultDensity) String density,
            @RequestParam(value = "colorMode", defaultValue = defaultColorMode) String colorMode,
            @RequestParam(value = "limitSize", defaultValue = "true") boolean limitSize,
            @RequestParam(value = "progressId", required = false) String progressIdParam) {
        
        Path tempFile = null;
        Path resultFile = null;
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

            if (isWebp && isAnimated && !isWebpProcessorEnabled) {
                throw new ServiceException("Webp处理服务未开启，无法处理Webp格式动图");
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
            
            // 确定文件类型
            String contentType = "image/png"; // 默认为PNG
            String resultExtension = ".png"; // 默认扩展名
            
            // 根据文件扩展名设置适当的Content-Type和扩展名
            if (fileExtension != null && !fileExtension.isEmpty()) {
                switch (fileExtension.toLowerCase()) {
                    case ".jpg":
                    case ".jpeg":
                        contentType = "image/jpeg";
                        resultExtension = ".jpg";
                        break;
                    case ".png":
                        contentType = "image/png";
                        resultExtension = ".png";
                        break;
                    case ".gif":
                        contentType = "image/gif";
                        resultExtension = ".gif";
                        break;
                    case ".webp":
                        contentType = "image/webp";
                        resultExtension = ".webp";
                        break;
                }
            }
            
            // 如果是GIF或动态WEBP，覆盖设置为对应的内容类型和扩展名
            if (isGif) {
                contentType = "image/gif";
                resultExtension = ".gif";
            } else if (isWebp && isAnimated) {
                contentType = "image/webp";
                resultExtension = ".webp";
            }

            // 将结果保存到临时文件
            String resultFileName = "result_" + progressId + resultExtension;
            String tempDir = System.getProperty("java.io.tmpdir");
            resultFile = Path.of(tempDir, resultFileName);
            Files.write(resultFile, result);
            
            // 构建相对路径（相对于临时目录）
            String relativePath = resultFile.getFileName().toString();
            log.info("已保存结果到临时文件: {}", relativePath);
            
            // 返回文件路径和内容类型
            Map<String, String> response = new HashMap<>();
            response.put("filePath", relativePath);
            response.put("contentType", contentType);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
            
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
    public ResponseEntity<Map<String,Object>> getCharText(
            @RequestParam("filename") String filename) throws Exception {
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
    public SseEmitter getProgress(
            @PathVariable String id) {
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
                int heartbeatCount = 0; // 心跳计数器

                while (true) {
                    ProgressInfo progressInfo = progressService.getProgress(id);

                    long now = System.currentTimeMillis();
                    long progressInfoTimestamp = progressInfo.getTimestamp();

                    if (progressInfo.getPercentage() >= 0 && progressInfoTimestamp > lastProgressInfoTimestamp) {
                        log.info(progressInfo.toString());
                        // 发送进度更新
                        emitter.send(SseEmitter.event().name("progress").data(progressInfo, MediaType.APPLICATION_JSON));
                        lastProgressInfoTimestamp = progressInfo.getTimestamp();
                        heartbeatCount = 0; // 收到进度更新时重置心跳计数

                        // 如果进度达到100%且isDone为true，发送close事件并关闭连接
                        if (progressInfo.getPercentage() >= 100 && progressInfo.isDone()) {
                            log.info("进度完成，发送关闭事件: {}", id);
                            emitter.send(SseEmitter.event().name("close").data("连接关闭中", MediaType.TEXT_PLAIN));
                            emitter.complete();
                            break;
                        }
                    } else {
                        // 如果没有有效进度信息，则定期发送心跳消息保持连接
                        if (now - lastHeartbeat > 10000) { // 10秒发送一次心跳
                            heartbeatCount++; // 增加心跳计数
                            log.debug("发送心跳 #{}: {}", heartbeatCount, id);
                            emitter.send(SseEmitter.event().name("heartbeat").data("ping", MediaType.TEXT_PLAIN));
                            lastHeartbeat = now;
                            
                            // 如果心跳计数达到12次（约2分钟），发送关闭事件
                            if (heartbeatCount >= 12) {
                                log.info("心跳计数达到12次，发送关闭事件: {}", id);
                                emitter.send(SseEmitter.event().name("close").data("连接关闭中", MediaType.TEXT_PLAIN));
                                emitter.complete();
                                break;
                            }
                        }
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
     * 关闭进度连接
     * <p>
     * 主动关闭指定ID的进度连接，发送关闭事件并清理相关资源。
     * 返回的Map包含两个字段：
     * - success: 布尔值，表示是否成功关闭连接
     * - message: 字符串，操作结果描述
     * </p>
     *
     * @param id 进度ID，用于标识要关闭的特定连接
     * @return 包含操作结果的HTTP响应
     */
    @PostMapping("/progress/{id}/close")
    public ResponseEntity<Map<String, Object>> closeProgress(
            @PathVariable String id) {
        log.info("接收到关闭进度连接请求: {}", id);
        Map<String, Object> result = new HashMap<>();
        
        try {
            progressService.sendCloseEvent(id);
            result.put("success", true);
            result.put("message", "进度连接已关闭");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("关闭进度连接失败: {}, 错误: {}", id, e.getMessage());
            result.put("success", false);
            result.put("message", "关闭进度连接失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
    
    /**
     * 获取WebP处理器的进度流URL
     * <p>
     * 返回WebP处理器服务的SSE进度流URL，前端可以直接连接此URL获取实时进度更新。
     * 返回的Map包含两个字段：
     * - success: 布尔值，表示是否成功获取URL
     * - url: 字符串，成功时为进度流URL，失败时为空字符串
     * </p>
     *
     * @param taskId 任务ID，用于标识要跟踪的特定WebP处理任务
     * @return 包含进度流URL的HTTP响应
     */
    @GetMapping("/webp-progress-url/{taskId}")
    public ResponseEntity<Map<String, Object>> getWebpProgressUrl(
            @PathVariable String taskId) {
        log.info("接收到获取WebP进度流URL请求: {}", taskId);
        Map<String, Object> result = new HashMap<>();
        
        if (!isWebpProcessorEnabled) {
            result.put("success", false);
            result.put("url", "");
            result.put("message", "WebP处理服务未启用");
            return ResponseEntity.ok(result);
        }
        
        try {
            String progressUrl = webpProcessorClient.getProgressStreamUrl(taskId);
            result.put("success", true);
            result.put("url", progressUrl);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取WebP进度流URL失败", e);
            result.put("success", false);
            result.put("url", "");
            result.put("message", "获取进度流URL失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
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
        if(isWebpProcessorEnabled){
            // 检查Flask服务是否可用
            boolean flaskServiceAvailable = webpProcessorClient.isServiceAvailable();

            if(flaskServiceAvailable){
                response.put("status", "UP");
                response.put("webpProcessor", "UP");
                response.put("message", "字符画转换服务正常，Webp处理服务正常");
            }else{
                response.put("status", "UP");
                response.put("webpProcessor", "DOWN");
                response.put("message", "字符画转换服务正常，Webp处理服务异常");
            }
            log.info("健康检查: 主服务状态=UP, Flask服务状态={}", flaskServiceAvailable ? "UP" : "DOWN");
        }else{
            response.put("status", "UP");
            response.put("webpProcessor", "OFF");
            response.put("message", "字符画转换服务正常运行，Webp处理服务未开启");
            log.info("健康检查: 主服务状态=UP, Flask服务状态=OFF");
        }
        return response;
    }
    
    /**
     * 从临时文件夹获取图片数据
     * <p>
     * 根据提供的文件路径，从系统临时目录中读取图片文件并返回。
     * 该接口主要供Python WebP处理器调用，用于获取Java端生成的临时图片文件。
     * 成功返回图片数据后，不会删除原文件，由调用方负责文件的管理。
     * </p>
     *
     * @param filePath 临时文件的路径（相对于系统临时目录的路径）
     * @return 包含图片数据的HTTP响应
     */
    @GetMapping("/get-temp-image/{filePath:.+}")
    public ResponseEntity<byte[]> getTempImage(
            @PathVariable String filePath, HttpServletRequest request) {
        log.info("接收到获取临时图片请求: {}", filePath);
        
        try {
            // URL解码文件路径
            String decodedFilePath = java.net.URLDecoder.decode(filePath, "UTF-8");
            log.info("解码后的文件路径: {}", decodedFilePath);
            
            // 构建完整的文件路径（基于系统临时目录）
            String tempDir = System.getProperty("java.io.tmpdir");
            final Path fullPath = Path.of(tempDir, decodedFilePath);
            
            // 检查文件是否存在
            if (!Files.exists(fullPath)) {
                log.warn("请求的临时文件不存在: {}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            // 检查文件是否是图片
            String fileName = fullPath.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && 
                !fileName.endsWith(".jpeg") && !fileName.endsWith(".gif") && 
                !fileName.endsWith(".bmp") && !fileName.endsWith(".webp")) {
                log.warn("请求的文件不是支持的图片格式: {}", fullPath);
                return ResponseEntity.badRequest().body("不支持的图片格式".getBytes());
            }
            
            // 读取文件内容
            byte[] imageData = Files.readAllBytes(fullPath);
            
            // 设置适当的Content-Type
            String contentType = "image/jpeg"; // 默认
            if (fileName.endsWith(".png")) {
                contentType = "image/png";
            } else if (fileName.endsWith(".gif")) {
                contentType = "image/gif";
            } else if (fileName.endsWith(".bmp")) {
                contentType = "image/bmp";
            } else if (fileName.endsWith(".webp")) {
                contentType = "image/webp";
            }
            
            log.info("成功获取临时图片: {}, 大小: {} 字节", fullPath, imageData.length);
            
            // 启用异步处理，以便在响应完成后执行清理操作
            AsyncContext asyncContext = request.startAsync();
            asyncContext.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    try {
                        if (Files.exists(fullPath)) {
                            // 获取文件所在的目录路径
                            Path dirPath = fullPath.getParent();
                            // 删除文件
                            Files.delete(fullPath);
                            log.info("文件已成功传输并删除: {}", fullPath);
                            
                            // 检查目录是否为空，如果为空则删除
                            if (Files.exists(dirPath) && Files.list(dirPath).findAny().isEmpty()) {
                                Files.delete(dirPath);
                                log.info("空文件夹已删除: {}", dirPath);
                            }
                        }
                    } catch (Exception e) {
                        log.error("删除文件或文件夹时出错: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onTimeout(AsyncEvent event) {
                    // 不需要实现
                }
                
                @Override
                public void onError(AsyncEvent event) {
                    // 不需要实现
                }
                
                @Override
                public void onStartAsync(AsyncEvent event) {
                    // 不需要实现
                }
            });
            
            // 设置异步请求超时时间
            asyncContext.setTimeout(600000);
            
            // 返回图片数据
            ResponseEntity<byte[]> responseEntity = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(imageData.length)
                    .body(imageData);
            
            // 完成异步处理
            asyncContext.complete();
            
            return responseEntity;
            
        } catch (Exception e) {
            log.error("获取临时图片失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("获取临时图片失败: " + e.getMessage()).getBytes());
        }
    }

}