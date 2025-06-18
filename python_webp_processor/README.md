# WebP动图处理服务

这是一个Python Flask应用，用于处理WebP格式的动图，并将其解析为Java后端所需的数据结构。该服务作为字符画转换器项目的组件，专门负责WebP动图的处理，为主后端提供支持。

> **Docker部署说明**: 如需了解如何使用Docker部署本服务，请参阅[Docker部署指南](./Docker.md)。

## 功能特点

- 接收并处理WebP格式动图
- 解析动图的帧和延迟信息
- 返回JSON格式的数据，包含帧数、延迟时间和帧图像数据
- 支持创建WebP动画（从多个图片帧合成）
- 自动清理临时文件
- 提供健康检查和版本信息接口
- 支持Docker容器化部署

## 技术栈

- **Python 3.6+**：核心编程语言
- **Flask 2.0.1**：Web框架
- **Gunicorn 20.1.0**：WSGI HTTP服务器，用于生产环境部署
- **Pillow 9.0.0**：图像处理库，用于WebP解析和处理
- **python-dotenv 0.19.0**：环境变量管理
- **requests 2.26.0**：HTTP客户端
- **ffmpeg-python 0.2.0**：视频处理支持
- **Werkzeug 2.0.1**：WSGI工具库
- **setproctitle 1.3.2**：设置进程名称，便于监控（生产环境推荐）

## 项目结构

```
.
├── api/                    # API接口目录
│   ├── __init__.py        # API蓝图初始化
│   ├── health.py          # 健康检查和版本信息接口
│   └── webp.py            # WebP处理接口
├── utils/                  # 工具函数目录
│   └── utils.py           # 工具函数，如临时文件清理
├── .env                    # 环境变量配置文件
├── app.py                  # 应用入口
├── config.py               # 配置管理
├── wsgi.py                 # WSGI入口点，用于Gunicorn
├── gunicorn.conf.py        # Gunicorn配置文件
├── Dockerfile              # Docker构建文件
├── docker-compose.yml      # Docker Compose配置
├── docker-entrypoint.sh    # Docker容器入口脚本
└── requirements.txt        # 依赖项列表
```

## API接口

### 1. 处理WebP动图

**URL**: `/api/process-webp`

**方法**: `POST`

**参数**:
- `image`: WebP格式的动图文件（multipart/form-data）

**返回**:
```json
{
    "frameCount": "帧数",
    "delays": ["帧1延迟", "帧2延迟", "..."],
    "frames": ["帧1的base64编码", "帧2的base64编码", "..."]
}
```

**错误返回**:
```json
{
    "error": "错误信息"
}
```

### 2. 创建WebP动画

**URL**: `/api/create-webp-animation`

**方法**: `POST`

**请求体** (JSON):
```json
{
    "framePaths": ["路径1", "路径2", "..."],
    "delays": ["延迟1", "延迟2", "..."]
}
```

**返回**:
```json
{
    "webpPath": "WebP文件临时路径"
}
```

**错误返回**:
```json
{
    "error": "错误信息"
}
```

### 3. 健康检查

**URL**: `/api/health`

**方法**: `GET`

**返回**:
```json
{
    "status": "ok",
    "timestamp": "2023-05-01T12:34:56.789Z",
    "service": "WebP Processor API",
    "version": "1.0.0"
}
```

### 4. 版本信息

**URL**: `/api/version`

**方法**: `GET`

**返回**:
```json
{
    "name": "WebP Processor API",
    "version": "1.1.0",
    "description": "WebP动图处理服务",
    "endpoints": [
        {"path": "/api/health", "method": "GET", "description": "健康检查"},
        {"path": "/api/process-webp", "method": "POST", "description": "处理WebP动图"},
        {"path": "/api/create-webp-animation", "method": "POST", "description": "创建WebP动画"},
        {"path": "/api/version", "method": "GET", "description": "版本信息"}
    ]
}
```

## 安装指南

### 前提条件

- Python 3.6+
- pip（Python包管理器）

### 方法一：直接安装

1. 克隆或下载项目代码

2. 安装依赖包：

    ```bash
    pip install -r requirements.txt
    ```

3. 运行应用（开发模式）：

    ```bash
    python app.py
    ```

4. 使用Gunicorn运行（生产模式）：

    ```bash
    gunicorn --config gunicorn.conf.py wsgi:application
    ```

### 方法二：直接使用Gunicorn（推荐）

```bash
# 使用Gunicorn启动
gunicorn --config gunicorn.conf.py wsgi:application
```

### 方法三：Docker部署

1. 构建并启动容器：

   ```bash
   docker-compose up -d
   ```

2. 查看日志：

   ```bash
   docker-compose logs -f
   ```

3. 停止服务：

   ```bash
   docker-compose down
   ```

4. 使用自定义Gunicorn配置启动容器：

```bash
docker run -d \
  --name webp-processor \
  -p 8081:5000 \
  -e GUNICORN_WORKERS=4 \
  -e GUNICORN_TIMEOUT=180 \
  webp-processor:latest
```

## 配置选项

服务可通过环境变量或`.env`文件进行配置：

### 基本配置

