# 字符画转换器 Docker 部署指南

本文档提供了使用 Docker 部署字符画转换器应用的详细说明，包括一体化 Dockerfile 和 Docker Compose 两种部署方式。

## 目录

- [字符画转换器 Docker 部署指南](#字符画转换器-docker-部署指南)
  - [目录](#目录)
  - [前提条件](#前提条件)
  - [项目结构](#项目结构)
  - [使用 Docker Compose 部署](#使用-docker-compose-部署)
    - [构建并启动服务](#构建并启动服务)
    - [验证服务状态](#验证服务状态)
    - [访问服务](#访问服务)
    - [停止服务](#停止服务)
  - [使用一体化 Dockerfile 部署](#使用一体化-dockerfile-部署)
    - [构建镜像](#构建镜像)
    - [运行容器](#运行容器)
    - [验证服务状态](#验证服务状态-1)
    - [访问服务](#访问服务-1)
  - [快速启动](#快速启动)
    - [Windows用户](#windows用户)
    - [Linux/Mac用户](#linuxmac用户)
  - [配置参数](#配置参数)
    - [环境变量](#环境变量)
      - [通用配置](#通用配置)
      - [后端服务配置](#后端服务配置)
      - [后端日志配置](#后端日志配置)
      - [Redis 配置](#redis-配置)
      - [字符画缓存配置](#字符画缓存配置)
      - [WebP 处理器配置](#webp-处理器配置)
      - [Python WebP处理器配置](#python-webp处理器配置)
      - [字符画默认配置](#字符画默认配置)
      - [前端配置](#前端配置)
  - [数据持久化](#数据持久化)
  - [服务间集成](#服务间集成)
    - [前端与后端集成](#前端与后端集成)
    - [后端与WebP处理器集成](#后端与webp处理器集成)
    - [后端与Redis集成](#后端与redis集成)
  - [常见问题](#常见问题)
    - [1. 文件上传大小限制](#1-文件上传大小限制)
    - [2. 日志查看](#2-日志查看)
    - [3. 临时文件清理](#3-临时文件清理)
    - [4. 健康检查](#4-健康检查)
    - [5. 停止和删除容器](#5-停止和删除容器)
    - [6. 应用无法访问](#6-应用无法访问)
    - [7. 注意事项](#7-注意事项)

## 前提条件

- Docker 19.03.0+
- Docker Compose 1.27.0+ (如使用 Docker Compose 部署)
- Git (可选，用于克隆项目)

## 项目结构

字符画转换器由三个主要组件组成：

1. **前端**：Vue 3 + Element Plus 构建的用户界面
2. **后端**：Spring Boot 应用，提供字符画转换核心功能
3. **WebP处理器**：Python Flask 应用，处理 WebP 格式图片

```
./
├── Dockerfile                # 一体化Dockerfile，包含所有组件
├── docker-compose.yml        # 定义服务组合
├── docker-start.sh           # 容器启动脚本
├── frontend/                 # 前端服务目录
│   ├── Dockerfile            # 前端服务的Dockerfile
│   ├── docker-compose.yml    # 前端服务的Docker Compose配置
│   ├── docker-entrypoint.sh  # 前端服务的容器启动脚本
│   └── src/                  # 前端源代码
├── backend/                  # 后端服务目录
│   ├── Dockerfile            # 后端服务的Dockerfile
│   ├── docker-compose.yml    # 后端服务的Docker Compose配置
│   ├── docker-entrypoint.sh  # 后端服务的容器启动脚本
│   └── src/                  # 后端源代码
└── python_webp_processor/    # WebP处理服务目录
    ├── Dockerfile            # WebP处理服务的Dockerfile
    ├── docker-compose.yml    # WebP处理服务的Docker Compose配置
    ├── docker-entrypoint.sh  # WebP处理服务的容器启动脚本
    ├── app.py                # 应用入口
    ├── config.py             # 配置文件
    └── api/                  # API模块目录
```

## 使用 Docker Compose 部署

项目根目录已提供 `docker-compose.yml` 文件，可以使用 Docker Compose 启动所有服务。

### 构建并启动服务

```bash
# 在项目根目录下执行
docker-compose up -d
```

这将启动所有必要的服务，包括：
- `char-art-frontend`: 前端服务
- `char-art-backend`: 后端服务
- `webp-processor`: WebP处理服务
- `redis`: Redis服务（用于缓存）

### 验证服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f char-art-frontend
docker-compose logs -f char-art-backend
docker-compose logs -f webp-processor
```

### 访问服务

服务启动后，可以通过 `http://localhost:80` 访问应用。如果配置了 `BASE_PATH` 环境变量，则应通过 `http://localhost:80/{BASE_PATH}` 访问。

### 停止服务

```bash
# 停止所有服务但不删除容器
docker-compose stop

# 停止并删除所有容器
docker-compose down

# 停止并删除所有容器及卷（会删除所有数据）
docker-compose down -v
```

## 使用一体化 Dockerfile 部署

项目根目录提供了一个一体化的 Dockerfile，可以构建包含所有组件的单一镜像。这种方式适合开发和测试环境。

### 构建镜像

```bash
# 在项目根目录下执行
docker build -t char-art-converter:latest .
```

### 运行容器

```bash
docker run -d \
  --name char-art-app \
  -p 80:80 \
  char-art-converter:latest
```

自定义配置示例：

```bash
docker run -d \
  --name char-art-app \
  -p 80:80 \
  -e BASE_PATH="app" \
  -e LOG_LEVEL="DEBUG" \
  -e MAX_FILE_SIZE="20MB" \
  -e MAX_REQUEST_SIZE="20MB" \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-converter:latest
```

注意：内部服务（后端API和WebP处理器）不对外暴露，只能通过前端接口访问。

### 验证服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs char-art-app

# 查看最近100行日志
docker logs --tail=100 char-art-app

# 实时查看日志
docker logs -f char-art-app
```

### 访问服务

服务启动后，可以通过 `http://localhost:80` 访问应用。如果配置了 `BASE_PATH` 环境变量，则应通过 `http://localhost:80/{BASE_PATH}` 访问。

## 手动部署

### 构建镜像

在项目根目录下执行以下命令构建Docker镜像：

```bash
docker build -t char-art-converter:latest .
```

### 运行容器

```bash
# 停止并删除已存在的容器（如果有）
docker rm -f char-art-app 2>/dev/null || true

# 启动新容器
docker run -d --name char-art-app -p 80:80 char-art-converter:latest

# 等待服务启动
sleep 5

# 检查服务状态
docker ps | grep char-art-app
```

服务启动后，将在 `http://localhost:80` 上可用。

## 配置参数

### 环境变量

无论是使用一体化 Dockerfile 还是 Docker Compose，都可以通过环境变量自定义以下配置：

#### 通用配置

- `LOG_LEVEL`: 日志级别，默认为 `INFO`

  ```bash
  docker run -d --name char-art-app -p 80:80 -e LOG_LEVEL=DEBUG char-art-converter:latest
  ```

#### 后端服务配置

- `MAX_FILE_SIZE`: 最大文件大小，默认为 `10MB`

  ```bash
  docker run -d --name char-art-app -p 80:80 -e MAX_FILE_SIZE=20MB char-art-converter:latest
  ```

- `MAX_REQUEST_SIZE`: 最大请求大小，默认为 `10MB`

  ```bash
  docker run -d --name char-art-app -p 80:80 -e MAX_REQUEST_SIZE=20MB char-art-converter:latest
  ```

#### 后端日志配置

- `LOG_LEVEL`: 日志级别 (默认: INFO)
- `LOG_FILE_MAX_SIZE`: 日志文件最大大小 (默认: 10MB)
- `LOG_FILE_MAX_HISTORY`: 日志文件保留历史数量 (默认: 30)

  ```bash
  docker run -d --name char-art-app -p 80:80 \
    -e LOG_LEVEL=DEBUG \
    -e LOG_FILE_MAX_SIZE=20MB \
    -e LOG_FILE_MAX_HISTORY=10 \
    char-art-converter:latest
  ```

#### Redis 配置

- `REDIS_DATABASE`: Redis 数据库索引，默认为 `0`
- `REDIS_TIMEOUT`: Redis 超时时间（毫秒），默认为 `60000`
  
  ```bash
  docker run -d --name char-art-app -p 80:80 \
    -e REDIS_DATABASE=1 \
    -e REDIS_TIMEOUT=30000 \
    char-art-converter:latest
  ```

#### 字符画缓存配置

- `CHAR_ART_CACHE_TTL`: 缓存过期时间，单位秒 (默认: 3600)
- `CHAR_ART_CACHE_DEFAULT_KEY_PREFIX`: 缓存键前缀 (默认: char-art:text:)
  
  ```bash
  docker run -d --name char-art-app -p 80:80 \
    -e CHAR_ART_CACHE_TTL=7200 \
    -e CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=char-art:custom: \
    char-art-converter:latest
  ```

#### WebP 处理器配置

- `WEBP_PROCESSOR_CONNECTION_TIMEOUT`: 连接超时时间（毫秒），默认为 `5000`
- `WEBP_PROCESSOR_MAX_RETRIES`: 最大重试次数，默认为 `2`

  ```bash
  docker run -d --name char-art-app -p 80:80 \
    -e WEBP_PROCESSOR_CONNECTION_TIMEOUT=10000 \
    -e WEBP_PROCESSOR_MAX_RETRIES=3 \
    char-art-converter:latest
  ```

#### Python WebP处理器配置

- `LOG_LEVEL`: 日志级别，默认为 `INFO`
- `DEBUG`: 是否开启调试模式，默认为 `False`
- `MAX_CONTENT_LENGTH`: 最大内容长度（字节），默认为 `16777216`（16MB）
- `TEMP_FILE_TTL`: 临时文件存活时间（秒），默认为 `3600`（1小时）

  ```bash
  docker run -d --name char-art-app -p 80:80 \
    -e DEBUG=True \
    -e MAX_CONTENT_LENGTH=33554432 \
    -e TEMP_FILE_TTL=7200 \
    char-art-converter:latest
  ```

#### 字符画默认配置

- `DEFAULT_DENSITY`: 默认字符密度，默认为 `medium`
- `DEFAULT_COLOR_MODE`: 默认颜色模式，默认为 `grayscale`

  ```bash
  docker run -d --name char-art-app -p 80:80 \
    -e DEFAULT_DENSITY=high \
    -e DEFAULT_COLOR_MODE=color \
    char-art-converter:latest
  ```

#### 前端配置

- `BASE_PATH`: 前端资源路径前缀，默认为空（根路径）

  例如，如果您的应用部署在 `http://example.com/char-art/`，则应设置 `BASE_PATH=char-art`

  ```bash
  docker run -d --name char-art-app -p 80:80 -e BASE_PATH=char-art char-art-converter:latest
  ```

注意：以下环境变量使用默认配置且不可修改：
- `REDIS_HOST`: 默认为 `localhost`
- `REDIS_PORT`: 默认为 `6379`
- `WEBP_PROCESSOR_URL`: 默认为 `http://localhost:5000`
- `WEBP_PROCESSOR_ENABLED`: 默认为 `true`
- `BACKEND_PORT`: 默认为 `8080`
- `WEBP_PROCESSOR_PORT`: 默认为 `5000`
- `FRONTEND_PORT`: 默认为 `80`

## 数据持久化

应用数据和日志存储在Docker卷中，确保不要意外删除这些卷：

- `char-art-data`: 应用数据
- `char-art-logs`: 应用日志
- `redis-data`: Redis数据
- `webp-processor-data`: WebP处理器临时文件
- `webp-processor-logs`: WebP处理器日志

使用一体化Dockerfile时，可以挂载以下目录：

```bash
docker run -d \
  --name char-art-app \
  -p 80:80 \
  -v char-art-data:/app/backend/data \
  -v char-art-logs:/app/backend/logs \
  -v redis-data:/app/redis/data \
  -v webp-processor-data:/app/webp-processor/data \
  -v webp-processor-logs:/app/webp-processor/logs \
  char-art-converter:latest
```

使用 Docker Compose 时，卷会自动创建：

```yaml
volumes:
  char-art-data:
  char-art-logs:
  redis-data:
  webp-processor-data:
  webp-processor-logs:
```

查看现有卷：

```bash
docker volume ls | grep char-art
```

备份卷数据：

```bash
# 创建临时容器挂载卷并备份数据
docker run --rm -v char-art-data:/data -v $(pwd):/backup alpine tar -czvf /backup/char-art-data-backup.tar.gz /data
```

## 服务间集成

### 前端与后端集成

前端应用需要与后端API通信。在一体化部署中，这已经配置好了。如果您使用分离部署，需要确保：

1. 前端的 `API_URL` 环境变量指向后端服务
2. 如果使用Docker网络，前端容器可以通过容器名访问后端容器

```bash
# 创建Docker网络
docker network create char-art-network

# 启动后端服务并连接到网络
docker run -d --name char-art-backend --network char-art-network -p 8080:8080 char-art-backend:latest

# 启动前端服务并连接到网络，设置API_URL指向后端容器名
docker run -d --name char-art-frontend --network char-art-network -p 80:80 -e API_URL=http://char-art-backend:8080 char-art-frontend:latest
```

### 后端与WebP处理器集成

后端服务需要与WebP处理器通信。在一体化部署中，这已经配置好了。如果您使用分离部署，需要确保：

1. 后端的 `WEBP_PROCESSOR_URL` 环境变量指向WebP处理器服务
2. 如果使用Docker网络，后端容器可以通过容器名访问WebP处理器容器

```bash
# 启动WebP处理器并连接到网络
docker run -d --name webp-processor --network char-art-network -p 5000:5000 webp-processor:latest

# 启动后端服务并连接到网络，设置WEBP_PROCESSOR_URL指向WebP处理器容器名
docker run -d --name char-art-backend --network char-art-network -p 8080:8080 -e WEBP_PROCESSOR_URL=http://webp-processor:5000 char-art-backend:latest
```

### 后端与Redis集成

后端服务需要与Redis通信。在一体化部署中，这已经配置好了。如果您使用分离部署，需要确保：

1. 后端的 `REDIS_HOST` 环境变量指向Redis服务
2. 如果使用Docker网络，后端容器可以通过容器名访问Redis容器

```bash
# 启动Redis并连接到网络
docker run -d --name redis --network char-art-network -p 6379:6379 -v redis-data:/data redis:6.2-alpine redis-server --appendonly yes

# 启动后端服务并连接到网络，设置REDIS_HOST指向Redis容器名
docker run -d --name char-art-backend --network char-art-network -p 8080:8080 -e REDIS_HOST=redis char-art-backend:latest
```

## 常见问题

### 1. 文件上传大小限制

如果需要上传大文件，请调整以下环境变量：

- 后端服务：`MAX_FILE_SIZE` 和 `MAX_REQUEST_SIZE`
- WebP处理器：`MAX_CONTENT_LENGTH`

```bash
docker run -d \
  --name char-art-app \
  -p 80:80 \
  -e MAX_FILE_SIZE=50MB \
  -e MAX_REQUEST_SIZE=50MB \
  -e MAX_CONTENT_LENGTH=52428800 \
  char-art-converter:latest
```

或者在 `docker-compose.yml` 中设置：

```yaml
services:
  char-art-app:
    environment:
      - MAX_FILE_SIZE=50MB
      - MAX_REQUEST_SIZE=50MB
      - MAX_CONTENT_LENGTH=52428800  # 50MB
```

### 2. 日志查看

```bash
# 使用Docker命令查看日志
docker logs -f char-art-app

# 查看最近100行日志
docker logs --tail=100 char-art-app

# 查看特定时间段的日志
docker logs --since=2023-01-01T00:00:00 --until=2023-01-02T00:00:00 char-art-app

# 使用Docker Compose查看日志
docker-compose logs -f
```

您也可以直接查看持久化的日志文件：

```bash
# 使用Docker命令进入容器查看后端日志文件内容
docker exec char-art-app sh -c "cat /app/backend/logs/char-art-converter.log"

# 查看最近100行后端日志文件内容
docker exec char-art-app sh -c "tail -n 100 /app/backend/logs/char-art-converter.log"

# 实时查看后端日志文件更新
docker exec char-art-app sh -c "tail -f /app/backend/logs/char-art-converter.log"

# 查看WebP处理器日志文件内容
docker exec char-art-app sh -c "cat /app/webp-processor/logs/webp-processor.log"
```

### 3. 临时文件清理

临时文件会根据配置的 `TEMP_FILE_TTL` 自动清理。如果需要手动清理，可以执行：

```bash
# 清理后端临时文件
docker exec char-art-app sh -c "rm -rf /app/backend/data/temp/*"

# 清理WebP处理器临时文件
docker exec char-art-app sh -c "rm -rf /app/webp-processor/data/*"
```

### 4. 健康检查

检查各服务健康状态：

```bash
# 检查后端服务健康状态
curl http://localhost:8080/api/health
# 预期输出: {"status":"UP"}

# 检查WebP处理器健康状态
curl http://localhost:5000/api/health
# 预期输出: {"status":"ok"}
```

如果健康检查失败，请查看日志以诊断问题。

### 5. 停止和删除容器

```bash
# 停止容器
docker stop char-art-app

# 删除容器
docker rm char-art-app

# 停止并删除容器（一步操作）
docker rm -f char-art-app

# 使用Docker Compose停止并删除所有容器
docker-compose down
```

### 6. 应用无法访问

如果应用无法访问，请检查：
- 容器是否正常运行 `docker ps`
- 端口映射是否正确 `docker port char-art-app`
- 查看容器日志是否有错误 `docker logs char-art-app`
- 如果配置了 `BASE_PATH`，确保访问URL包含该路径
- 检查网络连接是否正常 `docker network inspect char-art-network`

### 7. 注意事项

- 一体化 Dockerfile 适合开发和测试环境，生产环境建议使用 Docker Compose 分别部署各个服务
- 默认配置使用内置 Redis 服务，生产环境建议使用外部 Redis 服务
- 内部服务（后端API和WebP处理器）不对外暴露，只能通过前端接口访问
- 确保为持久化数据配置适当的卷挂载，避免数据丢失
- 定期备份重要数据，特别是在更新容器或镜像之前