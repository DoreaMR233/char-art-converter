# 字符画转换器

一个功能强大的Web应用，可以将图片转换为字符画，支持静态图片、GIF动图和WebP动图的转换，并提供实时进度显示和多种导出选项。

## 目录

- [字符画转换器](#字符画转换器)
  - [目录](#目录)
  - [功能特点](#功能特点)
  - [项目截图](#项目截图)
  - [项目结构](#项目结构)
  - [技术栈](#技术栈)
    - [前端技术栈](#前端技术栈)
    - [后端技术栈](#后端技术栈)
    - [WebP处理器技术栈](#webp处理器技术栈)
  - [使用说明](#使用说明)
    - [本地部署说明](#本地部署说明)
      - [环境要求](#环境要求)
      - [安装步骤](#安装步骤)
      - [启动服务](#启动服务)
      - [注意事项](#注意事项)
    - [Docker部署说明](#docker部署说明)
    - [配置文件说明](#配置文件说明)
      - [通用配置](#通用配置)
      - [后端服务配置](#后端服务配置)
      - [前端服务配置](#前端服务配置)
      - [WebP处理器配置](#webp处理器配置)
      - [Redis配置](#redis配置)
      - [日志配置](#日志配置)
      - [字符画配置](#字符画配置)
  - [许可证](#许可证)

## 功能特点

- **多格式支持**：支持静态图片（JPG、PNG、BMP等）、GIF动图和WebP动图的转换
- **字符密度调整**：提供低/中/高三种字符密度选项，满足不同细节需求
- **色彩模式**：支持彩色和灰度两种字符画渲染模式
- **实时进度**：使用SSE（Server-Sent Events）技术实时显示转换进度
- **多种导出**：支持导出为字符文本和图片格式
- **流式处理**：使用流式传输提高大图片和动图的处理效率
- **缓存机制**：使用Redis缓存字符画文本，提高重复访问性能
- **现代化界面**：基于Vue 3和Element Plus构建的响应式用户界面
- **容器化部署**：支持Docker和Docker Compose一键部署

## 项目截图

![主界面](./img/image1.png)

![导出选项](./img/image2.png)

![使用说明](./img/image3.png)

![字符画转换过程](./img/image4.png)

![彩色字符模式](./img/image5.png)

![彩色背景模式](./img/image6.png)

![灰度模式](./img/image7.png)

![动图转换结果](./img/image8.gif)

## 项目结构

```text
char-art-converter/
├── frontend/                     # 前端服务 (Vue 3 + Element Plus)
│   ├── public/                   # 静态资源
│   ├── src/                      # 源代码
│   │   ├── api/                  # API调用模块
│   │   ├── assets/               # 资源文件
│   │   ├── App.vue               # 主组件
│   │   └── main.js               # 入口文件
│   ├── .env.example              # 环境变量示例
│   ├── docker-compose.yml        # Docker Compose配置
│   ├── Dockerfile                # Docker镜像构建文件
│   ├── nginx.conf                # Nginx配置
│   ├── package.json              # 依赖配置
│   └── vite.config.js            # Vite配置
│
├── backend/                      # 后端服务 (Spring Boot)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/doreamr233/charartconverter/
│   │   │   │   ├── config/       # 配置类
│   │   │   │   ├── controller/   # 控制器
│   │   │   │   ├── exception/    # 异常处理
│   │   │   │   ├── model/        # 数据模型
│   │   │   │   ├── service/      # 服务接口和实现
│   │   │   │   ├── util/         # 工具类
│   │   │   │   └── CharArtConverterApplication.java  # 启动类
│   │   │   └── resources/        # 资源文件
│   │   └── test/                 # 测试代码
│   ├── docker-compose.yml        # Docker Compose配置
│   ├── Dockerfile                # Docker镜像构建文件
│   └── pom.xml                   # Maven配置
│
├── python_webp_processor/        # WebP处理服务 (FastAPI)
│   ├── api/                      # API接口模块
│   │   ├── health.py             # 健康检查
│   │   ├── progress.py           # 进度监控
│   │   └── webp.py               # WebP处理
│   ├── utils/                    # 工具模块
│   │   ├── scheduler.py          # 任务调度器
│   │   ├── thread_safe_dict.py   # 线程安全字典
│   │   └── utils.py              # 工具函数
│   ├── .env.example              # 环境变量示例
│   ├── config.py                 # 配置文件
│   ├── docker-compose.yml        # Docker Compose配置
│   ├── Dockerfile                # Docker镜像构建文件
│   ├── main.py                   # 应用入口
│   └── requirements.txt          # Python依赖
│
├── Docker.md                     # Docker部署详细文档
├── Dockerfile                    # 一体化Docker镜像构建文件
├── docker-compose.yml            # 一体化Docker Compose配置
├── docker-start.sh               # Docker容器启动脚本
├── nginx.conf.template           # Nginx配置模板
├── supervisord.conf              # 进程管理配置
└── README.md                     # 项目说明文档
```

## 技术栈

### 前端技术栈

- **核心框架**: Vue 3 (Composition API)
- **构建工具**: Vite 4.4.0
- **UI组件库**: Element Plus 2.3.8
- **HTTP客户端**: Axios 1.4.0
- **图标库**: @element-plus/icons-vue 2.3.1
- **实时通信**: Server-Sent Events (SSE)
- **Web服务器**: Nginx (生产环境)
- **容器化**: Docker

### 后端技术栈

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

### WebP处理器技术栈

- **核心框架**: FastAPI 0.104.1
- **ASGI服务器**: Uvicorn 0.24.0
- **图像处理**: Pillow 10.1.0
- **异步支持**:
  - aiofiles 23.2.0 (异步文件操作)
  - aioredis 2.0.1 (异步Redis客户端)
- **数据验证**: Pydantic 2.5.0
- **实时通信**: sse-starlette 1.6.5
- **任务调度**: APScheduler 3.10.4
- **缓存**: Redis 5.0.1
- **容器化**: Docker

## 使用说明

### 本地部署说明

#### 环境要求

- **前端**: Node.js 14.0.0+, npm 6.0.0+
- **后端**: Java 11+, Maven 3.6+
- **WebP处理器**: Python 3.6+
- **缓存服务**: Redis 6.0+
- **系统要求**: 至少2GB内存，5GB可用磁盘空间

#### 安装步骤

1. **克隆项目**

   ```bash
   git clone https://github.com/yourusername/char-art-converter.git
   cd char-art-converter
   ```

2. **启动Redis服务**

   ```bash
   # Windows (使用Redis for Windows)
   redis-server
   
   # Linux/macOS
   sudo systemctl start redis
   # 或
   redis-server /etc/redis/redis.conf
   ```

3. **安装和启动后端服务**

   ```bash
   cd backend
   
   # 安装依赖并构建
   mvn clean package
   
   # 启动应用
   java -jar target/char-art-converter-1.0.0.jar
   ```

4. **安装和启动WebP处理器**

   ```bash
   cd python_webp_processor
   
   # 安装依赖
   pip install -r requirements.txt
   
   # 启动应用
   python main.py
   ```

5. **安装和启动前端服务**

   ```bash
   cd frontend
   
   # 安装依赖
   npm install
   
   # 开发模式启动
   npm run dev
   
   # 或构建生产版本
   npm run build
   ```

#### 启动服务

启动完成后，各服务将在以下端口运行：

- **前端服务**: <http://localhost:5174> (开发模式) 或 <http://localhost:80> (生产模式)
- **后端服务**: <http://localhost:8080>
- **WebP处理器**: <http://localhost:8081>
- **Redis服务**: localhost:6379

#### 注意事项

- 确保所有服务的端口没有被其他应用占用
- 大型GIF和WebP动图处理可能需要较长时间，请耐心等待
- 建议上传的图片不要超过10MB
- 字符画的质量取决于原图的清晰度和对比度
- 默认使用Redis缓存字符画文本，可以在配置文件中调整缓存时间

### Docker部署说明

推荐使用Docker进行部署，支持一键启动所有服务。详细的Docker部署说明请参阅 [Docker部署指南](./Docker.md)。

**快速启动**：

```bash
# 使用Docker Compose一键部署
docker-compose up -d

# 访问应用
# 前端: http://localhost:80
# 后端API: http://localhost:8080
# WebP处理器: http://localhost:8081
```

### 配置文件说明

#### 配置参数说明

| 变量名称 | 变量中文名 | 变量作用 | 变量默认值 |
|---------|-----------|----------|----------|
| **后端服务配置** | | | |
| server.port | 服务器端口 | 设置应用服务器监听端口 | 8080 |
| spring.servlet.multipart.max-file-size | 单个文件最大大小 | 限制上传单个文件的最大大小 | 10MB |
| spring.servlet.multipart.max-request-size | 请求最大大小 | 限制整个请求的最大大小 | 10MB |
| spring.servlet.multipart.location | 文件上传临时目录 | 指定multipart文件的临时存储位置 | /app/temp |
| logging.level.com.doreamr233.charartconverter | 应用日志级别 | 设置应用程序的日志输出级别 | INFO |
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
| **前端服务配置** | | | |
| VITE_APP_TITLE | 应用名称 | 设置应用程序的标题 | 字符画转换器 |
| VITE_APP_VERSION | 应用版本 | 设置应用程序的版本号 | 1.0.0 |
| VITE_API_BASE_PATH | API基础路径 | 设置API请求的基础路径 | /api |
| VITE_MAX_UPLOAD_SIZE | 上传文件最大大小 | 限制前端上传文件的最大大小（MB） | 10 |
| VITE_API_URL | API服务器地址 | 生产环境中的后端服务地址 | http://backend:8080 |
| VITE_BASE_PATH | 资源路径前缀 | 应用部署在子路径下的路径前缀 | charart |
| VITE_DEBUG | 调试模式开关 | 是否启用前端调试模式 | false |
| VITE_SOURCEMAP | 源码映射开关 | 是否启用源码映射 | false |
| **WebP处理器配置** | | | |
| TIMEZONE | 时区设置 | 设置WebP处理器的时区 | Asia/Shanghai |
| PORT | WebP服务端口 | WebP处理器服务监听端口 | 8081 |
| LOG_LEVEL | 日志级别 | WebP处理器的日志输出级别 | INFO |
| LOG_FILE | 日志文件路径 | WebP处理器日志文件存储路径 | /app/logs/webp-processor.log |
| TEMP_FILE_TTL | 临时文件保留时间 | 临时文件的保留时间（秒） | 3600 |
| TEMP_DIR | 临时文件目录 | 临时文件存储目录 | /app/data |
| DEBUG | 调试模式 | 是否启用WebP处理器调试模式 | False |
| MAX_CONTENT_LENGTH | 最大内容长度 | WebP处理器最大处理文件大小（字节） | 10485760 |
| REDIS_HOST | Redis主机地址 | WebP处理器连接的Redis主机 | localhost |
| REDIS_PORT | Redis端口 | WebP处理器连接的Redis端口 | 6379 |
| REDIS_DB | Redis数据库 | WebP处理器使用的Redis数据库索引 | 0 |
| REDIS_PASSWORD | Redis密码 | WebP处理器连接Redis的密码 | （空） |
| PROGRESS_UPDATE_INTERVAL | 进度更新间隔 | WebP处理进度更新间隔（秒） | 0.5 |
| JAVA_BACKEND_URL | Java后端服务URL | WebP处理器连接的Java后端服务地址 | http://localhost:8080 |

## 许可证

[MIT License](LICENSE)
