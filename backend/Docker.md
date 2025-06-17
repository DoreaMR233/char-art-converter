# 字符画转换器后端 Docker 部署指南

本文档提供了使用 Docker 部署字符画转换器后端服务的详细说明，包括 Docker Compose 和 docker run 两种部署方式。

## 目录

- [字符画转换器后端 Docker 部署指南](#字符画转换器后端-docker-部署指南)
  - [目录](#目录)
  - [前提条件](#前提条件)
  - [项目结构](#项目结构)
  - [使用 Docker Compose 部署](#使用-docker-compose-部署)
    - [构建并启动服务](#构建并启动服务)
    - [验证服务状态](#验证服务状态)
    - [访问服务](#访问服务)
    - [停止服务](#停止服务)
  - [使用 Docker Run 部署](#使用-docker-run-部署)
    - [快速启动](#快速启动)
      - [Windows用户](#windows用户)
      - [Linux/Mac用户](#linuxmac用户)
    - [构建镜像](#构建镜像)
    - [运行容器](#运行容器)
    - [验证服务状态](#验证服务状态-1)
  - [数据持久化](#数据持久化)
    - [与Redis一起运行](#与redis一起运行)
  - [配置参数](#配置参数)
    - [环境变量](#环境变量)
      - [Redis配置](#redis配置)
      - [字符画缓存配置](#字符画缓存配置)
      - [WebP处理服务配置](#webp处理服务配置)
      - [服务器配置](#服务器配置)
      - [上传文件配置](#上传文件配置)
      - [日志配置](#日志配置)
      - [字符画默认配置](#字符画默认配置)
  - [与其他服务集成](#与其他服务集成)
    - [与WebP处理服务集成](#与webp处理服务集成)
    - [与前端服务集成](#与前端服务集成)
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

```
./
├── Dockerfile            # 用于构建后端服务的Docker镜像
├── docker-compose.yml    # 定义服务组合，包括后端、WebP处理服务和Redis
├── docker-entrypoint.sh  # 容器启动脚本，用于自定义配置
├── run-docker.sh         # Linux/Mac快速启动脚本
├── run-docker.bat        # Windows快速启动脚本
└── src/                  # 源代码目录
    ├── main/             # 主要源代码
    │   ├── java/         # Java源代码
    │   └── resources/    # 资源文件
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

### 验证服务状态

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

### 快速启动

为了简化部署过程，我们提供了快速启动脚本。

#### Windows用户

双击运行 `run-docker.bat` 文件，该脚本将自动执行以下操作：

1. 构建Docker镜像
2. 停止并删除已存在的同名容器（如果有）
3. 启动新容器
4. 检查服务健康状态

```batch
@echo off
REM 构建Docker镜像
docker build -t char-art-backend:latest .

REM 停止并删除已存在的容器
docker stop char-art-backend 2>nul
docker rm char-art-backend 2>nul

REM 启动新容器
docker run -d --name char-art-backend -p 8080:8080 char-art-backend:latest

REM 等待服务启动
timeout /t 5

REM 检查服务健康状态
curl http://localhost:8080/api/health
```

#### Linux/Mac用户

```bash
# 添加执行权限
chmod +x run-docker.sh

# 运行脚本
./run-docker.sh
```

脚本内容示例：

```bash
#!/bin/bash
# 构建Docker镜像
docker build -t char-art-backend:latest .

# 停止并删除已存在的容器
docker stop char-art-backend 2>/dev/null
docker rm char-art-backend 2>/dev/null

# 启动新容器
docker run -d --name char-art-backend -p 8080:8080 char-art-backend:latest

# 等待服务启动
sleep 5

# 检查服务健康状态
curl http://localhost:8080/api/health
```

脚本执行完成后，服务将在 `http://localhost:8080/api` 上可用。

### 构建镜像

在项目根目录下执行以下命令构建Docker镜像：

```bash
docker build -t char-art-backend:latest .
```

### 运行容器

```bash
docker run -d --name char-art-backend -p 8080:8080 char-art-backend:latest
```

这将以默认配置启动字符画转换器后端服务，并将容器的8080端口映射到主机的8080端口。

### 验证服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs char-art-backend
```

服务启动后，可以通过 `http://localhost:8080/api/health` 检查服务健康状态。

## 数据持久化

应用会在 `/app/data` 目录中存储临时数据，在 `/app/logs` 目录中存储日志。要持久化这些数据，可以使用卷挂载：

```bash
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest
```

### 与Redis一起运行

如果需要在本地运行Redis服务，可以使用以下命令：

```bash
# 创建Docker网络
docker network create char-art-network

# 启动Redis容器
docker run -d --name redis \
  --network char-art-network \
  -v redis-data:/data \
  redis:6.2-alpine redis-server --appendonly yes

# 启动字符画转换器后端服务，并连接到Redis
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest
```

## 配置参数

### 环境变量

无论是使用 Docker Compose 还是 docker run 命令，都可以通过环境变量自定义以下配置：

#### Redis配置
- `REDIS_HOST`: Redis服务器地址 (Docker Compose默认: redis, Docker Run默认: localhost)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e REDIS_HOST=redis char-art-backend:latest
  ```

- `REDIS_PORT`: Redis服务器端口 (默认: 6379)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e REDIS_PORT=6380 char-art-backend:latest
  ```

- `REDIS_DATABASE`: Redis数据库索引 (默认: 0)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e REDIS_DATABASE=1 char-art-backend:latest
  ```

- `REDIS_TIMEOUT`: Redis连接超时时间，单位毫秒 (默认: 60000)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e REDIS_TIMEOUT=30000 char-art-backend:latest
  ```

#### 字符画缓存配置
- `CHAR_ART_CACHE_TTL`: 缓存过期时间，单位秒 (默认: 3600)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e CHAR_ART_CACHE_TTL=7200 char-art-backend:latest
  ```

- `CHAR_ART_CACHE_DEFAULT_KEY_PREFIX`: 缓存键前缀 (默认: char-art:text:)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=my-app:char-art: char-art-backend:latest
  ```

#### WebP处理服务配置
- `WEBP_PROCESSOR_URL`: WebP处理服务URL (Docker Compose默认: http://webp-processor:5000, Docker Run默认: http://localhost:8081)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e WEBP_PROCESSOR_URL=http://webp-processor:5000 char-art-backend:latest
  ```

- `WEBP_PROCESSOR_ENABLED`: 是否启用WebP处理服务 (默认: true)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e WEBP_PROCESSOR_ENABLED=false char-art-backend:latest
  ```

- `WEBP_PROCESSOR_CONNECTION_TIMEOUT`: 连接超时时间，单位毫秒 (默认: 600000)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e WEBP_PROCESSOR_CONNECTION_TIMEOUT=300000 char-art-backend:latest
  ```

- `WEBP_PROCESSOR_MAX_RETRIES`: 最大重试次数 (默认: 2)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e WEBP_PROCESSOR_MAX_RETRIES=3 char-art-backend:latest
  ```

#### 服务器配置
- `SERVER_PORT`: 服务器端口 (默认: 8080)
  ```bash
  docker run -d --name char-art-backend -p 9090:9090 -e SERVER_PORT=9090 char-art-backend:latest
  ```

#### 上传文件配置
- `MAX_FILE_SIZE`: 最大文件大小 (默认: 10MB)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e MAX_FILE_SIZE=20MB char-art-backend:latest
  ```

- `MAX_REQUEST_SIZE`: 最大请求大小 (默认: 10MB)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e MAX_REQUEST_SIZE=20MB char-art-backend:latest
  ```

#### 日志配置
- `LOG_LEVEL`: 日志级别 (DEBUG, INFO, WARNING, ERROR, CRITICAL) (默认: INFO)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e LOG_LEVEL=DEBUG char-art-backend:latest
  ```

- `LOG_FILE_MAX_SIZE`: 日志文件最大大小 (默认: 10MB)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e LOG_FILE_MAX_SIZE=20MB char-art-backend:latest
  ```

- `LOG_FILE_MAX_HISTORY`: 日志文件保留历史数量 (默认: 30)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e LOG_FILE_MAX_HISTORY=15 char-art-backend:latest
  ```

#### 字符画默认配置
- `DEFAULT_DENSITY`: 默认字符密度 (默认: medium)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e DEFAULT_DENSITY=high char-art-backend:latest
  ```

- `DEFAULT_COLOR_MODE`: 默认颜色模式 (默认: grayscale)
  ```bash
  docker run -d --name char-art-backend -p 8080:8080 -e DEFAULT_COLOR_MODE=color char-art-backend:latest
  ```

## 与其他服务集成

### 与WebP处理服务集成

字符画转换器后端服务需要与WebP处理服务集成，以支持WebP动图处理功能。在Docker环境中，可以通过以下方式集成：

1. 使用Docker Compose（推荐）：

```yaml
# docker-compose.yml 示例
version: '3.8'

services:
  char-art-backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - REDIS_HOST=redis
      - WEBP_PROCESSOR_URL=http://webp-processor:5000
    volumes:
      - char-art-data:/app/data
      - char-art-logs:/app/logs
    depends_on:
      - redis
      - webp-processor

  webp-processor:
    build: ../python_webp_processor
    ports:
      - "8081:5000"
    volumes:
      - webp-processor-data:/app/data
      - webp-processor-logs:/app/logs

  redis:
    image: redis:6.2-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data

volumes:
  char-art-data:
  char-art-logs:
  webp-processor-data:
  webp-processor-logs:
  redis-data:
```

2. 使用Docker网络：

```bash
# 创建Docker网络
docker network create char-art-network

# 启动WebP处理服务
docker run -d --name webp-processor \
  --network char-art-network \
  -p 8081:5000 \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  webp-processor:latest

# 启动后端服务，并连接到WebP处理服务
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  -e WEBP_PROCESSOR_URL=http://webp-processor:5000 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest
```

### 与前端服务集成

要将后端服务与前端服务集成，可以使用以下方法：

1. 使用Docker Compose（推荐）：

```yaml
# docker-compose.yml 示例
version: '3.8'

services:
  char-art-frontend:
    build: ../frontend
    ports:
      - "8081:80"
    environment:
      - API_URL=http://char-art-backend:8080
    depends_on:
      - char-art-backend

  char-art-backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - REDIS_HOST=redis
      - WEBP_PROCESSOR_URL=http://webp-processor:5000
    volumes:
      - char-art-data:/app/data
      - char-art-logs:/app/logs
    depends_on:
      - redis
      - webp-processor

  # 其他服务配置...

volumes:
  char-art-data:
  char-art-logs:
  redis-data:
  # 卷配置...
```

2. 使用Docker网络：

```bash
# 创建Docker网络
docker network create char-art-network

# 启动后端服务
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  char-art-backend:latest

# 启动前端服务，并连接到后端服务
docker run -d --name char-art-frontend \
  --network char-art-network \
  -p 8081:80 \
  -e API_URL=http://char-art-backend:8080 \
  char-art-frontend:latest
```

## 自定义构建

### 修改Dockerfile

如果需要自定义Docker镜像构建过程，可以修改 `Dockerfile`。例如，添加额外的依赖项或配置：

```dockerfile
# 基础镜像
FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 添加自定义依赖项
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制应用文件
COPY target/*.jar app.jar
COPY docker-entrypoint.sh /

# 设置权限
RUN chmod +x /docker-entrypoint.sh

# 创建数据和日志目录
RUN mkdir -p /app/data /app/logs

# 暴露端口
EXPOSE 8080

# 设置入口点
ENTRYPOINT ["/docker-entrypoint.sh"]
```

### 修改启动脚本

`docker-entrypoint.sh` 脚本用于在容器启动时自定义 `application.properties` 中的参数。如果需要添加更多自定义配置，可以修改此脚本：

```bash
#!/bin/sh
set -e

# 设置默认环境变量
SERVER_PORT=${SERVER_PORT:-8080}
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}

# 添加自定义配置
echo "Configuring application with SERVER_PORT=$SERVER_PORT, REDIS_HOST=$REDIS_HOST"

# 启动应用
exec java -jar \
  -Dserver.port=$SERVER_PORT \
  -Dspring.redis.host=$REDIS_HOST \
  -Dspring.redis.port=$REDIS_PORT \
  /app/app.jar
```

## 常见问题

### 1. Redis连接问题

如果遇到Redis连接问题，请检查以下配置：
- Redis服务是否正常运行
- `REDIS_HOST` 是否正确设置为Redis服务器的IP地址或容器名称
- 如果使用Docker Compose，确保 `REDIS_HOST` 设置为 `redis`
- 如果Redis在同一台主机上但不在Docker中运行，在Windows上可以使用 `host.docker.internal` 作为主机名

```bash
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -e REDIS_HOST=host.docker.internal \
  char-art-backend:latest
```

可以通过查看日志来诊断Redis连接问题：

```bash
docker logs char-art-backend | grep -i redis
```

### 2. 文件上传大小限制

如果需要上传大文件，请调整 `MAX_FILE_SIZE` 和 `MAX_REQUEST_SIZE` 环境变量：

```bash
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -e MAX_FILE_SIZE=50MB \
  -e MAX_REQUEST_SIZE=50MB \
  char-art-backend:latest
```

在Docker Compose中设置：

```yaml
services:
  char-art-backend:
    environment:
      - MAX_FILE_SIZE=50MB
      - MAX_REQUEST_SIZE=50MB
```

### 3. WebP处理服务

如果需要处理WebP动图，请确保WebP处理服务可用，并正确设置 `WEBP_PROCESSOR_URL`。

常见问题包括：
- WebP处理服务未启动或不可访问
- `WEBP_PROCESSOR_URL` 配置错误
- 网络连接问题

可以通过以下命令测试WebP处理服务是否可访问：

```bash
docker exec char-art-backend curl -s http://webp-processor:5000/api/health
```

### 4. 数据持久化

应用数据和日志以及Redis数据存储在Docker卷中，确保不要意外删除这些卷：
- `char-art-data`: 应用数据
- `char-art-logs`: 应用日志
- `redis-data`: Redis数据

查看现有卷：

```bash
docker volume ls | grep char-art
```

备份卷数据：

```bash
# 创建临时容器挂载卷并备份数据
docker run --rm -v char-art-data:/data -v $(pwd):/backup alpine tar -czvf /backup/char-art-data-backup.tar.gz /data
```

### 5. 日志查看

```bash
# 使用Docker Compose查看日志
docker-compose logs -f char-art-backend

# 使用Docker命令查看日志
docker logs -f char-art-backend

# 查看最近100行日志
docker logs --tail=100 char-art-backend

# 查看特定时间段的日志
docker logs --since=2023-01-01T00:00:00 --until=2023-01-02T00:00:00 char-art-backend
```

您也可以直接查看持久化的日志文件：

```bash
# 使用Docker Compose进入容器
docker-compose exec char-art-backend sh -c "cat /app/logs/char-art-converter.log"

# 使用Docker命令进入容器
docker exec char-art-backend sh -c "cat /app/logs/char-art-converter.log"

# 查看最近100行日志文件内容
docker exec char-art-backend sh -c "tail -n 100 /app/logs/char-art-converter.log"

# 实时查看日志文件更新
docker exec char-art-backend sh -c "tail -f /app/logs/char-art-converter.log"
```

### 6. 停止和删除容器

```bash
# 停止容器
docker stop char-art-backend

# 删除容器
docker rm char-art-backend

# 停止并删除容器（一步操作）
docker rm -f char-art-backend

# 停止并删除所有相关容器
docker-compose down
```