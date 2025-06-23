# 字符画转换器前端 Docker 部署指南

本文档提供了使用 Docker 部署字符画转换器前端服务的详细说明，包括 Docker Compose 和 docker run 两种部署方式。

## 目录

- [字符画转换器前端 Docker 部署指南](#字符画转换器前端-docker-部署指南)
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
      - [开发环境变量](#开发环境变量)
      - [生产环境变量](#生产环境变量)
      - [通用环境变量](#通用环境变量)
      - [使用示例](#使用示例)
    - [卷](#卷)
      - [卷使用示例](#卷使用示例)
    - [网络](#网络)
      - [网络使用示例](#网络使用示例)
  - [与其他服务集成](#与其他服务集成)
    - [与后端服务集成](#与后端服务集成)
      - [使用Docker Compose集成](#使用docker-compose集成)
      - [使用Docker网络集成](#使用docker网络集成)
    - [与WebP处理服务集成](#与webp处理服务集成)
      - [使用Docker Compose方式集成](#使用docker-compose方式集成)
      - [使用Docker网络方式集成](#使用docker网络方式集成)
    - [与Redis服务集成](#与redis服务集成)
    - [完整的服务集成示例](#完整的服务集成示例)
  - [自定义构建](#自定义构建)
    - [修改Dockerfile](#修改dockerfile)
    - [修改启动脚本](#修改启动脚本)
    - [修改Nginx配置](#修改nginx配置)
  - [常见问题](#常见问题)
    - [1. 资源路径问题](#1-资源路径问题)
    - [2. API请求代理问题](#2-api请求代理问题)
    - [3. 环境变量配置](#3-环境变量配置)
    - [4. Nginx配置问题](#4-nginx配置问题)
    - [5. 日志查看](#5-日志查看)
    - [6. 停止和删除容器](#6-停止和删除容器)

## 前提条件

- Docker 19.03.0+
- Docker Compose 1.27.0+ (如使用 Docker Compose 部署)
- Node.js 18+ (如需本地开发)
- Git (可选，用于克隆项目)

## 项目结构

``` text
./
├── Dockerfile            # 用于构建前端服务的Docker镜像
├── docker-compose.yml    # 定义服务组合
├── docker-entrypoint.sh  # 容器启动脚本，用于配置环境变量和Nginx
├── nginx.conf           # Nginx配置文件
├── .env                 # 通用环境变量配置
├── .env.development     # 开发环境变量配置
├── .env.production      # 生产环境变量配置
├── package.json         # Node.js项目配置文件
├── vite.config.js       # Vite构建配置文件
└── src/                 # 源代码目录
    ├── main.js          # 应用入口文件
    ├── App.vue          # 根组件
    └── api/             # API接口模块
```

## 使用 Docker Compose 部署

### 构建并启动服务

```bash
# 在项目根目录下执行
docker-compose up -d
```

这将启动 `char-art-frontend` 服务，并将容器的80端口映射到主机的8080端口。

### 验证Docker Compose服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看前端服务日志
docker-compose logs -f char-art-frontend
```

### 访问服务

服务启动后，可以通过以下方式访问前端应用：

- 默认访问：`http://localhost:8080`
- 如果配置了 `VITE_BASE_PATH`：`http://localhost:8080/{BASE_PATH}`

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
# 基本构建
docker build -t char-art-frontend:latest .

# 带构建参数的构建
docker build \
  --build-arg VITE_BASE_PATH=charart \
  --build-arg BACKEND_HOST=http://backend \
  --build-arg BACKEND_PORT=8080 \
  -t char-art-frontend:latest .
```

### 运行容器

```bash
# 基本运行
docker run -d --name char-art-frontend -p 8080:80 char-art-frontend:latest

# 带环境变量的运行
docker run -d --name char-art-frontend \
  -p 8080:80 \
  -e VITE_BASE_PATH=charart \
  -e VITE_API_URL=http://backend:8080 \
  char-art-frontend:latest

# 带数据卷的运行
docker run -d --name char-art-frontend \
  -p 8080:80 \
  -v char-art-frontend-logs:/var/log/nginx \
  char-art-frontend:latest
```

### 验证Docker Run服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs char-art-frontend

# 检查服务可用性
curl http://localhost:8080
```

## 配置参数

### 配置文件变量

#### 开发环境变量

开发环境变量定义在 `.env.development` 文件中：

| 变量名称             | 变量中文名    | 变量作用                 | 变量默认值                   |
|------------------|----------|----------------------|-------------------------|
| `VITE_API_URL`   | API服务器地址 | 指定后端API服务的完整URL地址    | `http://localhost:8080` |
| `VITE_BASE_PATH` | 资源路径前缀   | 设置应用部署的子路径，开发环境通常为空  | 空字符串                    |
| `VITE_PORT`      | 开发服务器端口  | 开发服务器监听的端口号          | `5174`                  |
| `VITE_DEBUG`     | 调试模式开关   | 是否启用调试模式，影响日志输出和错误显示 | `true`                  |

#### 生产环境变量

生产环境变量定义在 `.env.production` 文件中：

| 变量名称             | 变量中文名    | 变量作用            | 变量默认值                 |
|------------------|----------|-----------------|-----------------------|
| `VITE_API_URL`   | API服务器地址 | 生产环境中的后端API服务地址 | `http://backend:8080` |
| `VITE_BASE_PATH` | 资源路径前缀   | 生产环境中应用部署的子路径   | `charart`             |
| `VITE_DEBUG`     | 调试模式开关   | 生产环境中是否启用调试模式   | `false`               |
| `VITE_SOURCEMAP` | 源码映射开关   | 是否生成源码映射文件，用于调试 | `false`               |

#### 通用环境变量

通用环境变量定义在 `.env` 文件中，适用于所有环境：

| 变量名称                   | 变量中文名    | 变量作用            | 变量默认值    |
|------------------------|----------|-----------------|----------|
| `VITE_APP_TITLE`       | 应用名称     | 显示在浏览器标题栏的应用名称  | `字符画转换器` |
| `VITE_APP_VERSION`     | 应用版本     | 当前应用的版本号        | `1.0.0`  |
| `VITE_API_BASE_PATH`   | API基础路径  | API请求的基础路径前缀    | `/api`   |
| `VITE_MAX_UPLOAD_SIZE` | 最大上传文件大小 | 允许上传的文件最大大小（MB） | `10`     |

#### 使用示例

```bash
# 使用自定义配置运行容器
docker run -d --name char-art-frontend \
  -p 8080:80 \
  -e VITE_BASE_PATH=myapp \
  -e VITE_API_URL=http://api.example.com:8080 \
  -e VITE_MAX_UPLOAD_SIZE=20 \
  -e VITE_DEBUG=false \
  char-art-frontend:latest
```

### 卷

应用使用以下卷来持久化数据和日志：

| 卷名称                        | 容器内路径                   | 用途        | 说明               |
|----------------------------|-------------------------|-----------|------------------|
| `char-art-frontend-logs`   | `/var/log/nginx`        | Nginx日志存储 | 存储Nginx访问日志和错误日志 |
| `char-art-frontend-html`   | `/usr/share/nginx/html` | 静态文件存储    | 存储构建后的前端静态文件     |
| `char-art-frontend-config` | `/etc/nginx`            | Nginx配置存储 | 存储Nginx配置文件      |

#### 卷使用示例

```bash
# 创建命名卷
docker volume create char-art-frontend-logs
docker volume create char-art-frontend-config

# 使用命名卷运行容器
docker run -d --name char-art-frontend \
  -p 8080:80 \
  -v char-art-frontend-logs:/var/log/nginx \
  -v char-art-frontend-config:/etc/nginx \
  char-art-frontend:latest

# 使用主机目录挂载
docker run -d --name char-art-frontend \
  -p 8080:80 \
  -v /host/path/logs:/var/log/nginx \
  -v /host/path/config:/etc/nginx \
  char-art-frontend:latest
```

### 网络

应用支持以下网络配置：

| 网络类型 | 网络名称               | 用途         | 说明                |
|------|--------------------|------------|-------------------|
| 桥接网络 | `char-art-network` | 服务间通信      | 连接前端、后端、WebP处理服务等 |
| 主机网络 | `host`             | 直接访问主机网络   | 用于开发环境或特殊网络需求     |
| 默认网络 | `bridge`           | 默认Docker网络 | 单容器运行时使用          |

#### 网络使用示例

```bash
# 创建自定义网络
docker network create char-art-network

# 在自定义网络中运行前端服务
docker run -d --name char-art-frontend \
  --network char-art-network \
  -p 8080:80 \
  -e VITE_API_URL=http://char-art-backend:8080 \
  char-art-frontend:latest

# 查看网络信息
docker network inspect char-art-network
```

## 与其他服务集成

### 与后端服务集成

前端服务需要与后端API服务集成，以提供完整的字符画转换功能。

#### 使用Docker Compose集成

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

前端通过后端服务间接与WebP处理服务集成，用于处理WebP动图转换功能。

#### 使用Docker Compose方式集成

```yaml
services:
  char-art-frontend:
    environment:
      - VITE_API_URL=http://char-art-backend:8080
    depends_on:
      - char-art-backend

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

# 前端服务通过后端间接访问WebP处理服务
# 无需直接配置连接
```

### 与Redis服务集成

前端服务通过后端间接使用Redis进行缓存和会话管理。

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

  char-art-frontend:
    depends_on:
      - char-art-backend
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
# 第一阶段：构建应用
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制包管理文件
COPY package*.json ./

# 安装项目依赖
RUN npm install

# 复制项目文件
COPY . .

# 设置构建参数
ARG VITE_BASE_PATH
ARG BACKEND_HOST="http://localhost"
ARG BACKEND_PORT=8080
ARG API_URL="${BACKEND_HOST}:${BACKEND_PORT}"

# 更新生产环境配置
RUN if [ -f .env.production ]; then \
    sed -i "/^VITE_BASE_PATH=/c\VITE_BASE_PATH=$VITE_BASE_PATH" .env.production; \
    sed -i "/^VITE_API_URL=/c\VITE_API_URL=$API_URL" .env.production; \
    fi

# 构建生产版本
RUN npm run build

# 第二阶段：运行应用
FROM nginx:alpine

# 安装必要的工具
RUN apk add --no-cache bash

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制环境变量文件
COPY --from=builder /app/.env /app/.env
COPY --from=builder /app/.env.production /app/.env.production

# 复制Nginx配置文件
COPY nginx.conf /nginx.conf

# 复制启动脚本
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost/ || exit 1

# 设置入口点
ENTRYPOINT ["/docker-entrypoint.sh"]
```

### 修改启动脚本

`docker-entrypoint.sh` 脚本用于在容器启动时配置环境变量和Nginx：

```bash
#!/bin/bash
set -e

# 定义文件路径
HTML_DIR="/usr/share/nginx/html"
HTML_FILE="$HTML_DIR/index.html"
NGINX_CONF="/etc/nginx/nginx.conf"
ENV_FILE="/app/.env"
ENV_PROD_FILE="/app/.env.production"

# 加载环境变量
load_env_vars() {
    local env_file=$1
    if [ -f "$env_file" ]; then
        echo "正在加载 $env_file 中的环境变量..."
        while IFS="=" read -r key value; do
            # 跳过注释行和空行
            [[ $key =~ ^#.*$ ]] && continue
            [[ -z $key ]] && continue
            
            # 去除引号
            value=$(echo $value | sed -e "s/^[\"\']//" -e "s/[\"\']$//")
            
            # 导出环境变量
            export "$key=$value"
            echo "加载: $key=$value"
        done < <(grep -v "^#" "$env_file" | grep -v "^$")
    fi
}

# 加载环境变量文件
load_env_vars "$ENV_FILE"
load_env_vars "$ENV_PROD_FILE"

# 设置默认值
BASE_PATH_VALUE=${VITE_BASE_PATH:-${BASE_PATH:-""}}
API_URL_VALUE=${VITE_API_URL:-${API_URL:-"http://localhost:8080"}}
API_BASE_PATH_VALUE=${VITE_API_BASE_PATH:-"/api"}
MAX_UPLOAD_SIZE_VALUE=${VITE_MAX_UPLOAD_SIZE:-10}

# 配置Nginx
echo "正在配置Nginx..."
cp /nginx.conf "$NGINX_CONF"

# 替换Nginx配置中的环境变量
sed -i "s/\${BASE_PATH_VALUE}/$BASE_PATH_VALUE/g" "$NGINX_CONF"
sed -i "s/\${API_URL_VALUE}/$API_URL_VALUE/g" "$NGINX_CONF"
sed -i "s/\${API_BASE_PATH_VALUE}/$API_BASE_PATH_VALUE/g" "$NGINX_CONF"
sed -i "s/\${MAX_UPLOAD_SIZE_VALUE}/$MAX_UPLOAD_SIZE_VALUE/g" "$NGINX_CONF"

echo "Nginx配置完成"
echo "BASE_PATH: $BASE_PATH_VALUE"
echo "API_URL: $API_URL_VALUE"
echo "API_BASE_PATH: $API_BASE_PATH_VALUE"
echo "MAX_UPLOAD_SIZE: ${MAX_UPLOAD_SIZE_VALUE}MB"

# 启动Nginx
echo "启动Nginx..."
exec nginx -g "daemon off;"
```

### 修改Nginx配置

如果需要自定义Nginx配置，可以修改 `nginx.conf` 文件：

```nginx
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';
    
    access_log /var/log/nginx/access.log main;
    
    sendfile on;
    keepalive_timeout 65;
    
    # 开启gzip压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript 
               text/xml application/xml application/xml+rss text/javascript;
    
    # 文件大小配置
    client_max_body_size ${MAX_UPLOAD_SIZE_VALUE}m;
    
    server {
        listen 80;
        server_name localhost;
        
        # 根目录配置
        location /${BASE_PATH_VALUE}/ {
            alias /usr/share/nginx/html/;
            index index.html index.htm;
            try_files $uri $uri/ /${BASE_PATH_VALUE}/index.html;
        }
        
        # API请求转发配置
        location /${BASE_PATH_VALUE}${API_BASE_PATH_VALUE}/ {
            proxy_pass ${API_URL_VALUE}${API_BASE_PATH_VALUE}/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # 超时配置
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;
        }
        
        # 健康检查端点
        location /health {
            access_log off;
            return 200 "healthy\n";
            add_header Content-Type text/plain;
        }
    }
}
```

## 常见问题

### 1. 资源路径问题

**问题描述**：应用无法正确加载静态资源（CSS、JS、图片等）

**解决方案**：

- 检查 `VITE_BASE_PATH` 环境变量设置
- 确认 `docker-entrypoint.sh` 脚本正确执行
- 验证Nginx配置中的路径设置

```bash
# 检查环境变量
docker exec char-art-frontend env | grep VITE_

# 查看Nginx配置
docker exec char-art-frontend cat /etc/nginx/nginx.conf

# 检查静态文件
docker exec char-art-frontend ls -la /usr/share/nginx/html/
```

### 2. API请求代理问题

**问题描述**：前端无法正确访问后端API

**解决方案**：

- 检查 `VITE_API_URL` 配置
- 验证后端服务是否正常运行
- 确认网络连接正常

```bash
# 测试后端服务连接
docker exec char-art-frontend curl -f http://char-art-backend:8080/api/health

# 检查Nginx代理配置
docker exec char-art-frontend nginx -t

# 查看Nginx错误日志
docker logs char-art-frontend
```

### 3. 环境变量配置

**问题描述**：环境变量未正确应用

**解决方案**：

- 确认环境变量文件存在
- 检查启动脚本执行情况
- 验证变量值是否正确

```bash
# 检查环境变量文件
docker exec char-art-frontend cat /app/.env
docker exec char-art-frontend cat /app/.env.production

# 查看当前环境变量
docker exec char-art-frontend printenv | grep VITE_

# 重新构建镜像
docker build --no-cache -t char-art-frontend:latest .
```

### 4. Nginx配置问题

**问题描述**：Nginx配置错误导致服务无法启动

**解决方案**：

- 检查Nginx配置语法
- 验证配置文件权限
- 查看Nginx错误日志

```bash
# 测试Nginx配置
docker exec char-art-frontend nginx -t

# 重新加载Nginx配置
docker exec char-art-frontend nginx -s reload

# 查看Nginx进程
docker exec char-art-frontend ps aux | grep nginx
```

### 5. 日志查看

**查看容器日志**：

```bash
# 实时查看日志
docker logs -f char-art-frontend

# 查看最近100行日志
docker logs --tail=100 char-art-frontend

# 查看特定时间段日志
docker logs --since=2023-01-01T00:00:00 --until=2023-01-02T00:00:00 char-art-frontend
```

**查看Nginx日志文件**：

```bash
# 查看访问日志
docker exec char-art-frontend tail -f /var/log/nginx/access.log

# 查看错误日志
docker exec char-art-frontend tail -f /var/log/nginx/error.log

# 查看所有日志文件
docker exec char-art-frontend ls -la /var/log/nginx/
```

### 6. 停止和删除容器

```bash
# 停止容器
docker stop char-art-frontend

# 删除容器
docker rm char-art-frontend

# 强制删除运行中的容器
docker rm -f char-art-frontend

# 清理所有相关资源
docker-compose down -v
docker system prune -f

# 删除镜像
docker rmi char-art-frontend:latest
```
