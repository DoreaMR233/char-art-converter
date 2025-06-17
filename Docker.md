# 字符画转换器 Docker 部署指南

本文档提供了使用 Docker 部署字符画转换器应用的详细说明，包括一体化 Dockerfile 和 Docker Compose 两种部署方式。

## 目录

- [字符画转换器 Docker 部署指南](#字符画转换器-docker-部署指南)
  - [目录](#目录)
  - [前提条件](#前提条件)
  - [项目结构](#项目结构)
  - [使用一体化 Dockerfile 部署（推荐）](#使用一体化-dockerfile-部署推荐)
    - [构建镜像](#构建镜像)
    - [运行容器](#运行容器)
    - [验证服务状态](#验证服务状态)
    - [访问服务](#访问服务)
  - [使用 Docker Compose 部署](#使用-docker-compose-部署)
    - [构建并启动服务](#构建并启动服务)
    - [验证服务状态](#验证服务状态-1)
    - [访问服务](#访问服务-1)
    - [停止服务](#停止服务)
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
  - [常见问题](#常见问题)
    - [1. 文件上传大小限制](#1-文件上传大小限制)
    - [2. 日志查看](#2-日志查看)
    - [3. 停止和删除容器](#3-停止和删除容器)
    - [4. 应用无法访问](#4-应用无法访问)
    - [5. 注意事项](#5-注意事项)

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
├── Dockerfile          # 一体化Dockerfile，包含所有组件
├── docker-compose.yml  # 定义服务组合
├── docker-start.sh     # 容器启动脚本
├── run-all-docker.sh   # Linux/Mac快速启动脚本
├── run-all-docker.bat  # Windows快速启动脚本
├── frontend/           # 前端源代码目录
├── backend/            # 后端源代码目录
└── webp-processor/     # WebP处理器源代码目录
```

## 使用一体化 Dockerfile 部署（推荐）

项目根目录提供了一个一体化的 Dockerfile，可以构建包含所有组件的单一镜像。

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
  char-art-converter:latest
```

注意：内部服务（后端API和WebP处理器）不对外暴露，只能通过前端接口访问。

### 验证服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs char-art-app
```

### 访问服务

服务启动后，可以通过 `http://localhost:80` 访问应用。

## 使用 Docker Compose 部署

项目根目录已提供 `docker-compose.yml` 文件，可以使用 Docker Compose 启动所有服务。

### 构建并启动服务

```bash
# 在项目根目录下执行
docker-compose up -d
```

这将启动所有必要的服务。

### 验证服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

### 访问服务

服务启动后，可以通过 `http://localhost:80` 访问应用。

### 停止服务

```bash
docker-compose down
```

## 快速启动

为了简化部署过程，我们提供了快速启动脚本。

### Windows用户

双击运行 `run-all-docker.bat` 文件，该脚本将自动执行以下操作：

1. 构建Docker镜像
2. 停止并删除已存在的同名容器（如果有）
3. 启动新容器
4. 检查服务健康状态

### Linux/Mac用户

```bash
# 添加执行权限
chmod +x run-all-docker.sh

# 运行脚本
./run-all-docker.sh
```

脚本执行完成后，服务将在 `http://localhost:80` 上可用。

## 配置参数

### 环境变量

无论是使用一体化 Dockerfile 还是 Docker Compose，都可以通过环境变量自定义以下配置：

#### 通用配置

- `LOG_LEVEL`: 日志级别，默认为 `INFO`

#### 后端服务配置

- `MAX_FILE_SIZE`: 最大文件大小，默认为 `10MB`
- `MAX_REQUEST_SIZE`: 最大请求大小，默认为 `10MB`

#### 后端日志配置

- `LOG_LEVEL`: 日志级别 (默认: INFO)
- `LOG_FILE_MAX_SIZE`: 日志文件最大大小 (默认: 10MB)
- `LOG_FILE_MAX_HISTORY`: 日志文件保留历史数量 (默认: 30)

#### Redis 配置

- `REDIS_DATABASE`: Redis 数据库索引，默认为 `0`
- `REDIS_TIMEOUT`: Redis 超时时间（毫秒），默认为 `60000`
  
#### 字符画缓存配置

- `CHAR_ART_CACHE_TTL`: 缓存过期时间，单位秒 (默认: 3600)
- `CHAR_ART_CACHE_DEFAULT_KEY_PREFIX`: 缓存键前缀 (默认: char-art:text:)
  
#### WebP 处理器配置

- `WEBP_PROCESSOR_CONNECTION_TIMEOUT`: 连接超时时间（毫秒），默认为 `5000`
- `WEBP_PROCESSOR_MAX_RETRIES`: 最大重试次数，默认为 `2`

#### Python WebP处理器配置

- `LOG_LEVEL`: 日志级别，默认为 `INFO`
- `DEBUG`: 是否开启调试模式，默认为 `False`
- `MAX_CONTENT_LENGTH`: 最大内容长度（字节），默认为 `16777216`（16MB）
- `TEMP_FILE_TTL`: 临时文件存活时间（秒），默认为 `3600`（1小时）

#### 字符画默认配置

- `DEFAULT_DENSITY`: 默认字符密度，默认为 `medium`
- `DEFAULT_COLOR_MODE`: 默认颜色模式，默认为 `grayscale`

#### 前端配置

- `BASE_PATH`: 前端资源路径前缀，默认为空（根路径）

注意：以下环境变量使用默认配置且不可修改：
- `REDIS_HOST`: 默认为 `localhost`
- `REDIS_PORT`: 默认为 `6379`
- `WEBP_PROCESSOR_URL`: 默认为 `http://localhost:5000`
- `WEBP_PROCESSOR_ENABLED`: 默认为 `true`
- `BACKEND_PORT`: 默认为 `8080`
- `WEBP_PROCESSOR_PORT`: 默认为 `5000`
- `FRONTEND_PORT`: 默认为 `80`

## 数据持久化

如果需要持久化数据，可以挂载以下目录：

```bash
docker run -d \
  --name char-art-app \
  -p 80:80 \
  -v /path/to/redis-data:/data \
  -v /path/to/backend-data:/app/backend/data \
  -v /path/to/backend-log:/app/backend/log \
  -v /path/to/webp-processor-data:/app/webp-processor/data \
  -v /path/to/webp-processor-logs:/app/webp-processor/logs \
  char-art-converter:latest
```

使用 Docker Compose 时，可以在 `docker-compose.yml` 文件中配置卷挂载。

## 常见问题

### 1. 文件上传大小限制

如果需要上传大文件，请调整 `MAX_FILE_SIZE` 和 `MAX_REQUEST_SIZE` 环境变量：

```bash
docker run -d \
  --name char-art-app \
  -p 80:80 \
  -e MAX_FILE_SIZE=50MB \
  -e MAX_REQUEST_SIZE=50MB \
  char-art-converter:latest
```

### 2. 日志查看

```bash
# 使用Docker命令查看日志
docker logs -f char-art-app

# 查看最近100行日志
docker logs --tail=100 char-art-app

# 使用Docker Compose查看日志
docker-compose logs -f
```

您也可以直接查看持久化的日志文件：

```bash
# 使用Docker Compose进入容器查看后端日志文件内容
docker-compose exec char-art-app sh -c "cat /app/backend/data/char-art-converter.log"

# 使用Docker命令进入容器查看后端日志文件内容
docker exec char-art-app sh -c "cat /app/backend/data/char-art-converter.log"

# 查看最近100行后端日志文件内容
docker exec char-art-app sh -c "tail -n 100 /app/backend/data/char-art-converter.log"

# 使用Docker Compose进入容器查看WebP处理器日志文件内容
docker-compose exec char-art-app sh -c "cat /app/webp-processor/data/webp-processor.log"

# 使用Docker命令进入容器查看WebP处理器日志文件内容
docker exec char-art-app sh -c "cat /app/webp-processor/data/char-art-converter.log"

# 查看最近100行后端日志文件内容
docker exec char-art-app sh -c "tail -n 100 /app/webp-processor/data/char-art-converter.log"
```

### 3. 停止和删除容器

```bash
# 停止容器
docker stop char-art-app

# 删除容器
docker rm char-art-app
```

### 4. 应用无法访问

如果应用无法访问，请检查：
- 容器是否正常运行 `docker ps`
- 端口映射是否正确 `docker port char-art-app`
- 查看容器日志是否有错误 `docker logs char-art-app`

### 5. 注意事项

- 一体化 Dockerfile 适合开发和测试环境，生产环境建议使用 Docker Compose 分别部署各个服务
- 默认配置使用内置 Redis 服务，生产环境建议使用外部 Redis 服务
- 内部服务（后端API和WebP处理器）不对外暴露，只能通过前端接口访问