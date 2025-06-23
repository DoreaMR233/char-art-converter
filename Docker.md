# 字符画转换器 Docker 部署指南

本文档提供了使用 Docker 部署字符画转换器应用的详细说明，包括一体化 Dockerfile 和 Docker Compose 两种部署方式。

## 目录

- [字符画转换器 Docker 部署指南](#字符画转换器-docker-部署指南)
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
      - [通用配置](#通用配置)
      - [后端服务配置](#后端服务配置)
      - [前端服务配置](#前端服务配置)
      - [WebP处理器配置](#webp处理器配置)
      - [Redis配置](#redis配置)
      - [日志配置](#日志配置)
      - [字符画配置](#字符画配置)
    - [卷](#卷)
    - [网络](#网络)
  - [与其他服务集成](#与其他服务集成)
    - [外部Redis集成](#外部redis集成)
    - [负载均衡集成](#负载均衡集成)
    - [监控系统集成](#监控系统集成)
  - [自定义构建](#自定义构建)
    - [修改Dockerfile](#修改dockerfile)
    - [修改启动脚本](#修改启动脚本)
    - [自定义配置文件](#自定义配置文件)
  - [常见问题](#常见问题)
    - [1. 服务启动失败](#1-服务启动失败)
    - [2. 文件上传大小限制](#2-文件上传大小限制)
    - [3. 日志查看](#3-日志查看)
    - [4. 数据持久化](#4-数据持久化)
    - [5. 性能优化](#5-性能优化)
    - [6. 健康检查](#6-健康检查)
    - [7. 停止和删除容器](#7-停止和删除容器)

## 前提条件

在开始之前，请确保您的系统已安装以下软件：

- **Docker** 20.10.0 或更高版本
- **Docker Compose** 2.0.0 或更高版本（如使用 Docker Compose 部署）
- **Git**（可选，用于克隆项目）
- 至少 **2GB** 可用内存
- 至少 **5GB** 可用磁盘空间

## 项目结构

字符画转换器是一个多服务应用，由以下组件组成：

1. **前端服务**：Vue 3 + Element Plus 构建的用户界面
2. **后端服务**：Spring Boot 应用，提供字符画转换核心功能
3. **WebP处理器**：Python Flask 应用，专门处理 WebP 格式图片
4. **Redis服务**：缓存服务，提高性能
5. **Nginx服务**：反向代理和静态文件服务

``` text
char-art-converter/
├── Dockerfile                # 一体化Dockerfile，包含所有组件
├── docker-compose.yml        # Docker Compose配置文件
├── docker-start.sh           # 容器启动脚本
├── supervisord.conf          # 进程管理配置
├── nginx.conf.template       # Nginx配置模板
├── frontend/                 # 前端服务目录
│   ├── Dockerfile            # 前端独立Dockerfile
│   ├── docker-compose.yml    # 前端Docker Compose配置
│   ├── docker-entrypoint.sh  # 前端启动脚本
│   ├── nginx.conf            # 前端Nginx配置
│   ├── .env                  # 前端环境变量
│   ├── .env.development      # 前端开发环境变量
│   ├── .env.production       # 前端生产环境变量
│   └── src/                  # 前端源代码
├── backend/                  # 后端服务目录
│   ├── Dockerfile            # 后端独立Dockerfile
│   ├── docker-compose.yml    # 后端Docker Compose配置
│   ├── docker-entrypoint.sh  # 后端启动脚本
│   ├── application.properties.template  # 后端配置模板
│   └── src/                  # 后端源代码
└── python_webp_processor/    # WebP处理服务目录
    ├── Dockerfile            # WebP处理器独立Dockerfile
    ├── docker-compose.yml    # WebP处理器Docker Compose配置
    ├── docker-entrypoint.sh  # WebP处理器启动脚本
    ├── .env.template         # WebP处理器环境变量模板
    ├── app.py                # 应用入口
    ├── config.py             # 配置文件
    └── api/                  # API模块目录
```

## 使用 Docker Compose 部署

推荐使用 Docker Compose 进行部署，它会自动处理服务间的依赖关系和网络配置。

### 构建并启动服务

```bash
# 在项目根目录下执行
docker-compose up -d

# 查看构建过程（可选）
docker-compose up --build

# 仅构建镜像不启动
docker-compose build
```

这将启动以下服务：

- `char-art-app`: 一体化应用容器（包含前端、后端、WebP处理器、Nginx）
- `redis`: Redis缓存服务

### 验证Docker Compose服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f char-art-app
docker-compose logs -f redis

# 查看最近100行日志
docker-compose logs --tail=100 char-art-app
```

### 访问服务

服务启动后，可以通过以下方式访问：

- **前端应用**: `http://localhost:80`
- **后端API**: `http://localhost:80/api`
- **健康检查**: `http://localhost:80/api/health`
- **WebP处理器**: `http://localhost:80/webp/api/health`

### 停止服务

```bash
# 停止服务但保留容器
docker-compose stop

# 停止服务并删除容器
docker-compose down

# 停止服务并删除容器、网络和卷
docker-compose down -v

# 停止服务并删除容器、网络、卷和镜像
docker-compose down -v --rmi all
```

## 使用 Docker Run 部署

如果不使用 Docker Compose，也可以使用 docker run 命令手动部署。

### 构建镜像

```bash
# 在项目根目录下构建一体化镜像
docker build -t char-art-converter:latest .

# 使用自定义标签构建
docker build -t char-art-converter:v1.0.0 .

# 带构建参数的构建
docker build \
  --build-arg VITE_BASE_PATH=charart \
  --build-arg BACKEND_PORT=8080 \
  --build-arg API_URL=http://localhost:80/api \
  -t char-art-converter:latest .
```

### 运行容器

```bash
# 创建Docker网络
docker network create char-art-network

# 启动Redis服务
docker run -d --name redis \
  --network char-art-network \
  -v redis-data:/data \
  redis:6.2-alpine redis-server --appendonly yes

# 启动字符画转换器应用
docker run -d --name char-art-app \
  --network char-art-network \
  -p 80:80 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e TIMEZONE=Asia/Shanghai \
  char-art-converter:latest

# 带完整配置的运行
docker run -d --name char-art-app \
  --network char-art-network \
  -p 80:80 \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  -v char-art-redis-logs:/var/log/redis \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e SERVER_PORT=8080 \
  -e WEBP_PROCESSOR_PORT=8081 \
  -e MAX_UPLOAD_SIZE=20MB \
  -e BACKEND_LOG_LEVEL=INFO \
  -e WEBP_PROCESSOR_LOG_LEVEL=INFO \
  -e TIMEZONE=Asia/Shanghai \
  char-art-converter:latest
```

### 验证Docker Run服务状态

```bash
# 查看容器状态
docker ps

# 查看应用容器日志
docker logs -f char-art-app

# 查看Redis容器日志
docker logs -f redis

# 测试服务可用性
curl -f http://localhost:80/api/health
curl -f http://localhost:80/webp/api/health

# 测试前端页面
curl -I http://localhost:80
```

## 配置参数

### 配置文件变量

#### 通用配置

| 变量名称              | 变量中文名    | 变量作用          | 变量默认值           |
|-------------------|----------|---------------|-----------------|
| `TIMEZONE`        | 应用时区     | 设置应用程序使用的时区   | `Asia/Shanghai` |
| `PORT`            | 主服务端口    | 前端Nginx服务监听端口 | `80`            |
| `MAX_UPLOAD_SIZE` | 最大上传文件大小 | 限制上传文件的最大大小   | `10MB`          |

#### 后端服务配置

| 变量名称                    | 变量中文名    | 变量作用              | 变量默认值                              |
|-------------------------|----------|-------------------|------------------------------------|
| `SERVER_PORT`           | 后端服务端口   | Spring Boot应用监听端口 | `8080`                             |
| `BACKEND_PATH`          | 后端服务路径   | 后端服务部署路径          | `/app/backend`                     |
| `DEFAULT_CONFIG_PATH`   | 默认配置路径   | 后端配置文件路径          | `/app/config`                      |
| `BACKEND_LOG_LEVEL`     | 后端日志级别   | 后端服务日志输出级别        | `INFO`                             |
| `BACKEND_LOG_FILE_PATH` | 后端日志文件路径 | 后端日志文件存储路径        | `/app/logs/char-art-converter.log` |
| `LOG_FILE_MAX_SIZE`     | 日志文件最大大小 | 单个日志文件的最大大小       | `10MB`                             |
| `LOG_FILE_MAX_HISTORY`  | 日志文件保留数量 | 保留的历史日志文件数量       | `30`                               |
| `LOG_CHARSET_CONSOLE`   | 控制台日志字符集 | 控制台输出日志的字符编码      | `UTF-8`                            |
| `LOG_CHARSET_FILE`      | 文件日志字符集  | 日志文件的字符编码         | `UTF-8`                            |
| `DATETIME_FORMAT`       | 日期时间格式   | 日期时间的显示格式         | `yyyy-MM-dd HH:mm:ss`              |

#### 前端服务配置

| 变量名称                 | 变量中文名    | 变量作用         | 变量默认值                   |
|----------------------|----------|--------------|-------------------------|
| `FRONTEND_PATH`      | 前端服务路径   | 前端服务部署路径     | `/app/frontend`         |
| `VITE_BASE_PATH`     | 前端资源路径前缀 | 前端应用部署的子路径   | 空字符串                    |
| `VITE_API_URL`       | API服务器地址 | 前端调用的后端API地址 | `http://localhost:8080` |
| `VITE_API_BASE_PATH` | API基础路径  | API请求的基础路径   | `/api`                  |
| `VITE_APP_TITLE`     | 应用标题     | 前端应用显示的标题    | `字符画转换器`                |

#### WebP处理器配置

| 变量名称                                | 变量中文名         | 变量作用                | 变量默认值                          |
|-------------------------------------|---------------|---------------------|--------------------------------|
| `WEBP_PROCESSOR`                    | WebP处理器路径     | WebP处理器服务部署路径       | `/app/webp-processor`          |
| `TEMP_DIR_PATH`                     | WebP处理器临时文件目录 | WebP处理器临时文件存储路径     | `/app/webp-processor/data`     |
| `PORT`                              | WebP处理器端口     | WebP处理器服务监听端口       | `8081`                         |
| `WEBP_PROCESSOR_URL`                | WebP处理器URL    | 后端调用WebP处理器的URL     | `http://localhost:8081`        |
| `WEBP_PROCESSOR_ENABLED`            | WebP处理器开关     | 是否启用WebP处理服务        | `true`                         |
| `WEBP_PROCESSOR_CONNECTION_TIMEOUT` | WebP连接超时      | WebP处理器连接超时时间（毫秒）   | `600000`                       |
| `WEBP_PROCESSOR_MAX_RETRIES`        | WebP最大重试次数    | WebP处理器的最大重试次数      | `2`                            |
| `WEBP_PROCESSOR_LOG_LEVEL`          | WebP处理器日志级别   | WebP处理器日志输出级别       | `INFO`                         |
| `WEBP_LOG_FILE_PATH`                | WebP日志文件路径    | WebP处理器日志文件路径       | `/app/logs/webp_processor.log` |
| `JAVA_BACKEND_URL`                  | Java后端URL     | WebP处理器回调Java后端的URL | `http://localhost:8080`        |
| `MAX_CONTENT_LENGTH`                | 最大内容长度        | WebP处理器接受的最大请求大小    | `10485760`                     |
| `TEMP_FILE_TTL`                     | 临时文件生存时间      | 临时文件的生存时间（秒）        | `3600`                         |
| `DEBUG`                             | 调试模式          | 是否启用调试模式            | `False`                        |

#### Redis配置

| 变量名称             | 变量中文名      | 变量作用              | 变量默认值               |
|------------------|------------|-------------------|---------------------|
| `REDIS_HOST`     | Redis主机地址  | Redis服务器的主机名或IP地址 | `localhost`         |
| `REDIS_PORT`     | Redis端口    | Redis服务器的端口号      | `6379`              |
| `REDIS_DATABASE` | Redis数据库索引 | 使用的Redis数据库索引     | `0`                 |
| `REDIS_TIMEOUT`  | Redis连接超时  | Redis连接超时时间（毫秒）   | `60000`             |
| `REDIS_CHANNEL`  | Redis通道    | Redis发布订阅通道名称     | `char-art-progress` |

#### 日志配置

| 变量名称                      | 变量中文名             | 变量作用                 | 变量默认值                    |
|---------------------------|-------------------|----------------------|--------------------------|
| `SUPERVISORD_LOG_PATH`    | Supervisord日志路径   | Supervisord进程管理器日志路径 | `/var/log`               |
| `SUPERVISORD_PID_PATH`    | Supervisord PID路径 | Supervisord进程ID文件路径  | `/var/run`               |
| `SUPERVISORD_CONFIG_PATH` | Supervisord配置路径   | Supervisord配置文件路径    | `/etc/supervisor/conf.d` |

#### 字符画配置

| 变量名称                                | 变量中文名     | 变量作用          | 变量默认值            |
|-------------------------------------|-----------|---------------|------------------|
| `CHAR_ART_CACHE_TTL`                | 字符画缓存过期时间 | 字符画缓存的生存时间（秒） | `3600`           |
| `CHAR_ART_CACHE_DEFAULT_KEY_PREFIX` | 字符画缓存键前缀  | 字符画缓存键的默认前缀   | `char-art:text:` |
| `DEFAULT_DENSITY`                   | 默认字符密度    | 字符画转换的默认字符密度  | `medium`         |
| `DEFAULT_COLOR_MODE`                | 默认颜色模式    | 字符画转换的默认颜色模式  | `grayscale`      |

### 卷

应用使用以下数据卷进行数据持久化：

| 卷名称                | 容器内路径         | 用途             | 推荐大小  |
|--------------------|---------------|----------------|-------|
| `char-art-data`    | `/app/data`   | 存储临时文件和用户上传的图片 | 5GB+  |
| `char-art-logs`    | `/app/logs`   | 存储应用日志文件       | 1GB+  |
| `char-art-config`  | `/app/config` | 存储配置文件         | 100MB |
| `redis-data`       | `/data`       | Redis数据持久化     | 1GB+  |
| `supervisord-logs` | `/var/log`    | Supervisord日志  | 500MB |

**卷使用示例：**

```bash
# 创建命名卷
docker volume create char-art-data
docker volume create char-art-logs
docker volume create redis-data

# 使用绑定挂载
docker run -d --name char-art-app \
  -v /host/data:/app/data \
  -v /host/logs:/app/logs \
  -v /host/config:/app/config \
  char-art-converter:latest

# 查看卷信息
docker volume ls
docker volume inspect char-art-data
```

### 网络

应用支持以下网络配置：

| 网络类型      | 网络名称               | 用途     | 配置说明               |
|-----------|--------------------|--------|--------------------|
| `bridge`  | `char-art-network` | 服务间通信  | 默认桥接网络，支持服务发现      |
| `host`    | `host`             | 主机网络模式 | 直接使用主机网络，性能更好      |
| `overlay` | `char-art-overlay` | 跨主机通信  | 用于Docker Swarm集群部署 |

**网络使用示例：**

```bash
# 创建自定义网络
docker network create --driver bridge char-art-network

# 创建带子网的网络
docker network create \
  --driver bridge \
  --subnet=172.20.0.0/16 \
  --ip-range=172.20.240.0/20 \
  char-art-network

# 使用主机网络模式
docker run -d --name char-art-app \
  --network host \
  char-art-converter:latest

# 查看网络信息
docker network ls
docker network inspect char-art-network
```

## 与其他服务集成

### 外部Redis集成

如果您已有Redis服务，可以配置应用连接到外部Redis：

```bash
# 使用外部Redis
docker run -d --name char-art-app \
  -p 80:80 \
  -e REDIS_HOST=your-redis-host \
  -e REDIS_PORT=6379 \
  -e REDIS_DATABASE=0 \
  -e REDIS_TIMEOUT=60000 \
  char-art-converter:latest
```

**Docker Compose配置：**

```yaml
version: '3.8'
services:
  char-art-app:
    build: .
    ports:
      - "80:80"
    environment:
      - REDIS_HOST=external-redis.example.com
      - REDIS_PORT=6379
      - REDIS_DATABASE=1
    external_links:
      - external-redis
```

### 负载均衡集成

使用Nginx或HAProxy进行负载均衡：

```nginx
# nginx.conf
upstream char-art-backend {
    server char-art-app1:80;
    server char-art-app2:80;
    server char-art-app3:80;
}

server {
    listen 80;
    server_name char-art.example.com;
    
    location / {
        proxy_pass http://char-art-backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 监控系统集成

集成Prometheus和Grafana进行监控：

```yaml
# docker-compose.monitoring.yml
version: '3.8'
services:
  char-art-app:
    build: .
    ports:
      - "80:80"
    labels:
      - "prometheus.io/scrape=true"
      - "prometheus.io/port=8080"
      - "prometheus.io/path=/actuator/prometheus"
  
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
  
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

## 自定义构建

### 修改Dockerfile

您可以根据需要修改Dockerfile来自定义构建过程：

```dockerfile
# 自定义基础镜像
FROM ubuntu:22.04

# 添加自定义软件包
RUN apt-get update && apt-get install -y \
    your-custom-package \
    && rm -rf /var/lib/apt/lists/*

# 自定义Java版本
RUN apt-get update && apt-get install -y openjdk-17-jre-headless

# 自定义Python版本
RUN apt-get update && apt-get install -y python3.10 python3.10-pip

# 添加自定义配置
COPY custom-config/ /app/config/

# 设置自定义环境变量
ENV CUSTOM_VAR=value
```

### 修改启动脚本

自定义`docker-start.sh`脚本来添加额外的初始化逻辑：

```bash
#!/bin/bash

# 添加自定义初始化逻辑
echo "Starting custom initialization..."

# 检查外部依赖
if ! ping -c 1 external-service.example.com &> /dev/null; then
    echo "Warning: External service is not reachable"
fi

# 设置自定义权限
chown -R app:app /app/data
chmod 755 /app/data

# 运行原始启动脚本
exec /app/start.sh "$@"
```

### 自定义配置文件

创建自定义配置文件模板：

```properties
# custom-application.properties.template
server.port=%(ENV_SERVER_PORT)s
spring.application.name=char-art-converter-custom

# 自定义数据源配置
spring.datasource.url=jdbc:mysql://%(ENV_DB_HOST)s:%(ENV_DB_PORT)s/%(ENV_DB_NAME)s
spring.datasource.username=%(ENV_DB_USER)s
spring.datasource.password=%(ENV_DB_PASSWORD)s

# 自定义缓存配置
spring.cache.type=redis
spring.redis.host=%(ENV_REDIS_HOST)s
spring.redis.port=%(ENV_REDIS_PORT)s
spring.redis.database=%(ENV_REDIS_DATABASE)s
```

## 常见问题

### 1. 服务启动失败

**问题描述**：容器启动后立即退出或服务无法访问

**解决方案**：

```bash
# 查看容器日志
docker logs char-art-app

# 查看详细启动日志
docker-compose logs -f char-art-app

# 检查容器状态
docker ps -a

# 进入容器调试
docker exec -it char-art-app /bin/bash

# 检查端口占用
netstat -tulpn | grep :80

# 检查磁盘空间
df -h

# 检查内存使用
free -h
```

**常见原因**：

- 端口被占用
- 磁盘空间不足
- 内存不足
- 配置文件错误
- 依赖服务未启动

### 2. 文件上传大小限制

**问题描述**：上传大文件时出现413错误或上传失败

**解决方案**：

```bash
# 调整上传大小限制
docker run -d --name char-art-app \
  -e MAX_UPLOAD_SIZE=50MB \
  -e MAX_CONTENT_LENGTH=52428800 \
  char-art-converter:latest

# 或在docker-compose.yml中配置
environment:
  - MAX_UPLOAD_SIZE=50MB
  - MAX_CONTENT_LENGTH=52428800
```

**相关配置**：

- `MAX_UPLOAD_SIZE`: Spring Boot文件上传限制
- `MAX_CONTENT_LENGTH`: Python WebP处理器请求大小限制
- Nginx `client_max_body_size`: 在nginx.conf中配置

### 3. 日志查看

**查看不同服务的日志**：

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker logs -f char-art-app

# 查看容器内的日志文件
docker exec char-art-app tail -f /app/logs/char-art-converter.log
docker exec char-art-app tail -f /app/logs/webp_processor.log
docker exec char-art-app tail -f /var/log/nginx/access.log
docker exec char-art-app tail -f /var/log/nginx/error.log

# 查看supervisord日志
docker exec char-art-app tail -f /var/log/supervisord.log

# 查看Redis日志
docker logs -f redis
```

### 4. 数据持久化

**确保数据不丢失**：

```bash
# 使用命名卷
docker volume create char-art-data
docker volume create char-art-logs

# 备份数据卷
docker run --rm -v char-art-data:/data -v $(pwd):/backup ubuntu tar czf /backup/char-art-data-backup.tar.gz -C /data .

# 恢复数据卷
docker run --rm -v char-art-data:/data -v $(pwd):/backup ubuntu tar xzf /backup/char-art-data-backup.tar.gz -C /data

# 查看卷使用情况
docker system df -v
```

### 5. 性能优化

**提高应用性能**：

```bash
# 增加内存限制
docker run -d --name char-art-app \
  --memory=4g \
  --memory-swap=8g \
  char-art-converter:latest

# 设置CPU限制
docker run -d --name char-art-app \
  --cpus="2.0" \
  char-art-converter:latest

# 优化JVM参数
docker run -d --name char-art-app \
  -e JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC" \
  char-art-converter:latest

# 启用Redis持久化
docker run -d --name redis \
  -v redis-data:/data \
  redis:6.2-alpine redis-server --appendonly yes --save 60 1000
```

### 6. 健康检查

**配置和使用健康检查**：

```bash
# 手动健康检查
curl -f http://localhost:80/api/health
curl -f http://localhost:80/webp/api/health

# 查看容器健康状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 在Dockerfile中添加健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:80/api/health || exit 1

# 在docker-compose.yml中配置健康检查
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:80/api/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

### 7. 停止和删除容器

**正确清理资源**：

```bash
# 优雅停止服务
docker-compose stop

# 强制停止服务
docker-compose kill

# 删除容器但保留卷
docker-compose down

# 删除容器和卷
docker-compose down -v

# 删除容器、卷和镜像
docker-compose down -v --rmi all

# 清理未使用的资源
docker system prune -a

# 清理特定资源
docker container prune
docker image prune -a
docker volume prune
docker network prune
```

**注意事项**：

- 删除卷会导致数据丢失，请确保已备份重要数据
- 在生产环境中，建议使用外部存储来持久化重要数据
- 定期清理未使用的Docker资源以释放磁盘空间
- 监控容器资源使用情况，及时调整配置

---

如需更多帮助，请参考各子服务的独立Docker部署文档：

- [后端服务Docker部署指南](./backend/Docker.md)
- [前端服务Docker部署指南](./frontend/Docker.md)
- [WebP处理器Docker部署指南](./python_webp_processor/Docker.md)