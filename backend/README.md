# 字符画转换器后端服务

字符画转换器后端服务是一个基于Spring Boot的Web应用程序，提供将图片转换为字符画的功能。支持静态图片（JPG、PNG等）和动态图片（GIF、WebP）的处理，可以生成字符文本和字符画图像。

> **部署说明**: 如需了解如何使用Docker部署本服务，请参阅[Docker部署指南](./Docker.md)。

## 功能特点

- **多格式支持**：支持JPG、PNG、GIF和WebP等多种图片格式
- **动态图片处理**：支持GIF和WebP动图的字符画转换
- **自定义参数**：可调整字符密度和颜色模式
- **实时进度反馈**：使用SSE（Server-Sent Events）技术提供实时转换进度
- **缓存机制**：使用Redis缓存字符画文本，提高重复请求的响应速度
- **流式处理**：采用流式处理方式，减少内存占用

## API接口

### 1. 图片转换接口

将上传的图片转换为字符画图片。

- **URL**: `/api/convert`
- **方法**: POST
- **Content-Type**: multipart/form-data
- **参数**:
  - `image`: (必需) 要转换的图片文件
  - `density`: (可选) 字符密度，可选值为"low"、"medium"、"high"，默认为"medium"
  - `colorMode`: (可选) 颜色模式，可选值为"color"、"grayscale"，默认为"grayscale"
  - `limitSize`: (可选) 是否限制字符画的最大尺寸，默认为true
  - `progressId`: (可选) 进度ID，用于跟踪转换进度，如果不提供则自动生成
- **响应**: 转换后的字符画图片（二进制数据）

### 2. 获取字符文本接口

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

### 3. 进度监控接口

使用SSE技术获取转换进度的实时更新。

- **URL**: `/api/progress/{id}`
- **方法**: GET
- **参数**:
  - `id`: (必需) 进度ID，用于标识要跟踪的特定转换任务
- **响应**: SSE事件流，包含进度更新
  - 事件类型: `init` - 连接建立时发送的初始化事件
  - 事件类型: `progress` - 进度更新事件，包含当前进度百分比、消息等信息
  - 事件类型: `heartbeat` - 保持连接活跃的心跳事件

### 4. 健康检查接口

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

## 项目结构

``` text
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
└── pom.xml                          # Maven配置文件
```

## 使用说明

### 环境要求

- JDK 11+
- Maven 3.6+
- Redis 5.0+
- Python WebP处理服务（用于处理WebP动图）

### 配置说明

主要配置项在`application.properties`文件中：

```properties
# 服务器配置
server.port=8080

# 上传文件配置
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Redis配置
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.database=0
spring.redis.timeout=60000

# 字符画缓存配置
char-art.cache.ttl=3600

# WebP处理服务配置
webp-processor.url=http://localhost:8081
webp-processor.enabled=true
webp-processor.connection-timeout=5000
webp-processor.max-retries=2
```

### 安装步骤

1. 克隆项目到本地

    ```bash
    git clone https://github.com/yourusername/char-art-converter.git
    cd char-art-converter/backend
    ```

2. 编译项目

    ```bash
    mvn clean package
    ```

3. 运行项目

    ```bash
    java -jar target/char-art-converter-0.0.1-SNAPSHOT.jar
    ```

    或者使用Maven直接运行：

    ```bash
    mvn spring-boot:run
    ```

4. 访问服务

服务启动后，可以通过以下URL访问：

- API接口: <http://localhost:8080/api>
- 健康检查: <http://localhost:8080/api/health>

## 注意事项

1. **Redis依赖**：服务依赖Redis进行缓存，请确保Redis服务已启动并可访问。

2. **WebP处理服务**：处理WebP动图需要Python WebP处理服务，请确保该服务已启动并配置正确的URL。

3. **内存配置**：处理大图片或高分辨率GIF可能需要较大的内存，可以通过JVM参数调整内存大小：

   ```bash
   java -Xmx1g -jar target/char-art-converter-0.0.1-SNAPSHOT.jar
   ```

4. **临时文件清理**：服务会创建临时文件进行处理，正常情况下会自动清理，但如果服务异常终止，可能需要手动清理临时目录。

5. **安全考虑**：生产环境部署时，建议配置适当的安全措施，如HTTPS、请求限制等。

## 开发指南

### 添加新的图片格式支持

要添加新的图片格式支持，需要在以下位置进行修改：

1. `CharArtController.java`中的文件类型验证逻辑
2. 根据图片特性，在`CharArtProcessor.java`中添加相应的处理方法

### 自定义字符集

可以通过修改`CharArtProcessor.java`中的`CHAR_SETS`数组来自定义字符集：

```java
private static final String[] CHAR_SETS = {
    " .:-=+*#%@", // 低密度
    " .,:;i1tfLCG08@", // 中密度
    " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$" // 高密度
};
```

## 许可证

[MIT License](../LICENSE)
