package com.doreamr233.charartconverter.controller;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import com.doreamr233.charartconverter.enums.CloseReason;
import com.doreamr233.charartconverter.enums.EventType;
import com.doreamr233.charartconverter.exception.FileTypeException;
import com.doreamr233.charartconverter.event.ProgressUpdateEvent;
import com.doreamr233.charartconverter.exception.ServiceException;
import com.doreamr233.charartconverter.listener.SseProgressListener;
import com.doreamr233.charartconverter.model.ConvertResult;
import com.doreamr233.charartconverter.model.ProgressInfo;
import com.doreamr233.charartconverter.service.CharArtService;
import com.doreamr233.charartconverter.service.ProgressService;
import com.doreamr233.charartconverter.util.CharArtProcessor;
import com.doreamr233.charartconverter.util.WebpProcessorClient;
import com.doreamr233.charartconverter.config.ParallelProcessingConfig;
import com.doreamr233.charartconverter.config.TempDirectoryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * 字符画转换控制器
 * <p>
 * 提供字符画转换相关的REST API接口，包括图片转换、获取字符文本、进度监控和健康检查。
 * 该控制器处理前端的HTTP请求，调用相应的服务完成字符画转换功能。
 * 支持多种图片格式（JPG、PNG、GIF、WebP）的转换，并提供实时进度监控。
 * </p>
 *
 * @author doreamr233
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class CharArtController {

    /**
     * 字符画服务，用于处理图片到字符画的转换
     * 提供核心的图片转字符画功能
     */
    private final CharArtService charArtService;
    
    /**
     * 进度服务，用于跟踪和报告转换进度
     * 管理SSE连接和进度事件的发送
     */
    private final ProgressService progressService;
    
    /**
     * 初始化临时目录清理回调
     * <p>
     * 在Bean初始化后设置临时目录清理回调函数，
     * 当进度任务完成或出错时自动清理相关的临时文件。
     * </p>
     */
    @PostConstruct
    private void initTempDirectoryCleanup() {
        // 设置临时目录清理回调
        progressService.setTempDirectoryCleanupCallback(this::cleanupTempDirectoryForProgress);
    }
    
    /**
     * WebP处理客户端，用于处理WebP动图和健康检查
     * 与Python WebP处理服务进行通信
     */
    private final WebpProcessorClient webpProcessorClient;
    
    /**
     * 并行处理配置
     * 包含线程池大小、超时时间等配置参数
     */
    private final ParallelProcessingConfig parallelConfig;
    
    /**
     * 临时目录配置
     * 管理临时文件的存储路径
     */
    private final TempDirectoryConfig tempDirectoryConfig;

    /**
     * WebP处理服务是否启用
     * 控制是否支持WebP动图处理功能
     */
    @Value("${webp-processor.enabled}")
    private boolean isWebpProcessorEnabled;

    /**
     * 默认字符密度，可选值为"low"、"medium"、"high"
     * 控制字符画的精细程度
     */
    @Value("${char-art.default-density}")
    private final static String defaultDensity = "medium";

    /**
     * 默认颜色模式，可选值为"color"、"colorBackground"、"grayscale"
     * 控制字符画的颜色显示方式
     */
    @Value("${char-art.default-color-mode}")
    private final static String defaultColorMode = "grayscale";
    
    /**
     * 存储进度ID到临时目录路径的映射关系
     * 用于在特定条件下清理临时文件夹
     */
    private final Map<String, Path> progressTempDirMap = new ConcurrentHashMap<>();

    /**
     * 将图片转换为字符画（异步接口）
     * <p>
     * 接收上传的图片文件，根据指定参数异步转换为字符画。
     * 立即返回进度ID，客户端可通过SSE端点监听转换进度和结果。
     * 支持静态图片（如JPG、PNG）和动态图片（如GIF）的转换。
     * 转换完成后会通过SSE发送包含文件路径和内容类型的结果事件。
     * </p>
     *
     * @param imageFile 上传的图片文件
     * @param density 字符密度，可选值为"low"、"medium"、"high"，默认为"medium"
     * @param colorMode 颜色模式，可选值为"color"、"colorBackground"、"grayscale"，默认为"grayscale"
     * @param limitSize 是否限制字符画的最大尺寸，默认为true
     * @param progressIdParam 进度ID，用于跟踪转换进度，如果不提供则自动生成
     * @return 包含进度ID的HTTP响应，客户端可用此ID监听转换进度
     */
    @PostMapping("/convert")
    public ResponseEntity<Map<String, String>> convertImage(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(value = "density", defaultValue = defaultDensity) String density,
            @RequestParam(value = "colorMode", defaultValue = defaultColorMode) String colorMode,
            @RequestParam(value = "limitSize", defaultValue = "true") boolean limitSize,
            @RequestParam(value = "progressId", required = false) String progressIdParam) {
        
        try {
            String originalFilename = imageFile.getOriginalFilename();
            log.debug("接收到图片转换请求: {}, 密度: {}, 颜色模式: {}, 限制尺寸: {}", 
                    originalFilename, density, colorMode, limitSize);
            
            // 使用前端传递的progressId，如果没有则生成一个新的
            String progressId = (progressIdParam != null && !progressIdParam.isEmpty()) 
                ? progressIdParam : UUID.randomUUID().toString();
            
            log.debug("使用进度ID: {}", progressId);

            // 在处理前先读取文件内容到字节数组，避免多次读取InputStream
            byte[] fileBytes;
            try {
                fileBytes = imageFile.getBytes();
                log.debug("已读取上传文件到内存，大小: {} 字节", fileBytes.length);
            } catch (Exception e) {
                log.error("读取上传文件失败: {}", e.getMessage(), e);
                throw new ServiceException("读取上传文件失败: " + e.getMessage(), e);
            }

            // 判断文件类型
            String fileType = FileTypeUtil.getType(new java.io.ByteArrayInputStream(fileBytes));
            log.debug("传入的图片类型: {}", fileType);
            
            // 验证文件类型是否支持
            if (fileType == null || !(fileType.equals("jpg") || fileType.equals("jpeg") || 
                fileType.equals("png") || fileType.equals("gif") || fileType.equals("webp"))) {
                throw new FileTypeException("不支持的文件类型: " + fileType);
            }

            // 立即返回进度ID
            Map<String, String> response = new HashMap<>();
            response.put("progressId", progressId);
            response.put("status", "processing");
            response.put("message", "转换任务已启动，请通过SSE监听进度");

            // 异步执行转换任务
            CompletableFuture.runAsync(() -> {
                Path tempDir;
                Path tempFile;
                Path resultFile;
                try {
                    // 为此次转换创建专用的临时目录
                    tempDir = CharArtProcessor.createTempDirectoryForFile(originalFilename);
                    // 注册临时目录映射关系
                    registerTempDirectory(progressId, tempDir);
                    log.debug("为进度ID: {} 创建临时目录: {}", progressId, tempDir);
                    // 将上传的文件保存为临时文件，以便多次读取
                    // 提取文件扩展名，确保临时文件具有正确的扩展名
                    String fileExtension = getString(originalFilename, fileType);
                    log.debug("使用文件扩展名: {}", fileExtension);
                    tempFile = CharArtProcessor.createTempFileInDirectory(tempDir, "upload_", fileExtension);
                    // 使用字节数组写入文件，而不是InputStream
                    FileUtil.writeBytes(fileBytes, tempFile.toFile());

                    boolean isGif = originalFilename != null && originalFilename.toLowerCase().endsWith(".gif") && "gif".equals(fileType);
                    boolean isWebp = originalFilename != null && originalFilename.toLowerCase().endsWith(".webp") && "webp".equals(fileType);
                    boolean isAnimated = false;

                    // 如果是webp格式，判断是否为动图
                    if (isWebp) {
                        isAnimated = CharArtProcessor.isWebpAnimated(tempFile);
                        log.debug("WebP图片是否为动图: {}", isAnimated);
                    }

                    if (isWebp && isAnimated && !isWebpProcessorEnabled) {
                        throw new ServiceException("Webp处理服务未开启，无法处理Webp格式动图");
                    }

                    // 执行转换
                    byte[] result;
                    try {
                        result = charArtService.convertToCharArt(
                                FileUtil.getInputStream(tempFile.toFile()),
                                originalFilename,
                                density,
                                colorMode,
                                progressId,
                                limitSize,
                                tempDir
                        );
                    } catch (Exception e) {
                        // 将IOException包装为ServiceException
                        throw new ServiceException("读取临时文件失败: " + e.getMessage(), e);
                    }
                    
                    // 确定文件类型
                    String contentType = "image/png"; // 默认为PNG
                    String resultExtension = ".png"; // 默认扩展名
                    
                    // 根据文件扩展名设置适当的Content-Type和扩展名
                    if (!fileExtension.isEmpty()) {
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

                    // 将结果保存到专用临时目录中
                    String resultFileName = "result_" + progressId + resultExtension;
                    resultFile = tempDir.resolve(resultFileName);
                    FileUtil.writeBytes(result, resultFile.toFile());
                    
                    // 构建相对路径（包含临时文件夹）
                    String relativePath = tempDir.getFileName().toString() + "/" + resultFile.getFileName().toString();
                    log.debug("已保存结果到临时文件: {}", relativePath);
                    
                    // 通过SSE发送转换结果
                    progressService.sendConvertResultEvent(progressId, relativePath, contentType);
                    
                    log.debug("图片转换完成，进度ID: {}, 文件路径: {}", progressId, relativePath);

                    // 转换完成，发送关闭SSE消息
                    progressService.sendCloseEvent(progressId, CloseReason.TASK_COMPLETED);
                } catch (Exception e) {
                    log.error("异步转换图片失败，进度ID: {}", progressId, e);
                    // 发送错误事件
                    progressService.updateProgress(progressId, 0, "转换失败: " + e.getMessage(), "错误", 0, 0, true);
                    // Java端报错时发送错误关闭事件，由ProgressService统一处理临时文件清理
                    progressService.sendCloseEvent(progressId, CloseReason.ERROR_OCCURRED);
                }
            });

            log.debug("异步转换任务已启动，进度ID: {}", progressId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
                    
        } catch (Exception e) {
            log.error("图片转换失败", e);
            throw new ServiceException("图片转换失败: " + e.getMessage(), e);
        }
    }

    @NotNull
    private static String getString(String originalFilename, String fileType) {
        String fileExtension;
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else if (fileType != null) {
            // 如果无法从文件名获取扩展名，则使用检测到的文件类型
            fileExtension = "." + fileType;
        } else {
            // 默认扩展名
            fileExtension = ".tmp";
        }
        return fileExtension;
    }
    
    /**
     * 注册临时目录映射关系
     * <p>
     * 将进度ID与对应的临时目录路径建立映射关系，
     * 便于后续根据进度ID清理相关的临时文件。
     * </p>
     *
     * @param progressId 进度ID，用于标识特定的转换任务
     * @param tempDir 临时目录路径，存储该任务相关的临时文件
     */
    private void registerTempDirectory(String progressId, Path tempDir) {
        progressTempDirMap.put(progressId, tempDir);
        log.debug("注册临时目录映射: {} -> {}", progressId, tempDir);
    }
    
    /**
     * 清理指定进度ID的临时文件夹
     * <p>
     * 根据进度ID查找对应的临时目录，并删除该目录及其所有内容。
     * 这是一个回调方法，由ProgressService在适当的时机调用。
     * </p>
     *
     * @param progressId 进度ID，用于查找要清理的临时目录
     */
    public void cleanupTempDirectoryForProgress(String progressId) {
        Path tempDir = progressTempDirMap.remove(progressId);
        if (tempDir != null) {
            try {
                CharArtProcessor.deleteTempDirectory(tempDir);
                log.debug("已清理临时文件夹: {}", tempDir);
            } catch (Exception e) {
                log.error("清理临时文件夹失败: {}", tempDir, e);
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
            @RequestParam("filename") String filename) {
        log.debug("接收到获取字符画文本请求: {}", filename);
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
        log.debug("收到进度请求: {}", id);
        
        // 创建一个SSE发射器
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        // 创建SSE监听器
        SseProgressListener listener = new SseProgressListener(id, emitter);
        
        // 注册监听器
        progressService.addProgressListener(listener);
        
        // 设置错误处理
        emitter.onError((ex) -> {
            // 检查是否是正常的任务完成导致的连接关闭
            if (ex instanceof java.io.IOException && ex.getMessage() != null && 
                (ex.getMessage().contains("Broken pipe") || ex.getMessage().contains("Connection reset"))) {
                log.debug("SSE连接正常关闭: {}, 关闭原因: {}", id, ex.getMessage());
            } else {
                log.error("SSE连接错误: {}, 错误: {}", id, ex.getMessage());
            }
            progressService.removeProgressListener(listener);
        });
        
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成: {}", id);
            progressService.removeProgressListener(listener);
        });
        
        emitter.onTimeout(() -> {
            log.debug("Vue端SSE连接超时: {}", id);
            progressService.removeProgressListener(listener);
            // Vue端SSE心跳超时时发送超时关闭事件，由ProgressService统一处理临时文件清理
            progressService.sendCloseEvent(id, CloseReason.HEARTBEAT_TIMEOUT);
        });
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        // 在单独的线程中处理SSE
        executor.submit(() -> {
            try {
                // 发送初始化进度信息
                ProgressInfo currentProgress = progressService.getProgress(id);
                if (currentProgress != null) {
                    emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(currentProgress));
                }
                
                // 检查是否有待处理的事件
                ConvertResult latestEvent = progressService.getLatestEvent(id);
                if (latestEvent != null) {
                    // 转换结果事件直接发送ConvertResult对象
                    emitter.send(SseEmitter.event()
                            .name("convertResult")
                            .data(latestEvent));
                }
                
                // 使用阻塞队列等待事件
                while (listener.isActive()) {
                    try {
                        // 等待新事件，超时时间为10s
                        ProgressUpdateEvent event = listener.waitForEvent(10, TimeUnit.SECONDS);
                        if (event != null) {
                            // 处理接收到的事件
                            handleProgressEvent(emitter, event);
                            
                            // 如果是关闭事件，完成连接
                            if (event.getEventType() == EventType.CLOSE_EVENT) {
                                // 根据关闭原因决定是否记录日志
                                if (event.getCloseReason() == CloseReason.TASK_COMPLETED) {
                                    log.debug("任务完成，正常关闭SSE连接: {}", id);
                                } else {
                                    log.debug("收到关闭事件，准备完成SSE连接: {}, 原因: {}", id, event.getCloseReason());
                                }
                                try {
                                    emitter.complete();
                                } catch (IllegalStateException e) {
                                    log.debug("SSE连接已完成，无需重复关闭: {}", e.getMessage());
                                }
                                break;
                            }
                        } else {
                            // 超时，发送心跳
                            emitter.send(SseEmitter.event()
                                    .name("heartbeat")
                                    .data("ping"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.debug("SSE处理线程被中断: {}", id);
                        break;
                    }
                }
            } catch (Exception e) {
                // 检查是否是正常地连接关闭导致的异常
                if (e instanceof java.io.IOException && e.getMessage() != null && 
                    (e.getMessage().contains("Broken pipe") || e.getMessage().contains("Connection reset") || 
                     e.getMessage().contains("Connection closed"))) {
                    log.debug("SSE连接正常关闭: {}, 原因: {}", id, e.getMessage());
                } else {
                    log.error("SSE处理异常: {}, 错误: {}", id, e.getMessage());
                    try {
                        // 发送错误关闭事件
                        progressService.sendCloseEvent(id, CloseReason.ERROR_OCCURRED);
                        // 等待一小段时间确保客户端接收到关闭事件
                        long progressUpdateInterval = parallelConfig != null ? parallelConfig.getProgressUpdateInterval() : 500L;
                        Thread.sleep(progressUpdateInterval);
                        // 检查emitter状态，避免在已完成的连接上调用completeWithError
                        emitter.completeWithError(e);
                    } catch (IllegalStateException ex) {
                        log.debug("SSE连接已完成，无法发送错误: {}", ex.getMessage());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        log.debug("等待过程中被中断: {}", ex.getMessage());
                    } catch (Exception ex) {
                        log.error("完成SSE时发生错误: {}", ex.getMessage());
                    }
                }
            } finally {
                // 确保移除监听器
                progressService.removeProgressListener(listener);
                executor.shutdown();
            }
        });
        
        return emitter;
    }
    

    
    /**
     * 处理进度事件
     * <p>
     * 根据事件类型将进度更新事件转换为相应的SSE事件格式，
     * 并通过SseEmitter发送给客户端。支持进度更新、转换结果
     * 和连接关闭等多种事件类型。
     * </p>
     *
     * @param emitter SSE发射器，用于向客户端发送事件
     * @param event 进度更新事件，包含事件类型和相关数据
     * @throws Exception 发送事件时可能抛出的异常
     */
    private void handleProgressEvent(SseEmitter emitter, ProgressUpdateEvent event) throws Exception {
        String eventName;
        Object eventData;
        
        switch (event.getEventType()) {
            case PROGRESS_UPDATE:
                eventName = "progress";
                eventData = event.getProgressInfo();
                break;
            case CONVERT_RESULT:
                eventName = "convertResult";
                // 转换结果使用ConvertResult传输
                eventData = event.getConvertResult();
                break;
            case CLOSE_EVENT:
                eventName = "close";
                // 关闭事件使用包含关闭原因的数据结构
                Map<String, Object> closeData = new HashMap<>();
                closeData.put("progressInfo", event.getProgressInfo());
                closeData.put("closeReason", event.getCloseReason());
                closeData.put("message", event.getProgressInfo().getMessage());
                eventData = closeData;
                break;
            default:
                eventName = "event";
                eventData = event.getProgressInfo();
                break;
        }
        
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(eventData));
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
     * @param closeReason 关闭原因，可选参数，用于决定日志级别
     * @return 包含操作结果的HTTP响应
     */
    @PostMapping(value = "/progress/close/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> closeProgress(
            @PathVariable String id,
            @RequestParam(required = false) String closeReason) {
        
        // 根据关闭原因决定日志级别
        if ("ERROR_OCCURRED".equals(closeReason) || "HEARTBEAT_TIMEOUT".equals(closeReason)) {
            log.warn("接收到关闭进度连接请求: {}, 原因: {}", id, closeReason);
        } else {
            log.debug("接收到关闭进度连接请求: {}, 原因: {}", id, closeReason != null ? closeReason : "TASK_COMPLETED");
        }
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 根据关闭原因发送相应的关闭事件
            CloseReason reason = CloseReason.parseCloseReason(closeReason);
            progressService.sendCloseEvent(id, reason);
            
            result.put("success", true);
            result.put("message", "进度连接已关闭");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // 只有在非正常关闭时才记录错误日志
            if ("ERROR_OCCURRED".equals(closeReason)) {
                log.error("关闭进度连接失败: {}, 错误: {}", id, e.getMessage());
            } else {
                log.debug("关闭进度连接时发生异常: {}, 错误: {}", id, e.getMessage());
            }
            
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
        log.debug("接收到获取WebP进度流URL请求: {}", taskId);
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
            log.debug("健康检查: 主服务状态=UP, Flask服务状态={}", flaskServiceAvailable ? "UP" : "DOWN");
        }else{
            response.put("status", "UP");
            response.put("webpProcessor", "OFF");
            response.put("message", "字符画转换服务正常运行，Webp处理服务未开启");
            log.debug("健康检查: 主服务状态=UP, Flask服务状态=OFF");
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
     * @param tempDirName 临时文件夹名称
     * @param fileName 文件名
     * @return 包含图片数据的HTTP响应
     */
    @GetMapping("/get-temp-image/{tempDirName}/{fileName:.+}")
    public ResponseEntity<byte[]> getTempImage(
            @PathVariable String tempDirName, 
            @PathVariable String fileName, 
            HttpServletRequest request) {
        log.debug("接收到获取临时图片请求: 临时文件夹={}, 文件名={}", tempDirName, fileName);
        
        try {
            // URL解码参数
            String decodedTempDirName = java.net.URLDecoder.decode(tempDirName, StandardCharsets.UTF_8);
            String decodedFileName = java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            log.debug("解码后的参数: 临时文件夹={}, 文件名={}", decodedTempDirName, decodedFileName);
            
            // 构建完整的文件路径（基于配置的临时目录）
            String tempDir = tempDirectoryConfig.getTempDirectory();
            final Path fullPath = Path.of(tempDir, decodedTempDirName, decodedFileName);
            
            // 检查文件是否存在
            if (!FileUtil.exist(fullPath.toFile())) {
                log.warn("请求的临时文件不存在: {}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            // 检查文件是否是图片
            fileName = fullPath.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && 
                !fileName.endsWith(".jpeg") && !fileName.endsWith(".gif") && 
                !fileName.endsWith(".bmp") && !fileName.endsWith(".webp")) {
                log.warn("请求的文件不是支持的图片格式: {}", fullPath);
                return ResponseEntity.badRequest().body("不支持的图片格式".getBytes());
            }
            
            // 读取文件内容
            byte[] imageData = FileUtil.readBytes(fullPath.toFile());
            
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
            
            log.debug("成功获取临时图片: {}, 大小: {} 字节", fullPath, imageData.length);
            
            // 启用异步处理，以便在响应完成后执行清理操作
            AsyncContext asyncContext = request.startAsync();
            asyncContext.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    try {
                        if (FileUtil.exist(fullPath.toFile())) {
                            // 获取文件所在的目录路径
                            Path dirPath = fullPath.getParent();
                            // 删除文件
                            FileUtil.del(fullPath.toFile());
                            log.debug("文件已成功传输并删除: {}", fullPath);

                            // 检查目录是否为空，如果为空则删除
                            if (FileUtil.exist(dirPath.toFile())) {
                                if (FileUtil.isDirEmpty(dirPath.toFile())) {
                                    FileUtil.del(dirPath.toFile());
                                    log.debug("空文件夹已删除: {}", dirPath);
                                }
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
            long taskTimeout = parallelConfig != null ? parallelConfig.getTaskTimeout() : 600000L;
            asyncContext.setTimeout(taskTimeout);
            
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