# WebP 处理服务 Docker 部署指南

本文档提供了使用 Docker 部署 WebP 处理服务的详细说明，包括 Docker Compose 和 Docker Run 两种部署方式。

## 目录

- [WebP 处理服务 Docker 部署指南](#webp-处理服务-docker-部署指南)
  - [目录](#目录)
  - [前提条件](#前提条件)
  - [项目结构](#项目结构)
  - [使用 Docker Compose 部署](#使用-docker-compose-部署)
    - [构建并启动服务](#构建并启动服务)
    - [验证Docker Compose服务状态](#验证docker-compose服务状态)
    - [访问服务](#访问服务)
    - [停止服务](#停止服务)
  - [使用 Docker Run 部署](#使用-docker-run-部署)
    - [构建镜像](#构建镜像)
    - [运行容器](#运行容器)
      - [基本运行](#基本运行)
      - [带自定义配置运行](#带自定义配置运行)
    - [验证Docker Run服务状态](#验证docker-run服务状态)
  - [配置参数](#配置参数)
    - [配置文件变量](#配置文件变量)
      - [使用示例](#使用示例)
    - [卷](#卷)
      - [卷使用示例](#卷使用示例)
    - [网络](#网络)
      - [网络使用示例](#网络使用示例)
  - [与其他服务集成](#与其他服务集成)
    - [与后端服务集成](#与后端服务集成)
      - [使用Docker Compose集成](#使用docker-compose集成)
      - [使用Docker网络集成](#使用docker网络集成)
    - [与前端服务集成](#与前端服务集成)
      - [使用Docker Compose方式集成](#使用docker-compose方式集成)
      - [使用Docker网络方式集成](#使用docker网络方式集成)
    - [与Redis服务集成](#与redis服务集成)
    - [完整的服务集成示例](#完整的服务集成示例)
  - [自定义构建](#自定义构建)
    - [修改Dockerfile](#修改dockerfile)
    - [修改启动脚本](#修改启动脚本)
  - [常见问题](#常见问题)
    - [1. Redis连接问题](#1-redis连接问题)
    - [2. 文件上传大小限制](#2-文件上传大小限制)
    - [3. 临时文件清理](#3-临时文件清理)
    - [4. 健康检查失败](#4-健康检查失败)
    - [5. 日志查看](#5-日志查看)
    - [6. 停止和删除容器](#6-停止和删除容器)

## 前提条件

在开始之前，请确保您的系统已安装以下软件：

- Docker 20.10.0 或更高版本
- Docker Compose 2.0.0 或更高版本（如果使用 Docker Compose 部署）
- 至少 512MB 可用内存
- 至少 1GB 可用磁盘空间

## 项目结构

``` text
python_webp_processor/
├── Dockerfile                 # Docker 镜像构建文件
├── docker-compose.yml        # Docker Compose 配置文件
├── docker-entrypoint.sh      # 容器启动脚本
├── requirements.txt           # Python 依赖包列表
├── .env                       # 环境变量配置文件
├── .env.template             # 环境变量模板文件
├── config.py                 # 应用配置文件
├── main.py                   # 应用入口文件
├── utils/                    # 工具模块
└── logs/                     # 日志目录（运行时创建）
```

## 使用 Docker Compose 部署

### 构建并启动服务

在 `python_webp_processor` 目录下执行以下命令：

```bash
# 构建并启动服务
docker-compose up -d

# 仅构建镜像
docker-compose build

# 强制重新构建
docker-compose up -d --build
```

### 验证Docker Compose服务状态

```bash
# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f webp-processor

# 查看最近100行日志
docker-compose logs --tail=100 webp-processor
```

### 访问服务

服务启动后，可以通过以下方式访问：

- **健康检查端点**: <http://localhost:8081/api/health>
- **WebP转换API**: <http://localhost:8081/api/convert>
- **进度查询API**: <http://localhost:8081/api/progress>

### 停止服务

```bash
# 停止服务
docker-compose down

# 停止服务并删除卷
docker-compose down -v

# 停止服务并删除镜像
docker-compose down --rmi all
```

## 使用 Docker Run 部署

### 构建镜像

```bash
# 在 python_webp_processor 目录下构建镜像
docker build -t webp-processor:latest .

# 使用自定义标签构建
docker build -t webp-processor:v1.0.0 .
```

### 运行容器

#### 基本运行

```bash
# 创建 Docker 网络
docker network create char-art-network

# 启动 Redis 服务
docker run -d --name redis \
  --network char-art-network \
  -v redis-data:/data \
  redis:6.2-alpine redis-server --appendonly yes

# 启动 WebP 处理服务
docker run -d --name webp-processor \
  --network char-art-network \
  -p 8081:8081 \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  -e REDIS_HOST=redis \
  -e JAVA_BACKEND_URL=http://localhost:8080 \
  webp-processor:latest
```

#### 带自定义配置运行

```bash
docker run -d --name webp-processor \
  --network char-art-network \
  -p 8081:8081 \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  -e PORT=8081 \
  -e LOG_LEVEL=DEBUG \
  -e DEBUG=True \
  -e MAX_CONTENT_LENGTH=20971520 \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e TEMP_FILE_TTL=7200 \
  -e JAVA_BACKEND_URL=http://localhost:8080 \
  webp-processor:latest
```

### 验证Docker Run服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs -f webp-processor

# 测试健康检查
curl -f http://localhost:8081/api/health
```

## 配置参数

### 配置文件变量

| 变量名称                       | 变量中文名       | 变量作用          | 变量默认值                          |
|----------------------------|-------------|---------------|--------------------------------|
| `PORT`                     | 服务端口        | WebP 处理服务监听端口 | `8081`                         |
| `LOG_LEVEL`                | 日志级别        | 应用日志输出级别      | `INFO`                         |
| `LOG_FILE`                 | 日志文件路径      | 日志文件存储路径      | `/app/logs/webp-processor.log` |
| `TIMEZONE`                 | 时区设置        | 应用程序时区配置      | `Asia/Shanghai`                |
| `TEMP_FILE_TTL`            | 临时文件保留时间    | 临时文件自动清理时间（秒） | `3600`                         |
| `TEMP_DIR`                 | 临时文件目录      | 临时文件存储目录      | `/app/data`                    |
| `DEBUG`                    | 调试模式        | 是否启用调试模式      | `False`                        |
| `MAX_CONTENT_LENGTH`       | 最大上传文件大小    | 请求内容最大长度（字节）  | `10485760`                     |
| `REDIS_HOST`               | Redis 主机地址  | Redis 服务器地址   | `localhost`                    |
| `REDIS_PORT`               | Redis 端口    | Redis 服务器端口   | `6379`                         |
| `REDIS_DB`                 | Redis 数据库索引 | Redis 数据库编号   | `0`                            |
| `REDIS_PASSWORD`           | Redis 密码    | Redis 服务器密码   | `None`                         |
| `REDIS_CHANNEL`            | Redis 频道    | SSE 消息推送频道名称  | `sse`                          |
| `PROGRESS_UPDATE_INTERVAL` | 进度更新间隔      | 进度信息更新频率（秒）   | `0.5`                          |
| `JAVA_BACKEND_URL`         | 后端服务地址      | Java 后端服务 URL | `http://localhost:8080`        |

#### 使用示例

**Docker Compose 配置示例：**

```yaml
services:
  webp-processor:
    environment:
      - PORT=8081
      - LOG_LEVEL=INFO
      - DEBUG=False
      - MAX_CONTENT_LENGTH=10485760
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - TEMP_FILE_TTL=3600
      - JAVA_BACKEND_URL=http://localhost:8080
```

**Docker Run 配置示例：**

```bash
docker run -d --name webp-processor \
  -e PORT=8081 \
  -e LOG_LEVEL=INFO \
  -e DEBUG=False \
  -e MAX_CONTENT_LENGTH=10485760 \
  -e REDIS_HOST=redis \
  -e TEMP_FILE_TTL=3600 \
  webp-processor:latest
```

### 卷

| 卷名称                   | 容器路径        | 用途          | 说明                 |
|-----------------------|-------------|-------------|--------------------|
| `webp-processor-data` | `/app/data` | 临时文件存储      | 存储上传的图片和处理过程中的临时文件 |
| `webp-processor-logs` | `/app/logs` | 日志文件存储      | 存储应用程序日志文件         |
| `redis-data`          | `/data`     | Redis 数据持久化 | 存储 Redis 数据库文件     |

#### 卷使用示例

**Docker Compose 卷配置：**

```yaml
services:
  webp-processor:
    volumes:
      - webp-processor-data:/app/data
      - webp-processor-logs:/app/logs
      - ./config:/app/config:ro  # 只读配置文件

volumes:
  webp-processor-data:
    driver: local
  webp-processor-logs:
    driver: local
```

**Docker Run 卷配置：**

```bash
# 使用命名卷
docker run -d --name webp-processor \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  webp-processor:latest

# 使用绑定挂载
docker run -d --name webp-processor \
  -v /host/path/data:/app/data \
  -v /host/path/logs:/app/logs \
  webp-processor:latest
```

### 网络

| 网络名称               | 类型     | 用途    | 说明                       |
|--------------------|--------|-------|--------------------------|
| `char-art-network` | bridge | 服务间通信 | 连接 WebP 处理服务、Redis 和后端服务 |
| `default`          | bridge | 默认网络  | Docker Compose 创建的默认网络   |

#### 网络使用示例

**Docker Compose 网络配置：**

```yaml
services:
  webp-processor:
    networks:
      - char-art-network
      - external-network

networks:
  char-art-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
  external-network:
    external: true
```

**Docker Run 网络配置：**

```bash
# 创建自定义网络
docker network create --driver bridge \
  --subnet=172.20.0.0/16 \
  char-art-network

# 连接到网络
docker run -d --name webp-processor \
  --network char-art-network \
  --ip 172.20.0.10 \
  webp-processor:latest
```

## 与其他服务集成

### 与后端服务集成

WebP处理服务需要与后端服务集成，为后端提供WebP动图处理功能支持。

#### 使用Docker Compose集成

在项目根目录下的 `docker-compose.yml` 文件中配置：

```yaml
services:
  webp-processor:
    build: ./python_webp_processor
    container_name: webp-processor
    ports:
      - "8081:8081"
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - JAVA_BACKEND_URL=http://char-art-backend:8080
    volumes:
      - webp-processor-data:/app/data
      - webp-processor-logs:/app/logs
    networks:
      - char-art-network
    depends_on:
      - redis
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  char-art-backend:
    build: ./backend
    container_name: char-art-backend
    ports:
      - "8080:8080"
    environment:
      - REDIS_HOST=redis
      - WEBP_PROCESSOR_URL=http://webp-processor:8081
      - WEBP_PROCESSOR_TIMEOUT=30000
    volumes:
      - char-art-data:/app/data
      - char-art-logs:/app/logs
    networks:
      - char-art-network
    depends_on:
      - redis
      - webp-processor
```

#### 使用Docker网络集成

```bash
# 创建共享网络
docker network create char-art-network

# 启动WebP处理服务
docker run -d --name webp-processor \
  --network char-art-network \
  -p 8081:8081 \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e JAVA_BACKEND_URL=http://char-art-backend:8080 \
  webp-processor:latest

# 启动后端服务
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  -e WEBP_PROCESSOR_URL=http://webp-processor:8081 \
  char-art-backend:latest
```

### 与前端服务集成

WebP处理服务通过后端服务间接为前端提供WebP动图处理功能。

#### 使用Docker Compose方式集成

```yaml
services:
  char-art-frontend:
    build: ./frontend
    container_name: char-art-frontend
    ports:
      - "80:80"
    environment:
      - VITE_API_URL=http://char-art-backend:8080
    networks:
      - char-art-network
    depends_on:
      - char-art-backend

  webp-processor:
    environment:
      - JAVA_BACKEND_URL=http://char-art-backend:8080
    depends_on:
      - char-art-backend
```

#### 使用Docker网络方式集成

```bash
# 启动前端服务
docker run -d --name char-art-frontend \
  --network char-art-network \
  -p 80:80 \
  -e VITE_API_URL=http://char-art-backend:8080 \
  char-art-frontend:latest

# WebP处理服务通过后端间接为前端提供服务
# 无需直接配置前端与WebP处理服务的连接
```

### 与Redis服务集成

WebP处理服务使用Redis进行任务队列管理和进度通知。

```yaml
services:
  redis:
    image: redis:6.2-alpine
    container_name: redis
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data
    networks:
      - char-art-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 30s
      timeout: 10s
      retries: 3

  webp-processor:
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_DATABASE=0
      - REDIS_CHANNEL=sse
    depends_on:
      - redis
```

### 完整的服务集成示例

```yaml
version: '3.8'

services:
  char-art-frontend:
    build: ./frontend
    container_name: char-art-frontend
    ports:
      - "80:80"
    environment:
      - VITE_API_URL=http://char-art-backend:8080
      - VITE_BASE_PATH=/
    volumes:
      - char-art-frontend-logs:/var/log/nginx
    networks:
      - char-art-network
    depends_on:
      - char-art-backend
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/"]
      interval: 30s
      timeout: 10s
      retries: 3

  char-art-backend:
    build: ./backend
    container_name: char-art-backend
    ports:
      - "8080:8080"
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - WEBP_PROCESSOR_URL=http://webp-processor:8081
      - WEBP_PROCESSOR_TIMEOUT=30000
    volumes:
      - char-art-data:/app/data
      - char-art-logs:/app/logs
    networks:
      - char-art-network
    depends_on:
      - redis
      - webp-processor
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  webp-processor:
    build: ./python_webp_processor
    container_name: webp-processor
    ports:
      - "8081:8081"
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_DATABASE=0
      - REDIS_CHANNEL=sse
      - JAVA_BACKEND_URL=http://char-art-backend:8080
    volumes:
      - webp-processor-data:/app/data
      - webp-processor-logs:/app/logs
    networks:
      - char-art-network
    depends_on:
      - redis
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  redis:
    image: redis:6.2-alpine
    container_name: redis
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data
    networks:
      - char-art-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  char-art-data:
    driver: local
  char-art-logs:
    driver: local
  char-art-frontend-logs:
    driver: local
  webp-processor-data:
    driver: local
  webp-processor-logs:
    driver: local
  redis-data:
    driver: local

networks:
  char-art-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## 自定义构建

### 修改Dockerfile

如果需要自定义 Docker 镜像构建过程，可以修改 `Dockerfile`：

```dockerfile
# 添加额外的系统依赖
RUN apt-get update && apt-get install -y \
    additional-package \
    imagemagick \
    && rm -rf /var/lib/apt/lists/*

# 安装额外的 Python 包
RUN pip install --no-cache-dir \
    additional-python-package \
    pillow-simd

# 设置自定义环境变量
ENV CUSTOM_CONFIG_PATH=/app/config
ENV PYTHONPATH=/app:$PYTHONPATH
```

### 修改启动脚本

`docker-entrypoint.sh` 脚本用于在容器启动时配置环境变量。如果需要添加更多自定义配置：

```bash
#!/bin/bash
set -e

# 添加自定义环境变量处理
if [ ! -z "$CUSTOM_CONFIG" ]; then
  sed -i "/^CUSTOM_CONFIG=/c\CUSTOM_CONFIG=$CUSTOM_CONFIG" "$ENV_FILE"
fi

# 添加自定义初始化逻辑
if [ "$ENABLE_CUSTOM_INIT" = "true" ]; then
  echo "Running custom initialization..."
  python /app/scripts/custom_init.py
fi

# 设置文件权限
chown -R app:app /app/data /app/logs

# 启动应用
exec python main.py
```

## 常见问题

### 1. Redis连接问题

**问题描述**: WebP 处理服务无法连接到 Redis

**解决方案**:

```bash
# 检查 Redis 服务状态
docker logs redis

# 测试 Redis 连接
docker exec -it webp-processor redis-cli -h redis ping

# 检查网络连接
docker exec -it webp-processor ping redis

# 验证环境变量
docker exec -it webp-processor env | grep REDIS
```

### 2. 文件上传大小限制

**问题描述**: 上传大文件时出现413错误

**解决方案**:

```bash
# 增加文件大小限制
docker run -d --name webp-processor \
  -e MAX_CONTENT_LENGTH=33554432 \
  webp-processor:latest

# 或在 docker-compose.yml 中配置
environment:
  - MAX_CONTENT_LENGTH=33554432  # 32MB
```

### 3. 临时文件清理

**问题描述**: 临时文件占用过多磁盘空间

**解决方案**:

```bash
# 手动清理临时文件
docker exec -it webp-processor rm -rf /app/data/*

# 调整清理间隔
docker run -d --name webp-processor \
  -e TEMP_FILE_TTL=1800 \
  webp-processor:latest

# 手动执行清理脚本
docker exec -it webp-processor python -c "from utils.utils import cleanup_temp_files; cleanup_temp_files()"
```

### 4. 健康检查失败

**问题描述**: 容器健康检查持续失败

**解决方案**:

```bash
# 查看容器状态
docker ps -a

# 查看详细日志
docker logs webp-processor

# 手动测试健康检查端点
curl -f http://localhost:8081/api/health

# 进入容器检查
docker exec -it webp-processor curl -f http://localhost:8081/api/health
```

### 5. 日志查看

**问题描述**: 需要查看详细的应用日志

**解决方案**:

```bash
# 查看容器日志
docker logs -f webp-processor
docker logs --tail=100 webp-processor

# 查看应用日志文件
docker exec -it webp-processor cat /app/logs/webp-processor.log
docker exec -it webp-processor tail -f /app/logs/webp-processor.log

# 启用调试模式
docker run -d --name webp-processor \
  -e DEBUG=True \
  -e LOG_LEVEL=DEBUG \
  webp-processor:latest
```

### 6. 停止和删除容器

**问题描述**: 需要完全清理容器和相关资源

**解决方案**:

```bash
# 停止容器
docker stop webp-processor

# 删除容器
docker rm webp-processor

# 删除镜像
docker rmi webp-processor:latest

# 清理未使用的卷
docker volume prune

# 清理未使用的网络
docker network prune

# 使用 Docker Compose 清理
docker-compose down -v --rmi all
```