| 环境变量                 | 描述                                 | 默认值             |
|----------------------|------------------------------------|-----------------|
| `PORT`               | 服务监听端口                             | 5000            |
| `LOG_LEVEL`          | 日志级别 (DEBUG, INFO, WARNING, ERROR) | INFO            |
| `DEBUG`              | 调试模式                               | False           |
| `MAX_CONTENT_LENGTH` | 最大上传文件大小（字节）                       | 10485760 (10MB) |
| `TEMP_DIR`           | 临时文件目录                             | 自动创建的临时目录       |
| `TEMP_FILE_TTL`      | 临时文件保留时间（秒）                        | 3600 (1小时)      |

### Gunicorn配置

| 环境变量                           | 描述             | 默认值        |
|--------------------------------|----------------|------------|
| `GUNICORN_WORKERS`             | 工作进程数          | CPU核心数×2+1 |
| `GUNICORN_TIMEOUT`             | 请求超时时间(秒)      | 120        |
| `GUNICORN_MAX_REQUESTS`        | 每个工作进程处理的最大请求数 | 1000       |
| `GUNICORN_MAX_REQUESTS_JITTER` | 最大请求数的随机抖动值    | 50         |

## 与Java后端集成

此服务设计为与Java后端的字符画转换服务配合使用：

1. Java后端通过HTTP请求调用此服务的`/api/process-webp`接口，上传WebP动图
2. 服务解析WebP动图并返回帧和延迟信息
3. Java后端使用返回的信息处理每一帧，生成字符画
4. Java后端可以调用`/api/create-webp-animation`接口，将处理后的字符画帧合成为新的WebP动画

## 注意事项

1. **临时文件管理**：
   - 服务会自动清理过期的临时文件
   - 可通过`TEMP_FILE_TTL`环境变量调整临时文件保留时间
   - 服务退出时会清理自动创建的临时目录

2. **文件大小限制**：
   - 默认限制上传文件大小为10MB
   - 可通过`MAX_CONTENT_LENGTH`环境变量调整

3. **内存使用**：
   - 处理大型WebP动图可能消耗较多内存
   - 在资源受限环境中，建议限制并发请求数量

4. **Docker部署**：
   - 使用Docker部署时，可通过`docker-compose.yml`文件调整环境变量
   - 服务包含健康检查，可与容器编排系统集成

5. **安全考虑**：
   - 此服务设计为内部服务，不建议直接暴露到公网
   - 生产环境中应考虑添加身份验证和访问控制

## 故障排除

1. **服务无法启动**：
   - 检查Python版本是否满足要求
   - 检查依赖项是否正确安装
   - 检查端口是否被占用

2. **文件上传失败**：
   - 检查文件格式是否为WebP
   - 检查文件大小是否超过限制
   - 检查临时目录是否有写入权限

3. **处理WebP失败**：
   - 检查WebP文件是否为有效的动图
   - 检查Pillow库是否正确安装并支持WebP格式

## Gunicorn部署

### 为什么使用Gunicorn

- **并发处理能力**：Gunicorn使用预分叉工作模式，可以同时处理多个请求
- **稳定性**：自动处理崩溃的工作进程，提高服务可靠性
- **资源利用**：更有效地利用多核处理器
- **生产级别**：适合生产环境的WSGI HTTP服务器

### Gunicorn配置文件

服务使用`gunicorn.conf.py`作为Gunicorn的配置文件，包含以下主要设置：

```python
# 绑定的IP和端口
bind = "0.0.0.0:5000"

# 工作进程数
# 设置为多核CPU数量×2+1
workers = 5

# 工作模式
worker_class = 'sync'

# 日志配置
accesslog = "/app/logs/access.log"
errorlog = "/app/logs/error.log"
```

### 性能调优

#### 工作进程数量

工作进程数量的最佳值取决于服务器的CPU核心数和内存大小。一般建议：

- CPU密集型应用：`CPU核心数 + 1`
- I/O密集型应用：`CPU核心数 × 2 + 1`

对于WebP处理服务，由于涉及图像处理，属于CPU和I/O混合型，默认使用`CPU核心数 × 2 + 1`的配置。

#### 最大请求数

设置`GUNICORN_MAX_REQUESTS`可以防止内存泄漏，当工作进程处理的请求数达到这个值时，会自动重启。

`GUNICORN_MAX_REQUESTS_JITTER`添加随机抖动，防止所有工作进程同时重启。

### 监控

#### 日志

Gunicorn的访问日志和错误日志分别保存在：

- 访问日志：`/app/logs/access.log`
- 错误日志：`/app/logs/error.log`

### 常见问题

#### 1. 请求超时

如果处理大型WebP文件时出现超时，可以增加`GUNICORN_TIMEOUT`的值：

```bash
docker run -d --name webp-processor -p 8081:5000 -e GUNICORN_TIMEOUT=300 webp-processor:latest
```

#### 2. 内存使用过高

如果服务器内存有限，可以减少工作进程数量：

```bash
docker run -d --name webp-processor -p 8081:5000 -e GUNICORN_WORKERS=2 webp-processor:latest
```

#### 3. 进程没有自动重启

检查Docker的重启策略是否设置为`unless-stopped`或`always`。

## 开发指南

### 添加新API端点

1. 在`api`目录下创建新的Python文件或在现有文件中添加新的路由
2. 在`api/__init__.py`中导入新模块（如果创建了新文件）
3. 使用`@api_bp.route`装饰器定义新的路由

### 修改配置

1. 在`config.py`中添加新的配置项
2. 使用`os.environ.get`从环境变量获取值，并提供默认值

### 构建Docker镜像

```bash
docker build -t webp-processor .
```

## 许可证

[MIT License](../LICENSE)
