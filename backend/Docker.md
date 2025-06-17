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
      - [基本运行命令](#基本运行命令)
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
├── Dockerfile          # 用于构建后端服务的Docker镜像
├── docker-compose.yml  # 定义服务组合，包括后端和Redis
├── docker-entrypoint.sh # 容器启动脚本，用于自定义配置
├── run-docker.sh       # Linux/Mac快速启动脚本
├── run-docker.bat      # Windows快速启动脚本
└── src/                # 源代码目录
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

### 验证服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看后端服务日志
docker-compose logs -f char-art-backend
```

### 访问服务

服务启动后，可以通过 `http://localhost:8080/api/health` 检查服务健康状态。

### 停止服务

```bash
docker-compose down
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

#### Linux/Mac用户

```bash
# 添加执行权限
chmod +x run-docker.sh

# 运行脚本
./run-docker.sh
```

脚本执行完成后，服务将在 `http://localhost:8080/api` 上可用。

### 构建镜像

在项目根目录下执行以下命令构建Docker镜像：

```bash
docker build -t char-art-backend:latest .
```

### 运行容器

#### 基本运行命令

```bash
docker run -d --name char-art-backend -p 8080:8080 char-art-backend:latest
```

这将以默认配置启动字符画转换器后端服务，并将容器的8080端口映射到主机的8080端口。

#### 验证服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs char-art-backend
```

服务启动后，可以通过 `http://localhost:8080/api/health` 检查服务健康状态。

### 数据持久化

应用会在 `/app/data` 目录中存储临时数据。要持久化这些数据，可以使用卷挂载：

```bash
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest
```

#### 与Redis一起运行

如果需要在本地运行Redis服务，可以使用以下命令：

```bash
# 启动Redis容器
docker run -d --name redis \
  -v redis-data:/data \
  redis:6.2-alpine redis-server --appendonly yes

# 启动字符画转换器后端服务，并连接到Redis
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  --link redis:redis \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest
```

## 配置参数

### 环境变量

无论是使用 Docker Compose 还是 docker run 命令，都可以通过环境变量自定义以下配置：

#### Redis配置
- `REDIS_HOST`: Redis服务器地址 (Docker Compose默认: redis, Docker Run默认: localhost)
- `REDIS_PORT`: Redis服务器端口 (默认: 6379)
- `REDIS_DATABASE`: Redis数据库索引 (默认: 0)
- `REDIS_TIMEOUT`: Redis连接超时时间 (默认: 60000)

#### 字符画缓存配置
- `CHAR_ART_CACHE_TTL`: 缓存过期时间，单位秒 (默认: 3600)
- `CHAR_ART_CACHE_DEFAULT_KEY_PREFIX`: 缓存键前缀 (默认: char-art:text:)

#### WebP处理服务配置
- `WEBP_PROCESSOR_URL`: WebP处理服务URL (Docker Compose默认: http://webp-processor:8081, Docker Run默认: http://localhost:8081)
- `WEBP_PROCESSOR_ENABLED`: 是否启用WebP处理服务 (默认: true)
- `WEBP_PROCESSOR_CONNECTION_TIMEOUT`: 连接超时时间 (默认: 600000)
- `WEBP_PROCESSOR_MAX_RETRIES`: 最大重试次数 (默认: 2)

#### 服务器配置
- `SERVER_PORT`: 服务器端口 (默认: 8080)

#### 上传文件配置
- `MAX_FILE_SIZE`: 最大文件大小 (默认: 10MB)
- `MAX_REQUEST_SIZE`: 最大请求大小 (默认: 10MB)

#### 日志配置
- `LOG_LEVEL`: 日志级别 (默认: INFO)
- `LOG_FILE_MAX_SIZE`: 日志文件最大大小 (默认: 10MB)
- `LOG_FILE_MAX_HISTORY`: 日志文件保留历史数量 (默认: 30)

#### 字符画默认配置
- `DEFAULT_DENSITY`: 默认字符密度 (默认: medium)
- `DEFAULT_COLOR_MODE`: 默认颜色模式 (默认: grayscale)

## 自定义构建

### 修改Dockerfile

如果需要自定义Docker镜像构建过程，可以修改 `Dockerfile`。

### 修改启动脚本

`docker-entrypoint.sh` 脚本用于在容器启动时自定义 `application.properties` 中的参数。如果需要添加更多自定义配置，可以修改此脚本。

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

### 2. 文件上传大小限制

如果需要上传大文件，请调整 `MAX_FILE_SIZE` 和 `MAX_REQUEST_SIZE` 环境变量：

```bash
docker run -d --name char-art-backend \
  -p 8080:8080 \
  -e MAX_FILE_SIZE=50MB \
  -e MAX_REQUEST_SIZE=50MB \
  char-art-backend:latest
```

### 3. WebP处理服务

如果需要处理WebP动图，请确保WebP处理服务可用，并正确设置 `WEBP_PROCESSOR_URL`。

### 4. 数据持久化

应用数据和日志以及Redis数据存储在Docker卷中，确保不要意外删除这些卷：
- `char-art-data`: 应用数据
- `char-art-logs`: 应用日志
- `redis-data`: Redis数据

### 5. 日志查看

```bash
# 使用Docker Compose查看日志
docker-compose logs -f char-art-backend

# 使用Docker命令查看日志
docker logs -f char-art-backend

# 查看最近100行日志
docker logs --tail=100 char-art-backend
```

您也可以直接查看持久化的日志文件：

```bash
# 使用Docker Compose进入容器
docker-compose exec char-art-backend sh -c "cat /app/logs/char-art-converter.log"

# 使用Docker命令进入容器
docker exec char-art-backend sh -c "cat /app/logs/char-art-converter.log"

# 查看最近100行日志文件内容
docker exec char-art-backend sh -c "tail -n 100 /app/logs/char-art-converter.log"
```

### 6. 停止和删除容器

```bash
# 停止容器
docker stop char-art-backend

# 删除容器
docker rm char-art-backend
```