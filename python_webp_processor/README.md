# WebP 动图处理服务

一个基于 FastAPI 的 WebP 动图处理服务，提供 WebP 动图解析、帧提取和动画创建功能。

## 目录

- [WebP 动图处理服务](#webp-动图处理服务)
  - [目录](#目录)
  - [项目结构](#项目结构)
  - [技术栈](#技术栈)
  - [API接口](#api接口)
    - [WebP处理接口](#webp处理接口)
    - [进度监控接口](#进度监控接口)
    - [系统接口](#系统接口)
  - [使用说明](#使用说明)
    - [本地部署说明](#本地部署说明)
      - [环境要求](#环境要求)
      - [安装步骤](#安装步骤)
      - [注意事项](#注意事项)
    - [Docker部署说明](#docker部署说明)
    - [配置文件说明](#配置文件说明)
      - [服务器配置](#服务器配置)
      - [临时文件配置](#临时文件配置)
      - [日志配置](#日志配置)
      - [Redis配置](#redis配置)
      - [进度监控配置](#进度监控配置)
  - [许可证](#许可证)

## 项目结构

``` text
python_webp_processor/
├── api/                    # API路由模块
│   ├── __init__.py
│   ├── health.py          # 健康检查接口
│   ├── progress.py        # 进度监控接口
│   └── webp.py           # WebP处理接口
├── utils/                 # 工具模块
│   ├── __init__.py
│   ├── scheduler.py       # 任务调度器
│   ├── thread_safe_dict.py # 线程安全字典
│   └── utils.py          # 工具函数
├── .dockerignore         # Docker忽略文件
├── .env.example          # 环境变量示例文件
├── config.py             # 配置文件
├── docker-compose.yml    # Docker Compose配置
├── docker-entrypoint.sh  # Docker入口脚本
├── Dockerfile            # Docker镜像构建文件
├── Docker.md             # Docker部署文档
├── main.py               # 应用入口文件
├── README.md             # 项目说明文档
├── README_FastAPI.md     # FastAPI相关文档
└── requirements.txt      # Python依赖文件
```

## 技术栈

### 核心框架

- **FastAPI** `0.104.1` - 现代、快速的Web框架
- **Uvicorn** `0.24.0` - ASGI服务器

### 图像处理

- **Pillow** `10.1.0` - Python图像处理库

### 异步支持

- **aiofiles** `23.2.0` - 异步文件操作
- **aioredis** `2.0.1` - 异步Redis客户端

### 数据验证

- **Pydantic** `2.5.0` - 数据验证和设置管理

### 实时通信

- **sse-starlette** `1.6.5` - 服务器发送事件支持

### 任务调度

- **APScheduler** `3.10.4` - 高级Python调度器

### 缓存

- **Redis** `5.0.1` - 内存数据结构存储

### 工具库

- **python-dotenv** `1.0.0` - 环境变量管理
- **python-multipart** `0.0.6` - 多部分表单数据解析
- **fastapi-cors** `0.1.0` - CORS中间件

### 容器化

- **Docker** - 容器化部署
- **Docker Compose** - 多容器应用编排

## API接口

### WebP处理接口

将WebP动图处理为单独的帧，或将多个图片帧合成为WebP动画。

#### 处理WebP动图

- **URL**: `/api/process-webp`
- **方法**: POST
- **Content-Type**: multipart/form-data
- **参数**:
  - `image`: (必需) 要处理的WebP动图文件
  - `task_id`: (可选) 任务ID，用于跟踪处理进度，如果不提供则自动生成
- **响应**: JSON格式，包含帧信息和延迟数据

  ```json
  {
    "frameCount": 10,
    "delays": [100, 100, 100, 100, 100, 100, 100, 100, 100, 100],
    "frames": ["webp_frames_20230101123456/frame_0000.png", "webp_frames_20230101123456/frame_0001.png", ...],
    "task_id": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```

#### 创建WebP动画

- **URL**: `/api/create-webp-animation`
- **方法**: POST
- **Content-Type**: application/x-www-form-urlencoded
- **参数**:
  - `frame_paths`: (必需) JSON格式的帧文件路径数组
  - `delays`: (必需) JSON格式的延迟时间数组（毫秒）
  - `frame_format`: (必需) JSON格式的帧格式数组
  - `task_id`: (可选) 任务ID，用于跟踪处理进度，如果不提供则自动生成
- **响应**: JSON格式，包含生成的WebP动画文件路径

  ```json
  {
    "webp": "animation_20230101123456.webp",
    "task_id": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```

#### 获取图片

- **URL**: `/api/get-image/{file_path}`
- **方法**: GET
- **参数**:
  - `file_path`: (必需) 图片文件的相对路径（相对于临时目录）
- **响应**: 图片二进制数据，响应完成后自动删除临时文件

### 进度监控接口

使用SSE技术获取处理进度的实时更新。

#### 创建进度跟踪任务

- **URL**: `/api/progress/create`
- **方法**: POST
- **响应**: JSON格式，包含新创建的任务ID

  ```json
  {
    "task_id": "550e8400-e29b-41d4-a716-446655440000",
    "message": "进度跟踪任务已创建"
  }
  ```

#### 获取任务进度

- **URL**: `/api/progress/{task_id}`
- **方法**: GET
- **参数**:
  - `task_id`: (必需) 任务ID，用于标识要跟踪的特定处理任务
- **响应**: SSE事件流，包含进度更新
  - 事件类型: `webp` - 进度更新事件，包含当前进度百分比、消息、阶段等信息
  - 事件类型: `heartbeat` - 保持连接活跃的心跳事件
  - 事件类型: `close` - 连接关闭事件

#### 关闭进度连接

- **URL**: `/api/progress/close/{task_id}`
- **方法**: POST
- **参数**:
  - `task_id`: (必需) 任务ID，用于标识要关闭的特定连接
- **响应**: JSON格式，包含操作结果

  ```json
  {
    "status": "success",
    "message": "任务 550e8400-e29b-41d4-a716-446655440000 的进度连接已关闭"
  }
  ```

### 系统接口

提供服务健康状态和版本信息的检查。

#### 健康检查

- **URL**: `/api/health`
- **方法**: GET
- **响应**: JSON格式，包含服务状态信息

  ```json
  {
    "status": "healthy",
    "timestamp": "2023-01-01T12:34:56.789Z",
    "uptime": "1:23:45",
    "version": "1.0.0",
    "endpoints": [
      {"path": "/api/health", "method": "GET", "description": "健康检查"},
      {"path": "/api/progress/create", "method": "POST", "description": "创建进度跟踪任务"},
      ...
    ]
  }
  ```

#### 版本信息

- **URL**: `/api/version`
- **方法**: GET
- **响应**: JSON格式，包含版本号和构建信息

  ```json
  {
    "version": "1.0.0",
    "build_time": "2023-01-01T12:34:56.789Z",
    "python_version": "3.9.0"
  }
  ```

## 使用说明

### 本地部署说明

#### 环境要求

- Python 3.8+
- pip（Python包管理器）
- Redis 5.0+（用于缓存和任务队列）

#### 安装步骤

1. **克隆项目到本地**

   ```bash
   git clone https://github.com/yourusername/char-art-converter.git
   cd char-art-converter/python_webp_processor
   ```

2. **配置Redis服务**

   确保Redis服务已启动并可访问，默认配置为：
   - 主机：localhost
   - 端口：6379
   - 数据库：0

3. **安装依赖包**

   ```bash
   pip install -r requirements.txt
   ```

4. **配置环境变量**

   创建环境变量文件：

   ```bash
   cp .env.example .env
   ```

   根据需要修改 `.env` 文件中的配置项。

5. **运行应用**

   方法1 - 使用启动脚本（推荐）：

   ```bash
   python main.py
   ```

   方法2 - 直接使用uvicorn：

   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8081 --reload
   ```

6. **访问服务**

   服务启动后，可以通过以下URL访问：
   - API接口: <http://localhost:8081/api>
   - 健康检查: <http://localhost:8081/api/health>

   **API文档**（自动生成）：
   - **Swagger UI**: <http://localhost:8081/docs>
   - **ReDoc**: <http://localhost:8081/redoc>
   - **OpenAPI JSON**: <http://localhost:8081/openapi.json>

#### 注意事项

- **Redis依赖**：服务依赖Redis进行缓存和任务队列管理，请确保Redis服务已启动并可访问
- **Python版本**：建议使用Python 3.8或更高版本以获得最佳性能和兼容性
- **内存配置**：处理大型WebP动图可能需要较大的内存，建议在资源受限环境中限制并发请求数量
- **临时文件清理**：服务会自动清理过期的临时文件，可通过`TEMP_FILE_TTL`环境变量调整清理策略
- **文件权限**：确保应用对临时目录有读写权限

### Docker部署说明

如需了解如何使用Docker部署本服务，请参阅 [Docker部署指南](./Docker.md)。

Docker部署提供了更简便的部署方式，包含了所有必要的依赖服务（Redis等），无需手动配置环境。

### 配置文件说明

本项目使用 `.env.example` 作为配置模板文件。

#### 首次使用步骤

1. 复制模板文件：
   ```bash
   cp .env.example .env
   ```

2. 根据你的环境修改 `.env` 中的配置项

#### 重要说明

- `.env` 文件已被添加到 `.gitignore` 中，不会被提交到版本控制
- 请不要直接修改 `.env.example` 文件，除非需要更新默认配置
- 如果需要添加新的配置项，请同时更新模板文件

#### 配置参数说明

| 变量名称 | 变量中文名 | 变量作用 | 变量默认值 |
|---------|-----------|----------|----------|
| TIMEZONE | 时区设置 | 设置应用程序的时区 | Asia/Shanghai |
| PORT | 服务端口 | 设置WebP处理服务监听端口 | 8081 |
| LOG_LEVEL | 日志级别 | 设置日志输出级别 | INFO |
| LOG_FILE | 日志文件路径 | 指定日志文件的存储路径 | /app/logs/webp-processor.log |
| TEMP_FILE_TTL | 临时文件保留时间 | 临时文件的保留时间（秒） | 3600 |
| TEMP_DIR | 临时文件目录 | 临时文件的存储目录 | /app/data |
| DEBUG | 调试模式 | 是否启用调试模式 | False |
| MAX_CONTENT_LENGTH | 最大上传文件大小 | 限制上传文件的最大大小（字节） | 10485760 |
| REDIS_HOST | Redis主机地址 | Redis服务器的主机地址 | localhost |
| REDIS_PORT | Redis端口 | Redis服务器的端口号 | 6379 |
| REDIS_DB | Redis数据库索引 | 使用的Redis数据库索引 | 0 |
| REDIS_PASSWORD | Redis密码 | Redis服务器的连接密码 | （空） |
| PROGRESS_UPDATE_INTERVAL | 进度更新间隔 | 进度更新的时间间隔（秒） | 0.5 |
| JAVA_BACKEND_URL | Java后端服务URL | Java后端服务的访问地址 | http://localhost:8080 |

## 许可证

本项目采用 MIT 许可证。详情请参阅 [LICENSE](../LICENSE) 文件。
