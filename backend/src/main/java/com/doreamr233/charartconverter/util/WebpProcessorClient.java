package com.doreamr233.charartconverter.util;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.http.*;
import com.doreamr233.charartconverter.exception.ServiceException;
import com.doreamr233.charartconverter.model.WebpProcessResult;
import com.doreamr233.charartconverter.service.ProgressService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;

import org.apache.commons.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
     * 存储任务ID与对应的SSE连接Call对象
     */
    private final Map<String, Call> taskCalls = new ConcurrentHashMap<>();

    /**
     * 存储每个任务的心跳计数
     */
    private final Map<String, Integer> heartbeatCounters = new ConcurrentHashMap<>();

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
                log.info("WebP处理服务第{}次尝试，共{}次", i+1,maxRetriesCount+1);
                // 执行 HTTP 请求
                if (i > 0){
                    Thread.sleep(500L * i);
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
     * 创建一个新的进度任务
     *
     * @return 任务ID，如果创建失败则返回null
     */
    public String createProgressTask() {
        try {
            log.info("创建WebP处理进度任务");
            
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
            log.info("成功创建进度任务，ID: {}", taskId);
            
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
            return;
        }
        
        String streamUrl = getProgressStreamUrl(webPTaskId);
        log.info("开始监听Flask SSE进度流: {}", streamUrl);
        
        // 在新线程中执行，避免阻塞主线程
        new Thread(() -> {
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
            
            log.info("SSE请求头: Accept={}, Cache-Control={}, Connection={}", 
                    request.header("Accept"), 
                    request.header("Cache-Control"), 
                    request.header("Connection"));
            
            // 使用OkHttp执行请求
            log.info("正在建立Flask SSE连接: {}", streamUrl);
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
                log.info("成功建立Flask SSE连接: URL={}, 状态码={}, Content-Type={}", 
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
                        log.info("收到SSE行: {}", line);
                        
                        // 空行表示事件结束
                        if (line.isEmpty()) {
                            if (eventData.length() > 0) {
                                log.info("处理SSE事件: 名称={}, 数据长度={}", eventName, eventData.length());
                                // 处理事件数据
                                boolean shouldClose = processEventData(eventName, eventData.toString(), oriTaskId,webPTaskId);
                                eventData = new StringBuilder();
                                eventName = null;
                                
                                // 如果处理结果表明应该关闭连接，则退出循环
                                if (shouldClose) {
                                    log.info("任务{}已完成，关闭SSE连接", webPTaskId);
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
                    
                    log.info("SSE连接已关闭: {}", streamUrl);
                }
            } catch (Exception e) {
                if (connectionLatch != null) {
                    connectionLatch.countDown(); // 异常时也要释放锁
                }
                log.error("监听Flask SSE进度流时发生错误", e);
            } finally {
                if (connectionLatch != null) {
                    connectionLatch.countDown(); // 异常时也要释放锁
                }
                // 从Map中移除Call对象
                taskCalls.remove(webPTaskId);
                // 清除心跳计数
                heartbeatCounters.remove(webPTaskId);
            }
        }).start();
    }
    
    /**
     * 处理SSE事件数据
     *
     * @param eventName 事件名称
     * @param eventData 事件数据
     * @param oriTaskId 前端SSE进度任务ID
     * @param webPTaskId webP处理器SSE进度任务ID
     * @return 如果任务已完成，返回true，表示应该关闭连接
     */
    
    private boolean processEventData(String eventName, String eventData, String oriTaskId, String webPTaskId) {
        try {
            log.info("处理SSE事件: 名称={}, 数据={}，任务编号={}", eventName, eventData,webPTaskId);
            // 处理心跳事件
            if ("heartbeat".equals(eventName)) {
                log.info("收到Flask心跳事件");
                // 增加心跳计数
                int count = heartbeatCounters.getOrDefault(webPTaskId, 0) + 1;
                heartbeatCounters.put(webPTaskId, count);
                log.info("任务{}的心跳计数: {}", webPTaskId, count);
                
                // 如果心跳计数超过12次，发送close消息并关闭SSE连接
                if (count >= 12) {
                    log.info("任务{}的心跳计数达到{}次，准备关闭SSE连接", webPTaskId, count);
                    // 发送close消息
                    sendCloseMessage(webPTaskId);
                    // 关闭SSE连接
                    closeProgressConnection(webPTaskId);
                    // 清除心跳计数
                    heartbeatCounters.remove(webPTaskId);
                    return true;
                }
                return false;
            }
            // 处理close消息
            if ("close".equals(eventName)) {
                log.info("收到Flask关闭事件，关闭SSE连接");
                // 关闭SSE连接
                closeProgressConnection(webPTaskId);
                // 清除心跳计数
                heartbeatCounters.remove(webPTaskId);
                return true;
            }
            if("webp".equals(eventName)){
                // 解析JSON数据
                JSONObject jsonData = new JSONObject(eventData);

                // 提取进度信息字段 - 根据Flask服务器发送的字段名进行匹配
                int percentage = jsonData.optInt("progress", 0);
                String message = jsonData.optString("message", "处理中");
                String stage = jsonData.optString("status", "处理中");
                int currentPixel = jsonData.optInt("current_step", 0);
                int totalPixels = jsonData.optInt("total_steps", 0);
                boolean isDone = jsonData.optBoolean("is_done", false);

                log.info("收到Flask进度更新: 任务={}, 进度={}%, 消息={}, 状态={}",
                        webPTaskId, percentage, message, stage);

                // 使用ProgressService更新进度
                // 此处isDone用于判断Flask端任务是否执行完毕
                progressService.updateProgress(oriTaskId, percentage, message, stage, currentPixel, totalPixels,false);

                // 如果进度达到100%，结束监听
                if (isDone) {
                    log.info("任务{}已完成，进度监听结束", webPTaskId);
                    // 关闭SSE连接
                    closeProgressConnection(webPTaskId);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("处理Flask SSE事件数据时发生错误: {}", e.getMessage());
            return false;
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
        
        Call call = taskCalls.get(taskId);
        if (call != null) {
            log.info("关闭任务{}的SSE连接", taskId);
            call.cancel();
            taskCalls.remove(taskId);
        }
    }
    
    /**
     * 发送关闭消息到Flask服务器
     *
     * @param taskId 任务ID
     */
    private void sendCloseMessage(String taskId) {
        try {
            log.info("发送关闭消息到Flask服务器: {}", taskId);
            
            // 创建POST请求到Flask服务器的关闭端点
            HttpRequest request = HttpUtil.createPost(serviceBaseUrl + "/api/progress/close/" + taskId)
                    .timeout(maxTimeout);
            
            // 发送请求
            HttpResponse response = executeWithRetry(request);
            
            if (response.getStatus() == HttpStatus.HTTP_OK) {
                log.info("成功发送关闭消息到Flask服务器: {}", taskId);
            } else {
                log.warn("发送关闭消息到Flask服务器失败: {} {}", response.getStatus(), response.body());
            }
        } catch (Exception e) {
            log.error("发送关闭消息到Flask服务器时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 处理WebP文件，支持进度跟踪，并将进度更新转发到ProgressService
     *
     * @param webpFile WebP文件
     * @param oriTaskId 前端SSE进度跟踪任务ID，如果为null则不进行进度跟踪
     * @return WebP处理结果
     * @throws ServiceException 如果处理过程中发生错误
     */
    public WebpProcessResult processWebp(File webpFile, String oriTaskId) throws ServiceException {
        try {
            log.info("开始处理WebP文件: {}", webpFile.getAbsolutePath());

            String webPTaskId = createProgressTask();
            log.info("创建WebP任务ID进行进度跟踪: {}", webPTaskId);
            if (oriTaskId != null) {
                // 如果提供了进度服务，则启动SSE监听
                if (progressService != null) {
                    // 初始化进度信息
                    progressService.updateProgress(oriTaskId, 30, "准备解析WebP动图", "WebP解码", 0, 0,false);
                    // 创建连接锁
                    CountDownLatch connectionLatch = new CountDownLatch(1);

                    // 启动SSE监听
                    listenToProgressStream(oriTaskId,webPTaskId,connectionLatch);
                    // 关键修改：等待连接建立（最多30秒）
                    if (!connectionLatch.await(30, TimeUnit.SECONDS)) {
                        log.warn("SSE连接建立超时，继续处理但进度可能延迟");
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
                    progressService.updateProgress(oriTaskId, 100, "WebP处理失败: " + response.getStatus(), "错误", 0, 0,true);
                }
                
                throw new ServiceException("WebP处理失败: " + response.getStatus() + " " + response.body());
            }
            
            // 解析响应
            WebpProcessResult result = parseResponse(response.body(),oriTaskId,webPTaskId);
            
            // 如果提供了任务ID，则设置到结果中
            if (oriTaskId != null) {
                result.setOriTaskId(oriTaskId);
            }
            result.setWebPTaskId(webPTaskId);
            return result;
        } catch (Exception e) {
            // 如果提供了进度服务，则更新错误状态
            if (progressService != null && oriTaskId != null) {
                progressService.updateProgress(oriTaskId, 100, "处理WebP文件时发生错误: " + e.getMessage(), "错误", 0, 0,true);
            }
            
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
     * @param oriTaskId 前端SSE任务ID
     * @param webPTaskId webP处理器SSE任务ID
     * @return WebP处理结果
     * @throws ServiceException 如果解析过程中发生错误
     */
    private WebpProcessResult parseResponse(String responseBody,String oriTaskId,String webPTaskId) {
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
                
                log.info("已获取第 {}/{} 帧图像", i+1, frameCount);
            }
            
            // 更新最终进度
            if (oriTaskId != null && progressService != null) {
                progressService.updateProgress(oriTaskId, 40, "所有帧图像获取完成", "图像获取", frameCount, frameCount, false);
            }
            
            // 检查响应中是否包含任务ID
            if (jsonResponse.has("task_id")) {
                webPTaskId = jsonResponse.getString("task_id");
                log.info("WebP处理任务ID: {}", webPTaskId);
                return new WebpProcessResult(frameCount, delays, frames, oriTaskId,webPTaskId);
            }
            
            return new WebpProcessResult(frameCount, delays, frames);
        } catch (Exception e) {
            log.error("解析WebP处理服务响应失败", e);
            throw new ServiceException("解析WebP处理服务响应失败: " + e.getMessage(), e);
        }
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
            log.info("开始从文件创建WebP动画，共{}帧", framePaths.length);
            String webPTaskId = createProgressTask();
            log.info("创建WebP任务ID进行进度跟踪: {}", webPTaskId);
            if (oriTaskId != null) {
                // 如果提供了进度服务，则启动SSE监听
                if (progressService != null) {
                    // 初始化进度信息
                    progressService.updateProgress(oriTaskId, 90, "准备创建WebP动图", "WebP编码", 0, 0,false);
                    // 创建连接锁
                    CountDownLatch connectionLatch = new CountDownLatch(1);
                    // 启动SSE监听
                    listenToProgressStream(oriTaskId,webPTaskId,connectionLatch);
                    // 关键修改：等待连接建立（最多30秒）
                    if (!connectionLatch.await(30, TimeUnit.SECONDS)) {
                        log.warn("SSE连接建立超时，继续处理但进度可能延迟");
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
                String extName = FileNameUtil.extName(framePath.toFile()).isEmpty() ? ".png" : "."+FileNameUtil.extName(framePath.toFile());
                frameFormatArray.put(extName);
                // 获取系统临时目录路径
                String tempDir = System.getProperty("java.io.tmpdir");
                Path tempDirPath = Path.of(tempDir);
                
                // 将绝对路径转换为相对于临时目录的路径
                Path relativePath = tempDirPath.relativize(framePath);
                framePathsArray.put(relativePath.toString());
            }
            request.form("frame_format", frameFormatArray.toString());
            request.form("frame_paths", framePathsArray.toString());
            
            log.info("已向Webp处理器发送创建WebP动画请求。任务ID {}", webPTaskId);
            HttpResponse response = executeWithRetry(request);
            
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                log.error("创建WebP动画失败: {} {}", response.getStatus(), response.body());
                throw new ServiceException("创建WebP动画失败: " + response.getStatus() + " " + response.body());
            }
            
            // 解析响应
            JSONObject jsonResponse = new JSONObject(response.body());
            String webpPath = jsonResponse.getString("webp");
            
            // 更新进度信息
            if (oriTaskId != null && progressService != null) {
                progressService.updateProgress(oriTaskId, 98, "开始获取WebP动画文件", "文件获取", 0, 1, false);
            }
            
            // 构建WebP文件URL
            String webpUrl = serviceBaseUrl + "/api/get-image/" + new File(webpPath).getPath();
            
            // 使用HttpUtil获取WebP文件
            HttpResponse webpResponse = executeWithRetry(HttpUtil.createGet(webpUrl).timeout(maxTimeout));
            if (webpResponse.getStatus() != HttpStatus.HTTP_OK) {
                throw new ServiceException("获取WebP文件失败: " + webpResponse.getStatus());
            }
            
            // 将响应体保存为临时文件
            byte[] webpData = webpResponse.bodyBytes();
            File tempFile = File.createTempFile("animation", ".webp");
            FileUtils.writeByteArrayToFile(tempFile, webpData);
            
            // 更新最终进度
            if (oriTaskId != null && progressService != null) {
                progressService.updateProgress(oriTaskId, 99, "WebP动画文件获取完成", "文件获取", 1, 1, false);
            }
            
            log.info("WebP动画创建成功，保存到临时文件: {}", tempFile.getAbsolutePath());
            return tempFile;
        } catch (Exception e) {
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            log.error("从文件创建WebP动画时发生错误", e);
            throw new ServiceException("从文件创建WebP动画时发生错误: " + e.getMessage(), e);
        }
    }
}