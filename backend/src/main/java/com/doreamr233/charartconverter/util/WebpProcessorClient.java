package com.doreamr233.charartconverter.util;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.http.*;
import com.doreamr233.charartconverter.enums.CloseReason;
import com.doreamr233.charartconverter.exception.ServiceException;
import com.doreamr233.charartconverter.model.WebpProcessResult;
import com.doreamr233.charartconverter.service.ProgressService;
import com.doreamr233.charartconverter.config.ParallelProcessingConfig;
import com.doreamr233.charartconverter.config.TempDirectoryConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import cn.hutool.core.io.FileUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

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
     * WebP处理进度服务
     */
    @Resource
    private ProgressService progressService;
    
    /**
     * 并行处理配置
     */
    @Resource
    private ParallelProcessingConfig parallelConfig;
    
    /**
     * 临时目录配置
     */
    @Resource
    private TempDirectoryConfig tempDirectoryConfig;
    
    /**
     * 存储任务ID与对应的SSE连接Call对象
     */
    private final Map<String, Call> taskCalls = new ConcurrentHashMap<>();

    /**
     * 存储每个任务的心跳计数
     */
    private final Map<String, Integer> heartbeatCounters = new ConcurrentHashMap<>();
    
    /**
     * 存储待完成的CompletableFuture
     */
    private final Map<String, CompletableFuture<WebpProcessResult>> pendingFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<File>> pendingAnimationFutures = new ConcurrentHashMap<>();
    
    /**
     * 存储任务ID与临时目录的映射
     */
    private final Map<String, Path> progressTempDirMap = new ConcurrentHashMap<>();
    


    /**
     * 执行带有重试机制的HTTP请求
     *
     * @param request HTTP请求
     * @return HTTP响应
     * @throws HttpException 如果请求失败
     */
    private HttpResponse executeWithRetry(HttpRequest request) throws HttpException {
        HttpResponse response;
        for (int i = 0; i <= maxRetriesCount; i++) {
            try {
                log.debug("WebP处理服务第{}次尝试，共{}次", i+1,maxRetriesCount+1);
                // 执行 HTTP 请求
                if (i > 0){
                    try {
                        TimeUnit.MILLISECONDS.sleep(500L * i);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new HttpException("请求被中断");
                    }
                }
                response = request.execute();
                if (response.isOk()) {
                    return response;
                }else {
                    log.warn("WebP处理服务第{}次尝试失败，响应状态码: {}", i+1, response.getStatus());
                }
            } catch (Exception e) {
                // 发生异常时打印堆栈信息
                log.error("WebP处理服务第{}次尝试失败", i+1, e);
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
        log.debug("WebP处理服务的基本URL：{}", serviceBaseUrl);
        log.debug("WebP处理服务的连接超时时间（毫秒）：{}", maxTimeout);
        log.debug("WebP处理服务的最大重试次数：{}", maxRetriesCount);
        try {
            HttpResponse response = executeWithRetry(HttpUtil.createGet(serviceBaseUrl + "/api/health").timeout(maxTimeout));
            return response.getStatus() == HttpStatus.HTTP_OK;
        } catch (Exception e) {
            log.warn("WebP处理服务不可用: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建一个新的进度任务
     *
     * @return 任务ID，如果创建失败则返回null
     */
    public String createProgressTask() {
        try {
            log.debug("创建WebP处理进度任务");
            
            // 发送请求并获取响应
            HttpResponse response = executeWithRetry(HttpUtil.createPost(serviceBaseUrl + "/api/progress/create")
                    .timeout(maxTimeout));
            
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                log.warn("创建进度任务失败: {} {}", response.getStatus(), response.body());
                return null;
            }
            
            // 解析JSON响应获取任务ID
            JSONObject jsonResponse = new JSONObject(response.body());
            String taskId = jsonResponse.getString("task_id");
            log.debug("成功创建进度任务，ID: {}", taskId);
            
            return taskId;
        } catch (Exception e) {
            log.error("创建进度任务时发生错误", e);
            return null;
        }
    }
    
    /**
     * 获取进度更新的SSE流URL
     *
     * @param taskId 任务ID
     * @return 进度更新的SSE流URL
     */
    public String getProgressStreamUrl(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }
        return serviceBaseUrl + "/api/progress/" + taskId;
    }
    
    /**
     * 监听Flask SSE进度消息并转发到ProgressService
     * <p>
     * 该方法会建立与Flask服务的SSE连接，接收进度更新消息，
     * 并将其转换为ProgressInfo对象，然后通过ProgressService发送。
     * 该方法会在新线程中运行，直到进度达到100%或发生错误。
     * </p>
     *
     * @param oriTaskId 前端SSE进度任务ID
     * @param webPTaskId webP处理器SSE进度任务ID
     * @param connectionLatch 线程同步的CountDownLatch
     */
    public void listenToProgressStream(String oriTaskId,String webPTaskId, CountDownLatch connectionLatch) {
        if (oriTaskId == null || oriTaskId.isEmpty() || webPTaskId == null || webPTaskId.isEmpty()) {
            log.error("无法监听进度流：任务ID为空");
            if (connectionLatch != null) {
                connectionLatch.countDown(); // 确保在错误情况下也释放锁
            }
            return;
        }
        
        String streamUrl = getProgressStreamUrl(webPTaskId);
        log.debug("开始监听Flask SSE进度流: {}", streamUrl);
        
        // 在新线程中执行，避免阻塞主线程
        Thread progressThread = new Thread(() -> {
            // 创建OkHttpClient实例
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(maxTimeout * 2L, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS) // 无限读取超时，因为SSE是长连接
                    .retryOnConnectionFailure(true)
                    .build();
            
            // 创建请求
            Request request = new Request.Builder()
                    .url(streamUrl)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .build();
            
            log.debug("SSE请求头: Accept={}, Cache-Control={}, Connection={}", 
                    request.header("Accept"), 
                    request.header("Cache-Control"), 
                    request.header("Connection"));
            
            // 使用OkHttp执行请求
            log.debug("正在建立Flask SSE连接: {}", streamUrl);
            Call call = client.newCall(request);
            
            // 将Call对象存储到Map中，以便在需要时取消
            taskCalls.put(webPTaskId, call);
            
            try (Response response = call.execute()) {
                // 检查响应状态
                if (!response.isSuccessful()) {
                    log.error("连接Flask SSE进度流失败: 状态码={}, 响应体={}", response.code(), response.body() != null ? response.body().string() : "无响应体");
                    if (connectionLatch != null) {
                        connectionLatch.countDown(); // 异常时也要释放锁
                    }
                    return;
                }
                
                // 连接建立后释放锁
                if (connectionLatch != null) {
                    connectionLatch.countDown(); // 通知主线程连接就绪
                }
                
                // 检查响应头
                String contentType = response.header("Content-Type");
                log.debug("成功建立Flask SSE连接: URL={}, 状态码={}, Content-Type={}", 
                        streamUrl, response.code(), contentType);
                
                if (contentType == null || !contentType.contains("text/event-stream")) {
                    log.warn("Flask服务器响应的Content-Type不是text/event-stream: {}", contentType);
                }
                
                // 获取响应体
                ResponseBody body = response.body();
                if (body == null) {
                    log.error("Flask SSE响应体为空");
                    return;
                }
                
                // 使用BufferedReader读取SSE事件流
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
                    String line;
                    StringBuilder eventData = new StringBuilder();
                    String eventName = null;
                    
                    while ((line = reader.readLine()) != null) {
                        log.debug("收到SSE行: {}", line);
                        
                        // 空行表示事件结束
                        if (line.isEmpty()) {
                            if (eventData.length() > 0) {
                                log.debug("处理SSE事件: 名称={}, 数据长度={}", eventName, eventData.length());
                                // 处理事件数据
                                int closeCode = processEventData(eventName, eventData.toString(), oriTaskId,webPTaskId);
                                eventData = new StringBuilder();
                                eventName = null;
                                
                                // 如果处理结果表明应该关闭连接，则退出循环
                                if (closeCode != 0  && closeCode != 1) {
                                    log.debug("任务{}出错，主动关闭SSE连接", webPTaskId);
                                    closeProgressConnection(webPTaskId);
                                    break;
                                } else if (closeCode != 0) {
                                    log.debug("任务{}正常完成", webPTaskId);
                                    break;
                                }
                            } else {
                                log.debug("收到空事件");
                            }
                            continue;
                        }
                        
                        // 解析事件行
                        if (line.startsWith("event:")) {
                            eventName = line.substring("event:".length()).trim();
                        } else if (line.startsWith("data:")) {
                            eventData.append(line.substring("data:".length()).trim());
                        }
                    }
                    
                    log.debug("SSE连接已关闭: {}", streamUrl);
                }
            } catch (Exception e) {
                if (connectionLatch != null) {
                    connectionLatch.countDown(); // 异常时也要释放锁
                }
                
                // 检查是否是正常地连接关闭导致的异常
                if (e instanceof java.io.IOException && e.getMessage() != null && 
                    (e.getMessage().contains("Socket closed") || e.getMessage().contains("Connection reset") || 
                     e.getMessage().contains("Stream closed") || e.getMessage().contains("Broken pipe"))) {
                    log.debug("Flask SSE连接正常关闭: {}, 原因: {}", webPTaskId, e.getMessage());
                } else {
                    log.error("监听Flask SSE进度流时发生错误", e);
                    
                    // 发送错误关闭事件到前端
                try {
                    progressService.sendCloseEvent(oriTaskId, CloseReason.ERROR_OCCURRED);
                } catch (Exception ex) {
                    log.error("发送错误关闭事件失败", ex);
                }
                }
            } finally {
                if (connectionLatch != null) {
                    connectionLatch.countDown(); // 异常时也要释放锁
                }
                // 从Map中移除Call对象
                taskCalls.remove(webPTaskId);
                // 清除心跳计数
                heartbeatCounters.remove(webPTaskId);
            }
        });
        progressThread.setName("SSE-Progress-" + webPTaskId);
        progressThread.setDaemon(true); // 设置为守护线程，避免阻止JVM退出
        progressThread.start();
        log.debug("已启动进度监听线程: {}", progressThread.getName());
    }
    
    /**
     * 处理SSE事件数据
     *
     * @param eventName 事件名称
     * @param eventData 事件数据
     * @param oriTaskId 前端SSE进度任务ID
     * @param webPTaskId webP处理器SSE进度任务ID
     * @return 0-任务未完成 1-任务正常完成 2-python端心跳超时 3-python端报错
     */
    
    private int processEventData(String eventName, String eventData, String oriTaskId, String webPTaskId) {
        try {
            log.debug("处理SSE事件: 名称={}, 数据={}，任务编号={}", eventName, eventData,webPTaskId);
            // 处理心跳事件
            if ("heartbeat".equals(eventName)) {
                log.debug("收到Flask心跳事件");
                // 增加心跳计数
                int count = heartbeatCounters.getOrDefault(webPTaskId, 0) + 1;
                heartbeatCounters.put(webPTaskId, count);
                log.debug("任务{}的心跳计数: {}", webPTaskId, count);
                
                // 如果心跳计数超过12次，发送心跳超时关闭事件并关闭SSE连接
                if (count >= 12) {
                    log.debug("任务{}的心跳计数达到{}次，准备关闭SSE连接（心跳超时）", webPTaskId, count);
                    // 发送心跳超时关闭事件到前端
                    progressService.sendCloseEvent(oriTaskId, CloseReason.HEARTBEAT_TIMEOUT);
                    // 发送close消息到Python端，传递超时原因（会自动关闭SSE连接）
                    sendCloseMessage(webPTaskId, "HEARTBEAT_TIMEOUT");
                    // 清除心跳计数
                    heartbeatCounters.remove(webPTaskId);
                    return 2;
                }
                return 0;
            }
            // 处理close消息
            if ("close".equals(eventName)) {
                log.debug("收到Flask关闭事件，准备优雅关闭SSE连接");
                CloseReason closeReason = CloseReason.TASK_COMPLETED;
                String message;
                
                try {
                    // 解析关闭事件数据，判断关闭原因
                    JSONObject closeData = new JSONObject(eventData);
                    message = closeData.optString("message", "连接关闭中");
                    String reason = closeData.optString("reason", "completed");
                    
                    // 根据Python端发送的原因确定关闭类型
                    closeReason = CloseReason.parseCloseReasonByPython(reason);
                    log.debug("Flask关闭事件: 消息={}, 原因={}", message, reason);
                } catch (Exception e) {
                    log.debug("解析关闭事件数据失败，使用默认处理: {}", e.getMessage());
                }

                sendCloseMessage(webPTaskId, closeReason.toString());
                // 清除心跳计数
                heartbeatCounters.remove(webPTaskId);
                log.debug("已优雅关闭与Flask的SSE连接: {}", webPTaskId);
                

                
                switch (closeReason){
                    case TASK_COMPLETED:
                        return 1;
                    case ERROR_OCCURRED:
                        return 3;
                    default:
                        return 0;
                }
            }
            // 处理WebP处理进度事件
            if ("webp".equals(eventName)){
                // 解析JSON数据
                JSONObject jsonData = new JSONObject(eventData);

                // 提取进度信息字段 - 根据Flask服务器发送的字段名进行匹配
                int percentage = jsonData.optInt("progress", 0);
                String message = jsonData.optString("message", "处理中");
                String stage = jsonData.optString("status", "处理中");
                int currentPixel = jsonData.optInt("current_step", 0);
                int totalPixels = jsonData.optInt("total_steps", 0);
                boolean isDone = jsonData.optBoolean("is_done", false);

                log.debug("收到Flask进度更新: 任务={}, 进度={}%, 消息={}, 状态={}",
                        webPTaskId, percentage, message, stage);

                // 使用ProgressService更新进度
                // 此处isDone用于判断Flask端任务是否执行完毕
                progressService.updateProgress(oriTaskId, percentage, message, stage, currentPixel, totalPixels,false);

                // 如果进度达到100%，结束监听
                if (isDone) {
                    log.debug("任务{}已完成", webPTaskId);
                }
            }
            // 处理WebP处理结果事件
            if ("webp_result".equals(eventName)) {
                log.debug("收到WebP处理结果事件: 任务={}", webPTaskId);
                // 解析JSON数据
                JSONObject jsonData = new JSONObject(eventData);
                if (jsonData.has("webp")) {
                    // 这是WebP动画创建的结果
                    try {
                        String webpPath = jsonData.optString("webp", "");
                        log.debug("从SSE事件中获取到webp路径: {}", webpPath);
                        log.debug("完整的JSON数据: {}", jsonData);
                        
                        // 更新进度
                        progressService.updateProgress(oriTaskId, 98, "开始获取WebP动画文件", "文件获取", 0, 1, false);
                        
                        // 构建WebP文件URL
                        String webpUrl = serviceBaseUrl + "/api/get-image/" + webpPath;
                        log.debug("构建的WebP文件URL: {}", webpUrl);
                        
                        // 使用HttpUtil获取WebP文件
                        log.debug("开始请求WebP文件: {}", webpUrl);
                        HttpResponse webpResponse = executeWithRetry(HttpUtil.createGet(webpUrl).timeout(maxTimeout));
                        log.debug("HTTP响应状态: {}, 响应体长度: {}", webpResponse.getStatus(), webpResponse.bodyBytes().length);
                        
                        if (webpResponse.getStatus() != HttpStatus.HTTP_OK) {
                            log.error("获取WebP文件失败: 状态码={}, 响应体={}", webpResponse.getStatus(), webpResponse.body());
                            progressService.updateProgress(oriTaskId, 100, "获取WebP文件失败", "错误", 0, 0, true);
                            
                            // 完成对应的CompletableFuture并传递异常
                            CompletableFuture<File> animationFuture = pendingAnimationFutures.remove(webPTaskId);
                            if (animationFuture != null) {
                                animationFuture.completeExceptionally(new ServiceException("获取WebP文件失败: " + webpResponse.getStatus()));
                            }
                        } else {
                            // 将响应体保存为临时文件
                            byte[] webpData = webpResponse.bodyBytes();
                            File tempFile = new File(tempDirectoryConfig.getTempDirectory(), "animation_" + System.currentTimeMillis() + ".webp");
                            FileUtil.writeBytes(webpData, tempFile);
                            
                            // 更新最终进度
                            progressService.updateProgress(oriTaskId, 100, "WebP动画文件获取完成", "完成", 1, 1, false);
                            
                            log.debug("WebP动画创建成功，保存到临时文件: {}", tempFile.getAbsolutePath());
                            
                            // 完成对应的CompletableFuture
                            CompletableFuture<File> animationFuture = pendingAnimationFutures.remove(webPTaskId);
                            if (animationFuture != null) {
                                animationFuture.complete(tempFile);
                                log.debug("WebP动画文件已通过CompletableFuture返回");
                            } else {
                                log.warn("未找到对应的动画CompletableFuture: {}", webPTaskId);
                            }
                        }

                        // WebP动画创建完成，主动关闭SSE连接
                        log.debug("WebP动画处理完成，主动关闭SSE连接: {}", webPTaskId);
                        sendCloseMessage(webPTaskId);
                        return 1; // WebP动画创建完成，关闭SSE连接
                    } catch (Exception e) {
                        log.error("处理WebP结果时发生错误", e);
                        progressService.updateProgress(oriTaskId, 100, "处理WebP结果时发生错误: " + e.getMessage(), "错误", 0, 0, true);
                        
                        // 完成对应的CompletableFuture并传递异常
                        CompletableFuture<File> animationFuture = pendingAnimationFutures.remove(webPTaskId);
                        if (animationFuture != null) {
                            animationFuture.completeExceptionally(new ServiceException("处理WebP结果时发生错误: " + e.getMessage(), e));
                        }
                        
                        // 处理出错时也要主动关闭SSE连接
                        log.debug("WebP动画处理出错，主动关闭SSE连接: {}", webPTaskId);
                        sendCloseMessage(webPTaskId, "ERROR_OCCURRED");
                        return 3; // 处理出错，也要关闭SSE连接
                    }
                } else if(jsonData.has("frameCount") && jsonData.has("delays") && jsonData.has("frames")) {
                    // 检查是否为WebP解析结果（包含frameCount、delays、frames字段）
                    int frameCount = jsonData.optInt("frameCount", 0);
                    if (frameCount > 0) {
                        // 这是WebP解析的结果
                        log.debug("收到WebP解析结果: 帧数={}", frameCount);
                        try {
                            // 使用parseResponse方法解析响应
                            WebpProcessResult result = parseResponse(eventData, oriTaskId);
                            
                            // 完成对应的CompletableFuture
                            CompletableFuture<WebpProcessResult> future = pendingFutures.remove(webPTaskId);
                            if (future != null) {
                                future.complete(result);
                                log.debug("WebP解析结果已通过CompletableFuture返回");
                            } else {
                                log.warn("未找到其对应的CompletableFuture: {}", webPTaskId);
                            }

                            // WebP解析完成，主动关闭SSE连接
                            log.debug("WebP解析处理完成，主动关闭SSE连接: {}", webPTaskId);
                            sendCloseMessage(webPTaskId);
                            return 1; // WebP解析完成，关闭SSE连接
                        } catch (Exception e) {
                            log.error("解析WebP处理结果时发生错误", e);
                            // 完成Future并传递异常
                            CompletableFuture<WebpProcessResult> future = pendingFutures.remove(webPTaskId);
                            if (future != null) {
                                future.completeExceptionally(new ServiceException("解析WebP处理结果时发生错误: " + e.getMessage(), e));
                            }
                            progressService.updateProgress(oriTaskId, 100, "解析WebP处理结果时发生错误: " + e.getMessage(), "错误", 0, 0, true);
                            
                            // 处理出错时也要主动关闭SSE连接
                            log.debug("WebP解析处理出错，主动关闭SSE连接: {}", webPTaskId);
                            sendCloseMessage(webPTaskId, "ERROR_OCCURRED");
                            return 3; // 处理出错，也要关闭SSE连接
                        }
                    } else {
                        log.warn("WebP解析结果中缺少delays或frames数组");
                        // 完成Future并传递异常
                        CompletableFuture<WebpProcessResult> future = pendingFutures.remove(webPTaskId);
                        if (future != null) {
                            future.completeExceptionally(new ServiceException("WebP解析结果中缺少delays或frames数组"));
                        }
                        progressService.updateProgress(oriTaskId, 100, "WebP解析完成但数据不完整", "错误", 0, 0, true);
                        
                        // 数据不完整时也要主动关闭SSE连接
                        log.debug("WebP解析结果数据不完整，主动关闭SSE连接: {}", webPTaskId);
                        sendCloseMessage(webPTaskId, "ERROR_OCCURRED");
                        return 3; // 数据不完整，关闭SSE连接
                    }
                } else {
                    log.warn("WebP结果事件中既没有webp字段也没有frameCount字段");
                    // 完成Future并传递异常
                    CompletableFuture<WebpProcessResult> future = pendingFutures.remove(webPTaskId);
                    if (future != null) {
                        future.completeExceptionally(new ServiceException("WebP处理完成但结果格式未知"));
                    }
                    progressService.updateProgress(oriTaskId, 100, "WebP处理完成但结果格式未知", "错误", 0, 0, true);
                    
                    // 结果格式未知时也要主动关闭SSE连接
                    log.debug("WebP结果格式未知，主动关闭SSE连接: {}", webPTaskId);
                    sendCloseMessage(webPTaskId, "ERROR_OCCURRED");
                    return 3; // 结果格式未知，关闭SSE连接
                }
            }
            // 处理WebP处理错误事件
            if ("webp_error".equals(eventName)) {
                log.error("收到WebP处理错误事件: 任务={}", webPTaskId);
                // 解析JSON数据
                JSONObject jsonData = new JSONObject(eventData);
                String errorMessage = jsonData.optString("message", "WebP处理失败");
                
                // 完成对应的CompletableFuture并传递异常
                CompletableFuture<WebpProcessResult> errorFuture = pendingFutures.remove(webPTaskId);
                if (errorFuture != null) {
                    errorFuture.completeExceptionally(new ServiceException("WebP处理失败: " + errorMessage));
                    log.debug("WebP错误已通过CompletableFuture传递");
                } else {
                    log.warn("未找到对应的CompletableFuture: {}", webPTaskId);
                }
                
                // 同时检查是否有动画创建的Future需要完成
                CompletableFuture<File> animationErrorFuture = pendingAnimationFutures.remove(webPTaskId);
                if (animationErrorFuture != null) {
                    animationErrorFuture.completeExceptionally(new ServiceException("WebP动画创建失败: " + errorMessage));
                    log.debug("WebP动画创建错误已通过CompletableFuture传递");
                }
                
                // 更新进度为100%错误状态
                progressService.updateProgress(oriTaskId, 100, errorMessage, "错误", 0, 0, true);
                
                log.error("WebP处理失败: {}", errorMessage);
                
                // WebP错误时也要主动关闭SSE连接
                log.debug("WebP处理错误，主动关闭SSE连接: {}", webPTaskId);
                sendCloseMessage(webPTaskId, "ERROR_OCCURRED");

                return 3; // 任务完成（失败），关闭连接
            }
            return 0;
        } catch (Exception e) {
            log.error("处理Flask SSE事件数据时发生错误: {}", e.getMessage());
            return 3;
        }
    }
    
    /**
     * 关闭指定任务的进度连接
     *
     * @param taskId 任务ID
     */
    public void closeProgressConnection(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return;
        }
        
        log.debug("正在关闭任务{}的SSE连接", taskId);
        
        // 取消OkHttp调用
        Call call = taskCalls.get(taskId);
        if (call != null) {
            log.debug("关闭任务{}的SSE连接", taskId);
            call.cancel();
            taskCalls.remove(taskId);
        } else {
            log.warn("任务{}没有活跃的OkHttp调用", taskId);
        }
        
        // 清除心跳计数
        if (heartbeatCounters.containsKey(taskId)) {
            log.debug("清除任务{}的心跳计数", taskId);
            heartbeatCounters.remove(taskId);
        }
        
        // 清除临时目录映射并删除临时目录
        Path tempDir = progressTempDirMap.remove(taskId);
        if (tempDir != null) {
            CharArtProcessor.deleteTempDirectory(tempDir);
        }
        
        // 清理未完成的Future
        CompletableFuture<WebpProcessResult> pendingFuture = pendingFutures.remove(taskId);
        if (pendingFuture != null && !pendingFuture.isDone()) {
            pendingFuture.completeExceptionally(new ServiceException("SSE连接已关闭"));
            log.debug("已清理未完成的WebP处理Future: {}", taskId);
        }
        
        CompletableFuture<File> pendingAnimationFuture = pendingAnimationFutures.remove(taskId);
        if (pendingAnimationFuture != null && !pendingAnimationFuture.isDone()) {
            pendingAnimationFuture.completeExceptionally(new ServiceException("SSE连接已关闭"));
            log.debug("已清理未完成的WebP动画Future: {}", taskId);
        }
        
        log.debug("任务{}的SSE连接已完全关闭", taskId);
    }
    
    /**
     * 发送关闭消息到Flask服务器
     *
     * @param taskId 任务ID
     */
    private void sendCloseMessage(String taskId) {
        sendCloseMessage(taskId, "TASK_COMPLETED");
    }
    
    /**
     * 发送带关闭原因的关闭消息到Flask服务器
     *
     * @param taskId 任务ID
     * @param closeReason 关闭原因
     */
    private void sendCloseMessage(String taskId, String closeReason) {
        try {
            // 根据关闭原因决定日志级别
            if ("ERROR_OCCURRED".equals(closeReason) || "HEARTBEAT_TIMEOUT".equals(closeReason)) {
                log.warn("发送关闭消息到Flask服务器: {}, 原因: {}", taskId, closeReason);
            } else {
                log.debug("发送关闭消息到Flask服务器: {}, 原因: {}", taskId, closeReason);
            }
            
            // 创建POST请求到Flask服务器的关闭端点，包含关闭原因参数
            HttpRequest request = HttpUtil.createPost(serviceBaseUrl + "/api/progress/close/" + taskId + "?closeReason=" + closeReason)
                    .timeout(maxTimeout);
            
            // 发送请求
            HttpResponse response = executeWithRetry(request);
            
            if (response.getStatus() == HttpStatus.HTTP_OK) {
                if ("ERROR_OCCURRED".equals(closeReason) || "HEARTBEAT_TIMEOUT".equals(closeReason)) {
                    log.warn("成功发送关闭消息到Flask服务器: {}, 原因: {}", taskId, closeReason);
                } else {
                    log.debug("成功发送关闭消息到Flask服务器: {}, 原因: {}", taskId, closeReason);
                }
            } else {
                log.warn("发送关闭消息到Flask服务器失败: {} {}", response.getStatus(), response.body());
            }
        } catch (Exception e) {
            // 只有在错误关闭时才记录错误日志
            if ("ERROR_OCCURRED".equals(closeReason)) {
                log.error("发送关闭消息到Flask服务器时发生错误: {}", e.getMessage());
            } else {
                log.debug("发送关闭消息到Flask服务器时发生异常: {}", e.getMessage());
            }
        } finally {
            if("ERROR_OCCURRED".equals(closeReason) || "HEARTBEAT_TIMEOUT".equals(closeReason)) {
                // 发送关闭事件到前端
                progressService.sendCloseEvent(taskId, CloseReason.parseCloseReason(closeReason));
            }
        }
    }

    /**
     * 异步处理WebP文件，支持进度跟踪，并将进度更新转发到ProgressService
     *
     * @param webpFile WebP文件
     * @param oriTaskId 前端SSE进度跟踪任务ID，如果为null则不进行进度跟踪
     * @return CompletableFuture包装的WebP处理结果
     * @throws ServiceException 如果处理过程中发生错误
     */
    public CompletableFuture<WebpProcessResult> processWebpAsync(File webpFile, String oriTaskId) throws ServiceException {
        CompletableFuture<WebpProcessResult> future = new CompletableFuture<>();
        
        try {
            log.debug("开始异步处理WebP文件: {}", webpFile.getAbsolutePath());

            String webPTaskId = createProgressTask();
            log.debug("创建WebP任务的任务ID进行进度跟踪: {}", webPTaskId);
            
            // 将Future存储到待完成映射中
            pendingFutures.put(webPTaskId, future);
            
            if (oriTaskId != null) {
                // 如果提供了进度服务，则启动SSE监听
                if (progressService != null) {
                    // 初始化进度信息
                    progressService.updateProgress(oriTaskId, 30, "准备解析WebP动图", "WebP解码", 0, 0, false);
                    // 创建连接锁
                    CountDownLatch connectionLatch = new CountDownLatch(1);

                    // 启动SSE监听
                     listenToProgressStream(oriTaskId, webPTaskId, connectionLatch);
                    // 关键修改：等待连接建立（最多30秒）
                    if (!connectionLatch.await(30, TimeUnit.SECONDS)) {
                        log.warn("SSE连接建立超时，继续进行处理但进度更新可能延迟");
                    } else {
                        // 连接建立后，使用CountDownLatch等待确保SSE流完全就绪
                        CountDownLatch stabilityLatch = new CountDownLatch(1);
                        long progressUpdateInterval = parallelConfig != null ? parallelConfig.getProgressUpdateInterval() : 500L;
                        CompletableFuture.delayedExecutor(progressUpdateInterval, TimeUnit.MILLISECONDS)
                                .execute(stabilityLatch::countDown);
                        try {
                            if (stabilityLatch.await(1, TimeUnit.SECONDS)) {
                                log.debug("SSE连接已建立并稳定，准备发送HTTP请求");
                            } else {
                                log.warn("等待SSE连接稳定超时");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("等待SSE连接稳定时被中断");
                        }
                    }
                }
            }
            
            // 创建表单并添加文件
            HttpRequest request = HttpUtil.createPost(serviceBaseUrl + "/api/process-webp")
                    .timeout(maxTimeout)
                    .form("image", webpFile);

            request.form("task_id", webPTaskId);
            
            // 发送请求并获取响应
            HttpResponse response = executeWithRetry(request);
            
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                log.error("WebP处理失败: {} {}", response.getStatus(), response.body());
                
                // 如果提供了进度服务，则更新失败状态
                if (oriTaskId != null) {
                    progressService.updateProgress(oriTaskId, 100, "WebP处理失败: " + response.getStatus(), "错误", 0, 0, true);
                }
                
                // 完成Future并抛出异常
                ServiceException exception = new ServiceException("WebP处理失败: " + response.getStatus() + " " + response.body());
                future.completeExceptionally(exception);
                pendingFutures.remove(webPTaskId);
                throw exception;
            }
            
            // 解析异步响应
            JSONObject asyncResponse = new JSONObject(response.body());
            String returnedTaskId = asyncResponse.getString("task_id");
            String message = asyncResponse.getString("message");
            String status = asyncResponse.getString("status");
            
            log.debug("WebP处理请求已提交: 任务ID={}, 消息={}, 状态={}", returnedTaskId, message, status);
            
            // 验证返回的任务ID是否与发送的一致
            if (!webPTaskId.equals(returnedTaskId)) {
                log.warn("返回的任务ID({})与发送出的任务ID({})不一致", returnedTaskId, webPTaskId);
            }
            
            return future;
            
        } catch (Exception e) {
            // 如果提供了进度服务，则更新错误状态
            if (progressService != null && oriTaskId != null) {
                progressService.updateProgress(oriTaskId, 100, "处理WebP文件时发生错误: " + e.getMessage(), "错误", 0, 0, true);
            }
            
            future.completeExceptionally(e);
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            log.error("处理WebP文件时发生错误", e);
            throw new ServiceException("处理WebP文件时发生错误: " + e.getMessage(), e);
        }
    }
    
    /**
     * 存储任务ID与临时目录的映射关系
     *
     * @param taskId  任务ID
     * @param tempDir 临时目录路径
     */
    public void storeTempDirectory(String taskId, Path tempDir) {
        if (taskId != null && tempDir != null) {
            progressTempDirMap.put(taskId, tempDir);
            log.debug("存储任务{}的临时目录: {}", taskId, tempDir);
        }
    }
    
    /**
     * 处理WebP文件，支持进度跟踪，并将进度更新转发到ProgressService（同步版本，保持向后兼容）
     *
     * @param webpFile WebP文件
     * @param oriTaskId 前端SSE进度跟踪任务ID，如果为null则不进行进度跟踪
     * @return WebP处理结果
     * @throws ServiceException 如果处理过程中发生错误
     */
    public WebpProcessResult processWebp(File webpFile, String oriTaskId) throws ServiceException {
        try {
            // 调用异步版本并等待结果
            CompletableFuture<WebpProcessResult> future = processWebpAsync(webpFile, oriTaskId);
            return future.get(); // 阻塞等待结果
        } catch (Exception e) {
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            log.error("处理WebP文件时发生错误", e);
            throw new ServiceException("处理WebP文件时发生错误: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析服务响应
     *
     * @param responseBody 响应体
     * @param oriTaskId    前端SSE任务ID
     * @return WebP处理结果
     * @throws ServiceException 如果解析过程中发生错误
     */
    private WebpProcessResult parseResponse(String responseBody,String oriTaskId) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            int frameCount = jsonResponse.getInt("frameCount");
            JSONArray delaysArray = jsonResponse.getJSONArray("delays");
            JSONArray framesArray = jsonResponse.getJSONArray("frames");
            
            log.debug("WebP处理成功，共{}帧", frameCount);
            
            // 提取延迟数组
            int[] delays = new int[frameCount];
            for (int i = 0; i < frameCount; i++) {
                delays[i] = delaysArray.getInt(i);
            }
            
            // 提取帧路径并加载图像
            BufferedImage[] frames = new BufferedImage[frameCount];
            
            // 更新进度信息
            if (oriTaskId != null && progressService != null) {
                progressService.updateProgress(oriTaskId, 38, "开始获取帧图像", "图像获取", 0, frameCount, false);
            }
            
            for (int i = 0; i < frameCount; i++) {
                // 更新获取图片阶段的进度
                if (oriTaskId != null && progressService != null) {
                    double progress = 38 + ((i+1)  / (double)frameCount * (40-38));
                    progressService.updateProgress(oriTaskId, progress, String.format("正在获取第 %d/%d 帧图像", i+1, frameCount), "图像获取", i+1, frameCount, false);
                }
                
                String framePath = framesArray.getString(i);
                // 构建图像URL
                String frameUrl = serviceBaseUrl + "/api/get-image/" + new File(framePath).getPath();
                // 使用HttpUtil获取图像
                HttpResponse imageResponse = executeWithRetry(HttpUtil.createGet(frameUrl).timeout(maxTimeout));
                if (imageResponse.getStatus() != HttpStatus.HTTP_OK) {
                    throw new ServiceException("获取帧图像失败: " + imageResponse.getStatus());
                }
                // 将响应体转换为BufferedImage
                byte[] imageBytes = imageResponse.bodyBytes();
                frames[i] = ImageIO.read(new ByteArrayInputStream(imageBytes));
                
                log.debug("已获取第 {}/{} 帧图像", i+1, frameCount);
            }
            
            // 更新最终进度
            if (oriTaskId != null && progressService != null) {
                progressService.updateProgress(oriTaskId, 40, "所有帧图像获取完成", "图像获取", frameCount, frameCount, false);
            }
            
            // 检查响应中是否包含任务ID
            if (jsonResponse.has("task_id")) {
                String webPTaskId = jsonResponse.getString("task_id");
                log.debug("WebP处理任务ID: {}", webPTaskId);
                return new WebpProcessResult(frameCount, delays, frames, oriTaskId, webPTaskId);
            }
            
            return new WebpProcessResult(frameCount, delays, frames);
        } catch (Exception e) {
            log.error("解析WebP处理服务响应失败", e);
            throw new ServiceException("解析WebP处理服务响应失败: " + e.getMessage(), e);
        }
    }


    /**
     * 从临时文件异步创建WebP动画
     * <p>
     * 将多个图像文件和对应的延迟时间转换为WebP动画。
     * 支持进度跟踪，可以通过任务ID监控处理进度。
     * 此方法从临时文件读取图像帧，而不是直接使用内存中的BufferedImage对象，
     * 有助于减少内存使用。
     * </p>
     *
     * @param framePaths 帧文件路径数组
     * @param delays 延迟数组（毫秒）
     * @param oriTaskId 前端SSE进度跟踪任务ID，如果为null则不进行进度跟踪
     * @return CompletableFuture包装的临时WebP文件
     */
    public CompletableFuture<File> createWebpAnimationFromFilesAsync(Path[] framePaths, int[] delays, String oriTaskId) {
        CompletableFuture<File> future = new CompletableFuture<>();
        
        try {
            log.debug("开始异步从文件创建WebP动画，共{}帧", framePaths.length);
            String webPTaskId = createProgressTask();
            log.debug("创建WebP任务ID进行进度跟踪: {}", webPTaskId);
            
            // 将Future存储到待完成映射中
            pendingAnimationFutures.put(webPTaskId, future);
            
            if (oriTaskId != null) {
                // 如果提供了进度服务，则启动SSE监听
                if (progressService != null) {
                    // 初始化进度信息
                    progressService.updateProgress(oriTaskId, 90, "准备创建WebP动图", "WebP编码", 0, 0, false);
                    // 创建连接锁
                    CountDownLatch connectionLatch = new CountDownLatch(1);
                    // 启动SSE监听
                    listenToProgressStream(oriTaskId, webPTaskId, connectionLatch);
                    // 关键修改：等待连接建立（最多30秒）
                    if (!connectionLatch.await(30, TimeUnit.SECONDS)) {
                        log.warn("SSE连接建立超时，继续处理但进度可能延迟");
                    } else {
                        // 连接建立后，使用CountDownLatch等待确保SSE流完全就绪
                        CountDownLatch stabilityLatch = new CountDownLatch(1);
                        long progressUpdateInterval2 = parallelConfig != null ? parallelConfig.getProgressUpdateInterval() : 500L;
                        CompletableFuture.delayedExecutor(progressUpdateInterval2, TimeUnit.MILLISECONDS)
                                .execute(stabilityLatch::countDown);
                        try {
                            if (stabilityLatch.await(1, TimeUnit.SECONDS)) {
                                log.debug("SSE连接已建立起并稳定，准备发送HTTP请求");
                            } else {
                                log.warn("等待SSE连接稳定时超时");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("等待SSE连接稳定的时候被中断");
                        }
                    }
                }
            }
            
            // 更新进度
            if (oriTaskId != null && progressService != null) {
                int progress = 91;
                progressService.updateProgress(oriTaskId, progress, "准备发送帧文件路径", "帧处理", 0, framePaths.length, false);
            }
            
            // 创建请求
            HttpRequest request = HttpUtil.createPost(serviceBaseUrl + "/api/create-webp-animation")
                    .timeout(maxTimeout);
            
            // 添加延迟信息
            JSONArray delaysArray = new JSONArray();
            for (int delay : delays) {
                delaysArray.put(delay);
            }
            request.form("delays", delaysArray.toString());
            JSONArray frameFormatArray = new JSONArray();
            
            // 如果提供了任务ID，则添加到请求中
            request.form("task_id", webPTaskId);
            
            // 添加帧文件路径数组
            JSONArray framePathsArray = new JSONArray();
            for (Path framePath : framePaths) {
                String extName = FileNameUtil.extName(framePath.toFile()).isEmpty() ? ".png" : "." + FileNameUtil.extName(framePath.toFile());
                frameFormatArray.put(extName);
                // 获取配置的临时目录路径
                String tempDir = tempDirectoryConfig.getTempDirectory();
                Path tempDirPath = Path.of(tempDir);
                
                // 将绝对路径转换为相对于临时目录的路径
                Path relativePath = tempDirPath.relativize(framePath);
                framePathsArray.put(relativePath.toString());
            }
            request.form("frame_format", frameFormatArray.toString());
            request.form("frame_paths", framePathsArray.toString());
            
            log.debug("已向Webp处理器发送创建WebP动画请求。任务ID {}", webPTaskId);
            HttpResponse response = executeWithRetry(request);
            
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                log.error("创建WebP动画失败: {} {}", response.getStatus(), response.body());
                pendingAnimationFutures.remove(webPTaskId);
                future.completeExceptionally(new ServiceException("创建WebP动画失败: " + response.getStatus() + " " + response.body()));
                return future;
            }
            
            // 解析异步响应
            JSONObject asyncResponse = new JSONObject(response.body());
            String returnedTaskId = asyncResponse.getString("task_id");
            String message = asyncResponse.getString("message");
            String status = asyncResponse.getString("status");
            
            log.debug("WebP动画创建请求已提交: 任务ID={}, 消息={}, 状态={}", returnedTaskId, message, status);
            
            // 验证返回的任务ID是否与发送的一致
            if (!webPTaskId.equals(returnedTaskId)) {
                log.warn("返回的任务ID({})与发送的任务ID({})不一致", returnedTaskId, webPTaskId);
            }
            
            log.debug("WebP动画创建请求已提交，等待异步处理完成");
            
        } catch (Exception e) {
            log.error("异步创建WebP动画时发生错误", e);
            future.completeExceptionally(new ServiceException("异步创建WebP动画时发生错误: " + e.getMessage(), e));
        }
        
        return future;
    }
    
    /**
     * 从临时文件创建WebP动画
     * <p>
     * 将多个图像文件和对应的延迟时间转换为WebP动画。
     * 支持进度跟踪，可以通过任务ID监控处理进度。
     * 此方法从临时文件读取图像帧，而不是直接使用内存中的BufferedImage对象，
     * 有助于减少内存使用。
     * </p>
     *
     * @param framePaths 帧文件路径数组
     * @param delays 延迟数组（毫秒）
     * @param oriTaskId 前端SSE进度跟踪任务ID，如果为null则不进行进度跟踪
     * @return 临时WebP文件
     * @throws ServiceException 如果创建过程中发生错误
     */
    public File createWebpAnimationFromFiles(Path[] framePaths, int[] delays, String oriTaskId) {
        try {
            return createWebpAnimationFromFilesAsync(framePaths, delays, oriTaskId).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("WebP动画创建被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ServiceException) {
                throw (ServiceException) cause;
            } else {
                throw new ServiceException("WebP动画创建失败: " + cause.getMessage(), cause);
            }
        }
    }

}