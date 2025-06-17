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
- **Pillow 9.0.0**：图像处理库，用于WebP解析和处理
- **python-dotenv 0.19.0**：环境变量管理
- **requests 2.26.0**：HTTP客户端
- **ffmpeg-python 0.2.0**：视频处理支持
- **Werkzeug 2.0.1**：WSGI工具库

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
├── Dockerfile              # Docker构建文件
├── docker-compose.yml      # Docker Compose配置
├── requirements.txt        # 依赖项列表
├── start.bat               # Windows启动脚本
└── start.sh                # Linux/Mac启动脚本
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
    "frameCount": 帧数,
    "delays": [帧1延迟, 帧2延迟, ...],
    "frames": [帧1的base64编码, 帧2的base64编码, ...]
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
    "framePaths": ["路径1", "路径2", ...],
    "delays": [延迟1, 延迟2, ...]
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

3. 运行应用：

```bash
python app.py
```

### 方法二：使用虚拟环境（推荐）

#### Windows

```bash
# 运行启动脚本
start.bat
```

#### Linux/Mac

```bash
# 添加执行权限
chmod +x start.sh

# 运行启动脚本
./start.sh
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

## 配置选项

服务可通过环境变量或`.env`文件进行配置：

| 环境变量 | 描述 | 默认值 |
|---------|------|-------|
| `PORT` | 服务监听端口 | 5000 |
| `LOG_LEVEL` | 日志级别 (DEBUG, INFO, WARNING, ERROR) | INFO |
| `DEBUG` | 调试模式 | False |
| `MAX_CONTENT_LENGTH` | 最大上传文件大小（字节） | 10485760 (10MB) |
| `TEMP_DIR` | 临时文件目录 | 自动创建的临时目录 |
| `TEMP_FILE_TTL` | 临时文件保留时间（秒） | 3600 (1小时) |

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

[MIT License](LICENSE)