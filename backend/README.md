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
  - `colorMode`: (可选) 颜色模式，可选值为"color"、"grayscale"，默认为"grayscale"
  - `limitSize`: (可选) 是否限制字符画的最大尺寸，默认为true
  - `progressId`: (可选) 进度ID，用于跟踪转换进度，如果不提供则自动生成
- **响应**: JSON格式，包含文件路径和内容类型

  ```json
  {
    "filePath": "result_1234567890.png",
    "contentType": "image/png"
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
  - 事件类型: `init` - 连接建立时发送的初始化事件
  - 事件类型: `progress` - 进度更新事件，包含当前进度百分比、消息等信息
  - 事件类型: `heartbeat` - 保持连接活跃的心跳事件
  - 事件类型: `close` - 连接关闭事件

### 关闭进度连接接口

主动关闭指定ID的进度连接。

- **URL**: `/api/progress/{id}/close`
- **方法**: POST
- **参数**:
  - `id`: (必需) 进度ID，用于标识要关闭的特定连接
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

提供服务健康状态的检查。

- **URL**: `/api/health`
- **方法**: GET
- **响应**: JSON格式，包含服务状态信息

  ```json
  {
    "status": "UP",
    "message": "字符画转换服务正常运行"
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

主要配置项在 `application.properties` 文件中，以下是各配置项的详细说明：

#### 服务器配置

```properties
# 服务器端口
server.port=8080
```

#### 文件上传配置

```properties
# 单个文件最大大小
spring.servlet.multipart.max-file-size=10MB
# 请求最大大小
spring.servlet.multipart.max-request-size=10MB
```

#### 日志配置

```properties
# 日志级别
logging.level.com.doreamr233.charartconverter=INFO
# 日志文件路径
logging.file.name=/app/logs/char-art-converter.log
# 日志文件最大大小
logging.logback.rollingpolicy.max-file-size=10MB
# 日志文件保留天数
logging.logback.rollingpolicy.max-history=30
# 日志时间格式
logging.pattern.dateformat=yyyy-MM-dd HH:mm:ss.SSS
# 文件编码
logging.charset.console=UTF-8
logging.charset.file=UTF-8
```

#### 时区配置

```properties
# Jackson时区设置
spring.jackson.time-zone=Asia/Shanghai
# 日期时间格式
spring.mvc.format.date-time=yyyy-MM-dd HH:mm:ss
```

#### 字符画转换配置

```properties
# 默认字符密度（low/medium/high）
char-art.default-density=medium
# 默认颜色模式（color/grayscale）
char-art.default-color-mode=grayscale
```

#### 临时文件配置

```properties
# 临时文件目录
java.io.tmpdir=/app/data
```

#### Redis配置

```properties
# Redis主机地址
spring.redis.host=localhost
# Redis端口
spring.redis.port=6379
# Redis数据库索引
spring.redis.database=0
# Redis连接超时时间（毫秒）
spring.redis.timeout=60000
```

#### 字符画缓存配置

```properties
# 缓存过期时间（秒）
char-art.cache.ttl=3600
# 缓存键前缀
char-art.cache.default_key_prefix=char-art:text:
```

#### WebP处理服务配置

```properties
# WebP处理服务URL
webp-processor.url=http://localhost:8081
# 是否启用WebP处理服务
webp-processor.enabled=true
# 连接超时时间（毫秒）
webp-processor.connection-timeout=600000
# 最大重试次数
webp-processor.max-retries=2
```

## 许可证

[MIT License](../LICENSE)
