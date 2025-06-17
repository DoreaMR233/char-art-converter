# WebP处理服务 Docker 部署指南

本文档提供了使用Docker部署WebP处理服务的详细说明。

## 目录

- [前提条件](#前提条件)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
  - [构建镜像](#构建镜像)
  - [启动服务](#启动服务)
  - [验证服务](#验证服务)
  - [停止服务](#停止服务)
- [配置说明](#配置说明)
  - [环境变量](#环境变量)
- [数据持久化](#数据持久化)
- [与后端服务集成](#与后端服务集成)
- [自定义构建](#自定义构建)
  - [修改Dockerfile](#修改dockerfile)
  - [修改启动脚本](#修改启动脚本)
- [常见问题](#常见问题)

## 前提条件

- 安装 [Docker](https://www.docker.com/get-started)
- 安装 [Docker Compose](https://docs.docker.com/compose/install/) (可选，用于多容器部署)

## 项目结构

```
python_webp_processor/
├── Dockerfile            # Docker镜像构建文件
├── docker-compose.yml    # Docker Compose配置文件
├── docker-entrypoint.sh  # 容器启动脚本
├── app.py               # 应用入口
├── config.py            # 配置文件
├── requirements.txt     # 依赖项
└── .env                 # 环境变量配置
```

## 快速开始

### 构建镜像

```bash
cd python_webp_processor
docker build -t webp-processor .
```

### 启动服务

使用Docker Compose启动服务：

```bash
docker-compose up -d
```

或者使用Docker命令启动：

```bash
docker run -d --name webp-processor \
  -p 8081:5000 \
  -v webp-processor-data:/app/data \
  -v webp-processor-logs:/app/logs \
  webp-processor
```

### 验证服务

服务启动后，可以通过访问健康检查接口验证服务是否正常运行：

```bash
curl http://localhost:8081/api/health
```

如果服务正常运行，将返回：

```json
{"status":"ok"}
```

### 停止服务

使用Docker Compose停止服务：

```bash
docker-compose down
```

或者使用Docker命令停止：

```bash
docker stop webp-processor
docker rm webp-processor
```

## 配置说明

### 环境变量

可以通过环境变量自定义WebP处理服务的配置。以下是可用的环境变量：

| 环境变量 | 描述 | 默认值 |
|---------|------|-------|
| PORT | 服务监听端口 | 5000 |
| LOG_LEVEL | 日志级别 (DEBUG, INFO, WARNING, ERROR, CRITICAL) | INFO |
| DEBUG | 调试模式 | False |
| MAX_CONTENT_LENGTH | 最大上传文件大小（字节） | 16777216 (16MB) |
| TEMP_FILE_TTL | 临时文件保留时间（秒） | 3600 (1小时) |

在`docker-compose.yml`中设置环境变量：

```yaml
services:
  webp-processor:
    environment:
      - PORT=5000
      - LOG_LEVEL=INFO
      - DEBUG=False
      - MAX_CONTENT_LENGTH=16777216
      - TEMP_FILE_TTL=3600
```

或者在Docker命令中设置：

```bash
docker run -d --name webp-processor \
  -p 8081:5000 \
  -e PORT=5000 \
  -e LOG_LEVEL=INFO \
  -e DEBUG=False \
  -e MAX_CONTENT_LENGTH=16777216 \
  -e TEMP_FILE_TTL=3600 \
  webp-processor
```

## 数据持久化

WebP处理服务使用两个数据卷来持久化数据：

- `/app/data`：用于存储临时文件
- `/app/logs`：用于存储日志文件

这些卷在`docker-compose.yml`中已经配置好：

```yaml
volumes:
  - webp-processor-data:/app/data
  - webp-processor-logs:/app/logs
```

## 与后端服务集成

要将WebP处理服务与字符画转换器后端服务集成，可以使用项目根目录下的`docker-compose.yml`文件，该文件配置了完整的服务栈，包括后端服务、WebP处理服务和Redis：

```bash
cd char-art-converter
docker-compose up -d
```

这将启动所有服务，并自动配置它们之间的通信。

## 自定义构建

### 修改Dockerfile

如果需要自定义Docker镜像，可以修改`Dockerfile`。例如，添加额外的依赖项：

```dockerfile
# 在构建阶段安装额外的依赖项
RUN pip install --no-cache-dir some-package
```

### 修改启动脚本

启动脚本`docker-entrypoint.sh`负责在容器启动时配置环境变量。如果需要自定义启动行为，可以修改此脚本。

## 常见问题

### 文件上传大小限制

如果遇到文件上传大小限制问题，可以增加`MAX_CONTENT_LENGTH`环境变量的值：

```yaml
environment:
  - MAX_CONTENT_LENGTH=33554432  # 32MB
```

### 日志查看

查看容器日志：

```bash
docker logs webp-processor
```

或者直接查看日志文件（如果挂载了日志卷）：

```bash
docker exec -it webp-processor ls -la /app/logs
docker exec -it webp-processor cat /app/logs/your-log-file.log
```

### 临时文件清理

临时文件会根据`TEMP_FILE_TTL`设置的时间自动清理。如果需要手动清理，可以执行：

```bash
docker exec -it webp-processor rm -rf /app/data/*
```

### 健康检查失败

如果健康检查失败，可能是服务未正常启动。检查日志：

```bash
docker logs webp-processor
```

确保服务内部端口（默认5000）与容器暴露的端口匹配。