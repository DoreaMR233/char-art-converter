# 字符画转换器后端 Docker 部署指南

本文档提供了使用 Docker 部署字符画转换器后端服务的详细说明，包括 Docker Compose 和 docker run 两种部署方式。

## 目录

- [字符画转换器后端 Docker 部署指南](#字符画转换器后端-docker-部署指南)
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
    - [验证Docker Run服务状态](#验证docker-run服务状态)
  - [配置参数](#配置参数)
    - [配置文件变量](#配置文件变量)
      - [使用示例](#使用示例)
    - [卷](#卷)
      - [卷使用示例](#卷使用示例)
    - [网络](#网络)
      - [网络使用示例](#网络使用示例)
  - [与其他服务集成](#与其他服务集成)
    - [与前端服务集成](#与前端服务集成)
      - [使用Docker Compose方式集成](#使用docker-compose方式集成)
      - [使用Docker网络集成](#使用docker网络集成)
    - [与WebP处理服务集成](#与webp处理服务集成)
      - [使用Docker Compose集成](#使用docker-compose集成)
      - [使用Docker网络方式集成](#使用docker网络方式集成)
    - [与Redis服务集成](#与redis服务集成)
    - [完整的服务集成示例](#完整的服务集成示例)
  - [自定义构建](#自定义构建)
    - [修改Dockerfile](#修改dockerfile)
    - [修改启动脚本](#修改启动脚本)
  - [常见问题](#常见问题)
    - [1. Redis连接问题](#1-redis连接问题)
    - [2. 文件上传大小限制](#2-文件上传大小限制)
    - [3. WebP处理服务](#3-webp处理服务)
    - [4. 数据持久化](#4-数据持久化)
    - [5. 日志查看](#5-日志查看)
    - [6. 停止和删除容器](#6-停止和删除容器)

## 前提条件

- Docker 19.03.0+
- Docker Compose 1.27.0+ (如使用 Docker Compose 部署)
- Git (可选，用于克隆项目)

## 项目结构

``` text
./
├── Dockerfile            # 用于构建后端服务的Docker镜像
├── docker-compose.yml    # 定义服务组合，包括后端、WebP处理服务和Redis
├── docker-entrypoint.sh  # 容器启动脚本，用于自定义配置
├── pom.xml              # Maven项目配置文件
└── src/                  # 源代码目录
    ├── main/             # 主要源代码
    │   ├── java/         # Java源代码
    │   └── resources/    # 资源文件
    │       └── application.properties  # 应用配置文件
    └── test/             # 测试源代码
```

## 使用 Docker Compose 部署

### 构建并启动服务

```bash
# 在项目根目录下执行
docker-compose up -d
```

这将启动以下服务：

- `char-art-backend`: 字符画转换器后端服务
- `redis`: Redis缓存服务
- `webp-processor`: WebP处理服务（如果在docker-compose.yml中定义）

### 验证Docker Compose服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看后端服务日志
docker-compose logs -f char-art-backend
```

### 访问服务

服务启动后，可以通过 `http://localhost:8080/api/health` 检查服务健康状态。如果服务正常运行，将返回：

```json
{"status":"ok"}
```

### 停止服务

```bash
# 停止服务但保留容器
docker-compose stop

# 停止服务并删除容器
docker-compose down

# 停止服务并删除容器、网络和卷
docker-compose down -v
```

## 使用 Docker Run 部署

### 构建镜像

在项目根目录下执行以下命令构建Docker镜像：

```bash
docker build -t char-art-backend:latest .
```

### 运行容器

```bash
# 基本运行
docker run -d --name char-art-backend -p 8080:8080 char-art-backend:latest

# 带数据持久化的运行
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest
```

### 验证Docker Run服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs char-art-backend

# 检查服务健康状态
curl http://localhost:8080/api/health
```

## 配置参数

### 配置文件变量

以下是可通过环境变量配置的参数列表：

| 变量名称                                | 变量中文名      | 变量作用                 | 变量默认值                   |
|-------------------------------------|------------|----------------------|-------------------------|
| `SERVER_PORT`                       | 服务器端口      | 设置Spring Boot应用的监听端口 | `8080`                  |
| `REDIS_HOST`                        | Redis主机地址  | Redis服务器的主机名或IP地址    | `localhost`             |
| `REDIS_PORT`                        | Redis端口    | Redis服务器的端口号         | `6379`                  |
| `REDIS_DATABASE`                    | Redis数据库索引 | 使用的Redis数据库索引        | `0`                     |
| `REDIS_PASSWORD`                    | Redis密码    | Redis服务器的认证密码       | 空字符串                  |
| `REDIS_TIMEOUT`                     | Redis连接超时  | Redis连接超时时间（毫秒）      | `60000`                 |
| `CHAR_ART_CACHE_TTL`                | 缓存过期时间     | 字符画缓存的生存时间（秒）        | `3600`                  |
| `CHAR_ART_CACHE_DEFAULT_KEY_PREFIX` | 缓存键前缀      | 字符画缓存键的默认前缀          | `char-art:text:`        |
| `WEBP_PROCESSOR_URL`                | WebP处理服务地址 | WebP处理服务的URL地址       | `http://localhost:8081` |
| `WEBP_PROCESSOR_ENABLED`            | WebP处理服务开关 | 是否启用WebP处理服务         | `true`                  |
| `WEBP_PROCESSOR_CONNECTION_TIMEOUT` | WebP连接超时   | WebP处理服务连接超时时间（毫秒）   | `600000`                |
| `WEBP_PROCESSOR_MAX_RETRIES`        | WebP最大重试次数 | WebP处理服务的最大重试次数      | `2`                     |
| `MAX_FILE_SIZE`                     | 最大文件大小     | 上传文件的最大大小限制          | `10MB`                  |
| `MAX_REQUEST_SIZE`                  | 最大请求大小     | HTTP请求的最大大小限制        | `10MB`                  |
| `LOG_LEVEL`                         | 日志级别       | 应用程序的日志输出级别          | `INFO`                  |
| `LOG_FILE_MAX_SIZE`                 | 日志文件最大大小   | 单个日志文件的最大大小          | `10MB`                  |
| `LOG_FILE_MAX_HISTORY`              | 日志文件保留数量   | 保留的历史日志文件数量          | `30`                    |
| `LOG_CHARSET_CONSOLE`               | 控制台日志字符集   | 控制台输出日志的字符编码         | `UTF-8`                 |
| `LOG_CHARSET_FILE`                  | 文件日志字符集    | 日志文件的字符编码            | `UTF-8`                 |
| `TIMEZONE`                          | 应用时区       | 应用程序使用的时区设置          | `Asia/Shanghai`         |
| `DATETIME_FORMAT`                   | 日期时间格式     | 日期时间的显示格式            | `yyyy-MM-dd HH:mm:ss`   |
| `DEFAULT_DENSITY`                   | 默认字符密度     | 字符画转换的默认字符密度         | `medium`                |
| `DEFAULT_COLOR_MODE`                | 默认颜色模式     | 字符画转换的默认颜色模式         | `grayscale`             |
| `DEFAULT_CONFIG_PATH`               | 默认配置路径     | 应用配置文件的默认路径          | `/app/config`           |
| `DEFAULT_TEMP_PATH`                 | 默认临时文件路径   | 临时文件存储的默认路径          | `/app/data`             |
| `char-art.temp-directory`           | 字符画临时目录    | 字符画处理时的临时文件目录        | `./temp`                |
| `LOG_FILE_PATH`                     | 日志文件路径     | 日志文件存储的路径            | `/app/logs`             |
| `CHAR_ART_PARALLEL_MAX_FRAME_THREADS` | 最大并行帧数     | 同时处理的帧数上限            | `4`                     |
| `CHAR_ART_PARALLEL_THREAD_POOL_FACTOR` | 线程池大小计算因子  | CPU核心数的倍数             | `0.5`                   |
| `CHAR_ART_PARALLEL_MIN_THREADS`     | 最小线程数      | 线程池的最小线程数            | `1`                     |
| `CHAR_ART_PARALLEL_PROGRESS_UPDATE_INTERVAL` | 进度更新间隔     | 进度更新的时间间隔（毫秒）        | `500`                   |
| `CHAR_ART_PARALLEL_PIXEL_PROGRESS_INTERVAL` | 像素处理进度报告间隔 | 每处理多少像素报告一次进度        | `1000`                  |
| `CHAR_ART_PARALLEL_TASK_TIMEOUT`    | 任务执行超时时间   | 任务执行的超时时间（毫秒）        | `60000`                 |
| `CHAR_ART_PARALLEL_PROGRESS_CLEANUP_DELAY` | 进度监听器清理延迟  | 进度监听器清理的延迟时间（毫秒）     | `60000`                 |

#### 使用示例

```bash
# 使用自定义配置运行容器
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e REDIS_PASSWORD=your_redis_password \
  -e LOG_LEVEL=DEBUG \
  -e MAX_FILE_SIZE=20MB \
  -e WEBP_PROCESSOR_URL=http://webp-processor:5000 \
  char-art-backend:latest
```

### 卷

应用使用以下卷来持久化数据：

| 卷名称               | 容器内路径         | 用途   | 说明               |
|-------------------|---------------|------|------------------|
| `char-art-data`   | `/app/data`   | 数据存储 | 存储临时文件、上传文件等应用数据 |
| `char-art-logs`   | `/app/logs`   | 日志存储 | 存储应用运行日志文件       |
| `char-art-config` | `/app/config` | 配置存储 | 存储应用配置文件         |

#### 卷使用示例

```bash
# 创建命名卷
docker volume create char-art-data
docker volume create char-art-logs

# 使用命名卷运行容器
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest

# 使用主机目录挂载
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -v /host/path/data:/app/data \
  -v /host/path/logs:/app/logs \
  char-art-backend:latest
```

### 网络

应用支持以下网络配置：

| 网络类型 | 网络名称               | 用途         | 说明                   |
|------|--------------------|------------|----------------------|
| 桥接网络 | `char-art-network` | 服务间通信      | 连接后端、Redis、WebP处理服务等 |
| 主机网络 | `host`             | 直接访问主机网络   | 用于开发环境或特殊网络需求        |
| 默认网络 | `bridge`           | 默认Docker网络 | 单容器运行时使用             |

#### 网络使用示例

```bash
# 创建自定义网络
docker network create char-art-network

# 在自定义网络中运行Redis
docker run -d --name redis \
  --network char-art-network \
  redis:6.2-alpine

# 在同一网络中运行后端服务
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  char-art-backend:latest

# 查看网络信息
docker network inspect char-art-network
```

## 与其他服务集成

### 与前端服务集成

后端服务需要与前端服务集成，为前端提供API接口支持。

#### 使用Docker Compose方式集成

在项目根目录下的 `docker-compose.yml` 文件中配置：

```yaml
services:
  char-art-frontend:
    build: ./frontend
    container_name: char-art-frontend
    ports:
      - "80:80"
    environment:
      - VITE_API_URL=http://char-art-backend:8080
    volumes:
      - char-art-frontend-logs:/var/log/nginx
    networks:
      - char-art-network
    depends_on:
      - char-art-backend

  char-art-backend:
    build: ./backend
    container_name: char-art-backend
    ports:
      - "8080:8080"
    environment:
      - REDIS_HOST=redis
      - WEBP_PROCESSOR_URL=http://webp-processor:8081
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

# 启动后端服务
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  -e WEBP_PROCESSOR_URL=http://webp-processor:8081 \
  char-art-backend:latest

# 启动前端服务
docker run -d --name char-art-frontend \
  --network char-art-network \
  -p 80:80 \
  -e VITE_API_URL=http://char-art-backend:8080 \
  char-art-frontend:latest
```

### 与WebP处理服务集成

后端服务需要与WebP处理服务集成，以支持WebP动图处理功能。

#### 使用Docker Compose集成

```yaml
services:
  char-art-backend:
    environment:
      - WEBP_PROCESSOR_URL=http://webp-processor:8081
      - WEBP_PROCESSOR_TIMEOUT=30000
    depends_on:
      - webp-processor

  webp-processor:
    build: ./python_webp_processor
    container_name: webp-processor
    ports:
      - "8081:8081"
    environment:
      - REDIS_HOST=redis
      - JAVA_BACKEND_URL=http://char-art-backend:8080
    volumes:
      - webp-processor-data:/app/data
      - webp-processor-logs:/app/logs
    networks:
      - char-art-network
    depends_on:
      - redis
```

#### 使用Docker网络方式集成

```bash
# 启动WebP处理服务
docker run -d --name webp-processor \
  --network char-art-network \
  -p 8081:8081 \
  -e REDIS_HOST=redis \
  -e JAVA_BACKEND_URL=http://char-art-backend:8080 \
  webp-processor:latest

# 启动后端服务，并连接到WebP处理服务
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  -e WEBP_PROCESSOR_URL=http://webp-processor:8081 \
  -e REDIS_HOST=redis \
  char-art-backend:latest
```

### 与Redis服务集成

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

  char-art-backend:
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_DATABASE=0
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
    networks:
      - char-art-network
    depends_on:
      - char-art-backend

  char-art-backend:
    build: ./backend
    container_name: char-art-backend
    ports:
      - "8080:8080"
    environment:
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - WEBP_PROCESSOR_URL=http://webp-processor:8081
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
      - JAVA_BACKEND_URL=http://char-art-backend:8080
    volumes:
      - webp-processor-data:/app/data
      - webp-processor-logs:/app/logs
    networks:
      - char-art-network
    depends_on:
      - redis

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

如果需要自定义Docker镜像构建过程，可以修改 `Dockerfile`：

```dockerfile
# 基础镜像
FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制应用文件
COPY target/*.jar app.jar
COPY docker-entrypoint.sh /

# 设置权限
RUN chmod +x /docker-entrypoint.sh

# 创建必要的目录
RUN mkdir -p /app/data /app/logs /app/config

# 设置环境变量
ENV DEFAULT_CONFIG_PATH=/app/config \
    DEFAULT_TEMP_PATH=/app/data \
    LOG_FILE_PATH=/app/logs

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# 设置入口点
ENTRYPOINT ["/docker-entrypoint.sh"]
```

### 修改启动脚本

`docker-entrypoint.sh` 脚本用于在容器启动时自定义配置：

```bash
#!/bin/sh
set -e

# 配置文件路径
CONFIG_FILE="/app/config/application.properties"

# 如果配置文件不存在，从默认位置复制
if [ ! -f "$CONFIG_FILE" ]; then
    mkdir -p /app/config
    cp /app/BOOT-INF/classes/application.properties "$CONFIG_FILE"
fi

# 应用环境变量配置
echo "Applying configuration from environment variables..."

# Redis配置
[ ! -z "$REDIS_HOST" ] && sed -i "s|spring.redis.host=.*|spring.redis.host=$REDIS_HOST|g" "$CONFIG_FILE"
[ ! -z "$REDIS_PORT" ] && sed -i "s|spring.redis.port=.*|spring.redis.port=$REDIS_PORT|g" "$CONFIG_FILE"

# 服务器配置
[ ! -z "$SERVER_PORT" ] && sed -i "s|server.port=.*|server.port=$SERVER_PORT|g" "$CONFIG_FILE"

# 启动应用
echo "Starting application..."
exec java -jar \
  -Dspring.config.location=file:$CONFIG_FILE \
  -Djava.io.tmpdir=$DEFAULT_TEMP_PATH \
  /app/app.jar
```

## 常见问题

### 1. Redis连接问题

**问题描述**：应用无法连接到Redis服务

**解决方案**：

- 检查Redis服务是否正常运行
- 验证 `REDIS_HOST` 环境变量设置
- 确保网络连接正常

```bash
# 检查Redis连接
docker exec char-art-backend ping redis

# 查看Redis连接日志
docker logs char-art-backend | grep -i redis

# 测试Redis连接
docker exec char-art-backend redis-cli -h redis ping
```

### 2. 文件上传大小限制

**问题描述**：上传大文件时出现413错误

**解决方案**：调整文件大小限制

```bash
# 设置更大的文件上传限制
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -e MAX_FILE_SIZE=50MB \
  -e MAX_REQUEST_SIZE=50MB \
  char-art-backend:latest
```

### 3. WebP处理服务

**问题描述**：WebP动图处理失败

**解决方案**：

- 确保WebP处理服务正常运行
- 检查 `WEBP_PROCESSOR_URL` 配置
- 验证网络连接

```bash
# 测试WebP处理服务
curl http://webp-processor:5000/api/health

# 检查服务日志
docker logs webp-processor
```

### 4. 数据持久化

**问题描述**：容器重启后数据丢失

**解决方案**：正确配置数据卷

```bash
# 查看现有卷
docker volume ls

# 备份数据卷
docker run --rm -v char-art-data:/data -v $(pwd):/backup alpine \
  tar -czvf /backup/char-art-data-backup.tar.gz /data

# 恢复数据卷
docker run --rm -v char-art-data:/data -v $(pwd):/backup alpine \
  tar -xzvf /backup/char-art-data-backup.tar.gz -C /
```

### 5. 日志查看

**查看容器日志**：

```bash
# 实时查看日志
docker logs -f char-art-backend

# 查看最近100行日志
docker logs --tail=100 char-art-backend

# 查看特定时间段日志
docker logs --since=2023-01-01T00:00:00 --until=2023-01-02T00:00:00 char-art-backend
```

**查看应用日志文件**：

```bash
# 进入容器查看日志
docker exec -it char-art-backend sh
cat /app/logs/char-art-converter.log

# 直接查看日志文件
docker exec char-art-backend tail -f /app/logs/char-art-converter.log
```

### 6. 停止和删除容器

```bash
# 停止容器
docker stop char-art-backend

# 删除容器
docker rm char-art-backend

# 强制删除运行中的容器
docker rm -f char-art-backend

# 清理所有相关资源
docker-compose down -v
docker system prune -f
```
