# WebP处理服务 Docker 部署指南

本文档提供了使用 Docker 部署 WebP 处理服务的详细说明，包括 Docker Compose 和 docker run 两种部署方式。

## 目录

- [WebP处理服务 Docker 部署指南](#webp处理服务-docker-部署指南)
  - [目录](#目录)
  - [前提条件](#前提条件)
  - [项目结构](#项目结构)
  - [使用 Docker Compose 部署](#使用-docker-compose-部署)
    - [构建并启动服务](#构建并启动服务)
    - [验证服务状态](#验证服务状态)
    - [访问服务](#访问服务)
    - [停止服务](#停止服务)
  - [使用 Docker Run 部署](#使用-docker-run-部署)
    - [构建镜像](#构建镜像)
    - [运行容器](#运行容器)
    - [验证容器服务状态](#验证容器服务状态)
  - [配置参数](#配置参数)
    - [环境变量](#环境变量)
      - [服务器配置](#服务器配置)
      - [日志配置](#日志配置)
      - [应用配置](#应用配置)
  - [数据持久化](#数据持久化)
  - [与后端服务集成](#与后端服务集成)
  - [自定义构建](#自定义构建)
    - [修改Dockerfile](#修改dockerfile)
    - [修改启动脚本](#修改启动脚本)
  - [常见问题](#常见问题)
    - [1. 文件上传大小限制](#1-文件上传大小限制)
    - [2. 日志查看](#2-日志查看)
    - [3. 临时文件清理](#3-临时文件清理)
    - [4. 健康检查失败](#4-健康检查失败)
    - [5. 停止和删除容器](#5-停止和删除容器)

## 前提条件

- Docker 19.03.0+
- Docker Compose 1.27.0+ (如使用 Docker Compose 部署)
- Git (可选，用于克隆项目)

## 项目结构

``` text
./
├── Dockerfile            # 用于构建WebP处理服务的Docker镜像
├── docker-compose.yml    # 定义服务组合
├── docker-entrypoint.sh  # 容器启动脚本，用于配置环境变量
├── app.py                # 应用入口
├── config.py             # 配置文件
├── requirements.txt      # 依赖项
└── api/                  # API模块目录
    ├── __init__.py
    ├── health.py         # 健康检查接口
    └── webp.py           # WebP处理接口
```

## 使用 Docker Compose 部署

### 构建并启动服务

```bash
# 在项目根目录下执行
docker-compose up -d
```

这将启动 `webp-processor` 服务，并将容器的5000端口映射到主机的8081端口。

### 验证服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看WebP处理服务日志
docker-compose logs -f webp-processor
```

### 访问服务

服务启动后，可以通过 `http://localhost:8081/api/health` 检查服务健康状态。如果服务正常运行，将返回：

```json
{"status":"ok"}
```

### 停止服务

```bash
docker-compose down
```

## 使用 Docker Run 部署

### 构建镜像

在项目根目录下执行以下命令构建Docker镜像：

```bash
docker build -t webp-processor:latest .
```

### 运行容器

```bash
docker run -d \
  --name webp-processor \
  --network char-art-network \
  -p 8081:5000 \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  -e PORT=5000 \
  -e LOG_LEVEL=INFO \
  -e DEBUG=False \
  -e MAX_CONTENT_LENGTH=16777216 \
  -e TEMP_FILE_TTL=3600 \
  webp-processor:latest
```

这将以默认配置启动WebP处理服务，并将容器的5000端口映射到主机的8081端口。

### 验证容器服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs webp-processor
```

服务启动后，可以通过 `http://localhost:8081/api/health` 检查服务健康状态。

## 配置参数

### 环境变量

无论是使用 Docker Compose 还是 docker run 命令，都可以通过环境变量自定义以下配置：

#### 服务器配置

- `PORT`: 服务监听端口 (默认: 5000)

  例如，如果您想让服务监听在8000端口，则应设置 `PORT=8000`

  ```bash
  docker run -d --name webp-processor -p 8081:8000 -e PORT=8000 webp-processor:latest
  ```

#### 日志配置

- `LOG_LEVEL`: 日志级别 (DEBUG, INFO, WARNING, ERROR, CRITICAL) (默认: INFO)

  例如，如果您想启用调试日志，则应设置 `LOG_LEVEL=DEBUG`

  ```bash
  docker run -d --name webp-processor -p 8081:5000 -e LOG_LEVEL=DEBUG webp-processor:latest
  ```

#### 应用配置

- `DEBUG`: 调试模式 (默认: False)

  在调试模式下，应用会提供更详细的错误信息，但不建议在生产环境中启用

  ```bash
  docker run -d --name webp-processor -p 8081:5000 -e DEBUG=True webp-processor:latest
  ```

- `MAX_CONTENT_LENGTH`: 最大上传文件大小（字节） (默认: 16777216，即16MB)

  例如，如果您需要处理更大的文件，可以将此值设置为32MB

  ```bash
  docker run -d --name webp-processor -p 8081:5000 -e MAX_CONTENT_LENGTH=33554432 webp-processor:latest
  ```

- `TEMP_FILE_TTL`: 临时文件保留时间（秒） (默认: 3600，即1小时)

  例如，如果您想将临时文件保留时间延长到2小时，则应设置 `TEMP_FILE_TTL=7200`

  ```bash
  docker run -d --name webp-processor -p 8081:5000 -e TEMP_FILE_TTL=7200 webp-processor:latest
  ```

## 数据持久化

WebP处理服务使用两个数据卷来持久化数据：

- `webp-processor-data`: 用于存储临时文件，挂载到容器的 `/app/data` 目录
- `webp-processor-logs`: 用于存储日志文件，挂载到容器的 `/app/logs` 目录

使用Docker Compose时，这些卷会自动创建：

```yaml
volumes:
  webp-processor-data:
  webp-processor-logs:
```

使用Docker命令时，需要手动指定卷挂载：

```bash
docker run -d --name webp-processor \
  -p 8081:5000 \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  webp-processor:latest
```

## 与后端服务集成

要将WebP处理服务与字符画转换器后端服务集成，可以使用项目根目录下的`docker-compose.yml`文件，该文件配置了完整的服务栈，包括后端服务、WebP处理服务和Redis：

```bash
# 在项目根目录下执行
docker-compose up -d
```

或者，如果您使用Docker命令部署，可以创建一个共享网络，并将所有服务连接到该网络：

```bash
# 创建Docker网络
docker network create char-art-network

# 启动Redis服务
docker run -d --name redis \
  --network char-art-network \
  -v redis-data:/data \
  redis:6.2-alpine redis-server --appendonly yes

# 启动WebP处理服务
docker run -d --name webp-processor \
  --network char-art-network \
  -p 8081:5000 \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  webp-processor:latest

# 启动后端服务
docker run -d --name char-art-backend \
  --network char-art-network \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  -e WEBP_PROCESSOR_URL=http://webp-processor:5000 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-backend:latest
```

## 自定义构建

### 修改Dockerfile

如果需要自定义Docker镜像构建过程，可以修改 `Dockerfile`。例如，添加额外的依赖项：

```dockerfile
# 在构建阶段安装额外的依赖项
RUN pip install --no-cache-dir some-package
```

### 修改启动脚本

`docker-entrypoint.sh` 脚本用于在容器启动时配置环境变量。如果需要添加更多自定义配置，可以修改此脚本。

## 常见问题

### 1. 文件上传大小限制

如果遇到文件上传大小限制问题，可以增加 `MAX_CONTENT_LENGTH` 环境变量的值：

```bash
docker run -d --name webp-processor \
  -p 8081:5000 \
  -e MAX_CONTENT_LENGTH=33554432 \
  webp-processor:latest
```

或者在 `docker-compose.yml` 中设置：

```yaml
services:
  webp-processor:
    environment:
      - MAX_CONTENT_LENGTH=33554432  # 32MB
```

### 2. 日志查看

查看容器日志：

```bash
# 使用Docker Compose查看日志
docker-compose logs -f webp-processor

# 使用Docker命令查看日志
docker logs -f webp-processor

# 查看最近100行日志
docker logs --tail=100 webp-processor
```

或者直接查看日志文件（如果挂载了日志卷）：

```bash
docker exec -it webp-processor ls -la /app/logs
docker exec -it webp-processor cat /app/logs/your-log-file.log
```

### 3. 临时文件清理

临时文件会根据 `TEMP_FILE_TTL` 设置的时间自动清理。如果需要手动清理，可以执行：

```bash
docker exec -it webp-processor rm -rf /app/data/*
```

### 4. 健康检查失败

如果健康检查失败，可能是服务未正常启动。检查日志：

```bash
docker logs webp-processor
```

确保服务内部端口（默认5000）与容器暴露的端口匹配。如果您修改了 `PORT` 环境变量，请确保容器映射的端口也相应调整。

### 5. 停止和删除容器

```bash
# 停止容器
docker stop webp-processor

# 删除容器
docker rm webp-processor
```
