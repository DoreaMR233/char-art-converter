# 字符画转换器后端服务

字符画转换器后端服务是一个基于Spring Boot的Web应用程序，提供将图片转换为字符画的功能。支持静态图片（JPG、PNG等）和动态图片（GIF、WebP）的处理，可以生成字符文本和字符画图像。

## 目录

- [字符画转换器后端服务](#字符画转换器后端服务)
  - [目录](#目录)
  - [项目结构](#项目结构)
  - [技术栈](#技术栈)
  - [API接口](#api接口)
    - [图片转换接口](#图片转换接口)
    - [获取字符文本接口](#获取字符文本接口)
    - [进度监控接口](#进度监控接口)
    - [关闭进度连接接口](#关闭进度连接接口)
    - [WebP进度流URL接口](#webp进度流url接口)
    - [获取临时图片接口](#获取临时图片接口)
    - [健康检查接口](#健康检查接口)
  - [使用说明](#使用说明)
    - [本地部署说明](#本地部署说明)
      - [环境要求](#环境要求)
      - [安装步骤](#安装步骤)
      - [注意事项](#注意事项)
    - [Docker部署说明](#docker部署说明)
    - [配置文件说明](#配置文件说明)
      - [服务器配置](#服务器配置)
      - [文件上传配置](#文件上传配置)
      - [日志配置](#日志配置)
      - [时区配置](#时区配置)
      - [字符画转换配置](#字符画转换配置)
      - [临时文件配置](#临时文件配置)
      - [Redis配置](#redis配置)
      - [字符画缓存配置](#字符画缓存配置)
      - [WebP处理服务配置](#webp处理服务配置)
  - [许可证](#许可证)

## 项目结构

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/doreamr233/charartconverter/
│   │   │       ├── config/                # 配置类
│   │   │       │   ├── RedisConfig.java   # Redis配置
│   │   │       │   └── WebConfig.java     # Web配置
│   │   │       ├── controller/            # 控制器
│   │   │       │   └── CharArtController.java  # API接口控制器
│   │   │       ├── exception/             # 异常处理
│   │   │       │   ├── BusinessException.java
│   │   │       │   ├── FileTypeException.java
│   │   │       │   ├── GlobalExceptionHandler.java
│   │   │       │   └── ServiceException.java
│   │   │       ├── model/                 # 数据模型
│   │   │       │   ├── ApiError.java      # API错误响应模型
│   │   │       │   └── ProgressInfo.java  # 进度信息模型
│   │   │       ├── service/               # 服务层
│   │   │       │   ├── CharArtService.java     # 字符画服务接口
│   │   │       │   ├── ProgressService.java    # 进度服务接口
│   │   │       │   └── impl/                   # 服务实现
│   │   │       │       ├── CharArtServiceImpl.java
│   │   │       │       └── ProgressServiceImpl.java
│   │   │       ├── util/                  # 工具类
│   │   │       │   ├── CharArtProcessor.java  # 字符画处理核心
│   │   │       │   └── WebpProcessorClient.java  # WebP处理客户端
│   │   │       └── CharArtConverterApplication.java  # 应用入口
│   │   └── resources/
│   │       └── application.properties  # 应用配置文件
│   └── test/                        # 测试代码
├── docker-compose.yml               # Docker Compose配置
├── docker-entrypoint.sh            # Docker启动脚本
├── Dockerfile                       # Docker镜像构建文件
└── pom.xml                          # Maven配置文件
```

## 技术栈

- **核心框架**: Spring Boot 2.7.0
- **Web框架**: Spring MVC
- **缓存**: Redis
- **图像处理**:
  - Thumbnailator 0.4.17 (图像缩放)
  - WebP-ImageIO 0.1.6 (WebP格式支持)
  - Animated-GIF-Lib 1.4 (GIF处理)
- **工具库**:
  - Lombok (简化代码)
  - Hutool 5.8.38 (工具集)
  - JSON 20230227 (JSON处理)
- **实时通信**: Server-Sent Events (SSE)
- **容器化**: Docker

## API接口

### 图片转换接口

将上传的图片转换为字符画图片。支持静态图片（JPG、PNG）和动态图片（GIF、WebP）的转换。

- **URL**: `/api/convert`
- **方法**: POST
- **Content-Type**: multipart/form-data
- **参数**:
  - `image`: (必需) 要转换的图片文件，支持JPG、JPEG、PNG、GIF、WebP格式
  - `density`: (可选) 字符密度，可选值为"low"、"medium"、"high"，默认为"medium"
  - `colorMode`: (可选) 颜色模式，可选值为"color"、"colorBackground"、"grayscale"，默认为"grayscale"
  - `limitSize`: (可选) 是否限制字符画的最大尺寸，默认为true
  - `progressId`: (可选) 进度ID，用于跟踪转换进度，如果不提供则自动生成
- **响应**: JSON格式，包含进度ID和任务状态

  ```json
  {
    "progressId": "1234567890",
    "status": "processing",
    "message": "转换任务已启动，请通过SSE监听进度"
  }
  ```

### 获取字符文本接口

获取已转换图片的字符画文本。

- **URL**: `/api/text`
- **方法**: GET
- **参数**:
  - `filename`: (必需) 原始文件名，用于在缓存中查找对应的字符画文本
- **响应**: JSON格式，包含查找结果和字符画文本

  ```json
  {
    "find": true,
    "text": "字符画文本内容..."
  }
  ```

### 进度监控接口

使用SSE技术获取转换进度的实时更新。

- **URL**: `/api/progress/{id}`
- **方法**: GET
- **参数**:
  - `id`: (必需) 进度ID，用于标识要跟踪的特定转换任务
- **响应**: SSE事件流，包含进度更新
  - 事件类型: `progress` - 进度更新事件，包含当前进度百分比、消息等信息
  - 事件类型: `convertResult` - 转换结果事件，包含文件路径和内容类型
  - 事件类型: `heartbeat` - 保持连接活跃的心跳事件
  - 事件类型: `close` - 连接关闭事件，包含关闭原因

  **事件数据示例**:
  
  进度更新事件:
  ```json
  {
    "progressId": "1234567890",
    "progress": 50,
    "message": "正在处理...",
    "status": "处理中",
    "currentFrame": 5,
    "totalFrames": 10,
    "isError": false
  }
  ```
  
  转换结果事件:
  ```json
  {
    "filePath": "result_1234567890.png",
    "contentType": "image/png"
  }
  ```

### 关闭进度连接接口

主动关闭指定ID的进度连接。

- **URL**: `/api/progress/close/{id}`
- **方法**: POST
- **参数**:
  - `id`: (必需) 进度ID，用于标识要关闭的特定连接
  - `closeReason`: (可选) 关闭原因，可选值为"TASK_COMPLETED"、"ERROR_OCCURRED"、"HEARTBEAT_TIMEOUT"等
- **响应**: JSON格式，包含操作结果

  ```json
  {
    "success": true,
    "message": "进度连接已关闭"
  }
  ```

### WebP进度流URL接口

获取WebP处理器的进度流URL。

- **URL**: `/api/webp-progress-url/{taskId}`
- **方法**: GET
- **参数**:
  - `taskId`: (必需) 任务ID，用于标识要跟踪的特定WebP处理任务
- **响应**: JSON格式，包含进度流URL

  ```json
  {
    "success": true,
    "url": "http://localhost:8081/progress/stream/task123"
  }
  ```

### 获取临时图片接口

从临时文件夹获取图片数据，主要供WebP处理器调用。

- **URL**: `/api/get-temp-image/{filePath}`
- **方法**: GET
- **参数**:
  - `filePath`: (必需) 临时文件的路径（相对于系统临时目录的路径）
- **响应**: 图片二进制数据，响应完成后自动删除临时文件

### 健康检查接口

提供服务健康状态的检查，包括主服务和WebP处理器的状态。

- **URL**: `/api/health`
- **方法**: GET
- **响应**: JSON格式，包含服务状态信息

  **WebP处理器启用时**:
  ```json
  {
    "status": "UP",
    "webpProcessor": "UP",
    "message": "字符画转换服务正常，Webp处理服务正常"
  }
  ```
  
  **WebP处理器未启用时**:
  ```json
  {
    "status": "UP",
    "webpProcessor": "OFF",
    "message": "字符画转换服务正常运行，Webp处理服务未开启"
  }
  ```
  
  **WebP处理器异常时**:
  ```json
  {
    "status": "UP",
    "webpProcessor": "DOWN",
    "message": "字符画转换服务正常，Webp处理服务异常"
  }
  ```

## 使用说明

### 本地部署说明

#### 环境要求

- JDK 11+
- Maven 3.6+
- Redis 5.0+
- Python WebP处理服务（用于处理WebP动图）

#### 安装步骤

1. **克隆项目到本地**

   ```bash
   git clone https://github.com/yourusername/char-art-converter.git
   cd char-art-converter/backend
   ```

2. **配置Redis服务**

   确保Redis服务已启动并可访问，默认配置为：
   - 主机：localhost
   - 端口：6379
   - 数据库：0

3. **编译项目**

   ```bash
   mvn clean package
   ```

4. **运行项目**

   使用JAR包运行：

   ```bash
   java -jar target/char-art-converter-1.0.0.jar
   ```

   或者使用Maven直接运行：

   ```bash
   mvn spring-boot:run
   ```

5. **访问服务**

   服务启动后，可以通过以下URL访问：
   - API接口: <http://localhost:8080/api>
   - 健康检查: <http://localhost:8080/api/health>

#### 注意事项

- **Redis依赖**：服务依赖Redis进行缓存，请确保Redis服务已启动并可访问
- **WebP处理服务**：处理WebP动图需要Python WebP处理服务，请确保该服务已启动并配置正确的URL
- **内存配置**：处理大图片或高分辨率GIF可能需要较大的内存，可以通过JVM参数调整内存大小：

  ```bash
  java -Xmx1g -jar target/char-art-converter-1.0.0.jar
  ```

- **临时文件清理**：服务会创建临时文件进行处理，正常情况下会自动清理，但如果服务异常终止，可能需要手动清理临时目录

### Docker部署说明

如需了解如何使用Docker部署本服务，请参阅 [Docker部署指南](./Docker.md)。

Docker部署提供了更简便的部署方式，包含了所有必要的依赖服务（Redis、WebP处理服务等），无需手动配置环境。

### 配置文件说明

本项目使用 `application.properties.template` 作为配置模板文件。

#### 首次使用步骤

1. 复制模板文件：
   ```bash
   cp application.properties.template application.properties
   ```

2. 根据你的环境修改 `application.properties` 中的配置项

#### 重要说明

- `application.properties` 文件已被添加到 `.gitignore` 中，不会被提交到版本控制
- 请不要直接修改 `application.properties.template` 文件，除非需要更新默认配置
- 如果需要添加新的配置项，请同时更新模板文件

#### 配置参数说明

| 变量名称 | 变量中文名 | 变量作用 | 变量默认值 |
|---------|-----------|----------|----------|
| server.port | 服务器端口 | 设置应用服务器监听端口 | 8080 |
| spring.servlet.multipart.max-file-size | 单个文件最大大小 | 限制上传单个文件的最大大小 | 10MB |
| spring.servlet.multipart.max-request-size | 请求最大大小 | 限制整个请求的最大大小 | 10MB |
| spring.servlet.multipart.location | 文件上传临时目录 | 指定multipart文件的临时存储位置 | /app/temp |
| logging.level.com.doreamr233.charartconverter | 应用日志级别 | 设置应用程序的日志输出级别 | DEBUG |
| logging.file.name | 日志文件路径 | 指定日志文件的存储路径 | /app/logs/char-art-converter.log |
| logging.logback.rollingpolicy.max-file-size | 日志文件最大大小 | 单个日志文件的最大大小 | 10MB |
| logging.logback.rollingpolicy.max-history | 日志文件保留天数 | 日志文件的最大保留数量 | 30 |
| logging.pattern.dateformat | 日志时间格式 | 日志中时间戳的格式 | yyyy-MM-dd HH:mm:ss.SSS |
| logging.charset.console | 控制台日志编码 | 控制台输出日志的字符编码 | UTF-8 |
| logging.charset.file | 文件日志编码 | 日志文件的字符编码 | UTF-8 |
| spring.jackson.time-zone | Jackson时区设置 | JSON序列化时的时区设置 | Asia/Shanghai |
| spring.mvc.format.date-time | 日期时间格式 | MVC层日期时间的格式化模式 | yyyy-MM-dd HH:mm:ss |
| char-art.default-density | 默认字符密度 | 字符画转换的默认密度设置 | medium |
| char-art.default-color-mode | 默认颜色模式 | 字符画转换的默认颜色模式 | grayscale |
| char-art.temp-directory | 字符画临时目录 | 字符画处理过程中的临时文件存储目录 | /app/temp |
| java.io.tmpdir | Java系统临时目录 | Java系统级临时文件目录 | /app/temp |
| char-art.temp-file.max-retention-hours | 临时文件最大保留时间 | 临时文件的最大保留时间（小时） | 24 |
| char-art.temp-file.cleanup-enabled | 临时文件清理开关 | 是否启用临时文件自动清理 | true |
| spring.redis.host | Redis主机地址 | Redis服务器的主机地址 | localhost |
| spring.redis.port | Redis端口 | Redis服务器的端口号 | 6379 |
| spring.redis.database | Redis数据库索引 | 使用的Redis数据库索引 | 0 |
| spring.redis.password | Redis密码 | Redis服务器的连接密码 | （空） |
| spring.redis.timeout | Redis连接超时时间 | Redis连接的超时时间（毫秒） | 60000 |
| char-art.cache.ttl | 缓存过期时间 | 字符画缓存的生存时间（秒） | 3600 |
| char-art.cache.default_key_prefix | 缓存键前缀 | 字符画缓存键的默认前缀 | char-art:text: |
| webp-processor.url | WebP处理服务URL | WebP处理服务的访问地址 | http://localhost:8081 |
| webp-processor.enabled | WebP处理服务开关 | 是否启用WebP处理服务 | true |
| webp-processor.connection-timeout | WebP服务连接超时 | WebP服务的连接超时时间（毫秒） | 600000 |
| webp-processor.max-retries | WebP服务最大重试次数 | WebP服务调用失败时的最大重试次数 | 2 |
| char-art.parallel.max-frame-threads | 最大并行帧数 | 同时处理的帧数上限 | 4 |
| char-art.parallel.thread-pool-factor | 线程池大小因子 | 线程池大小计算因子（CPU核心数的倍数） | 0.5 |
| char-art.parallel.min-threads | 最小线程数 | 线程池的最小线程数 | 1 |
| char-art.parallel.progress-update-interval | 进度更新间隔 | 进度更新的时间间隔（毫秒） | 500 |
| char-art.parallel.pixel-progress-interval | 像素进度报告间隔 | 像素处理进度报告间隔 | 1000 |
| char-art.parallel.task-timeout | 任务执行超时时间 | 单个任务的最大执行时间（毫秒） | 60000 |
| char-art.parallel.progress-cleanup-delay | 进度清理延迟 | 进度监听器清理的延迟时间（毫秒） | 60000 |

## 许可证

[MIT License](../LICENSE)
