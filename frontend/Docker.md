# 字符画转换器前端 Docker 部署指南

本文档提供了使用 Docker 部署字符画转换器前端服务的详细说明，包括 Docker Compose 和 docker run 两种部署方式。

## 目录

- [字符画转换器前端 Docker 部署指南](#字符画转换器前端-docker-部署指南)
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
      - [资源路径前缀配置](#资源路径前缀配置)
      - [项目后端地址配置](#项目后端地址配置)
  - [自定义构建](#自定义构建)
    - [修改Dockerfile](#修改dockerfile)
    - [修改启动脚本](#修改启动脚本)
  - [常见问题](#常见问题)
    - [1. 资源路径问题](#1-资源路径问题)
    - [2. Nginx配置](#2-nginx配置)
    - [3. 与后端服务集成](#3-与后端服务集成)
    - [4. 日志查看](#4-日志查看)
    - [5. 停止和删除容器](#5-停止和删除容器)

## 前提条件

- Docker 19.03.0+
- Docker Compose 1.27.0+ (如使用 Docker Compose 部署)
- Git (可选，用于克隆项目)

## 项目结构

``` text
./
├── Dockerfile          # 用于构建前端服务的Docker镜像
├── docker-compose.yml  # 定义服务组合
├── docker-entrypoint.sh # 容器启动脚本，用于配置资源路径前缀
└── src/                # 源代码目录
```

## 使用 Docker Compose 部署

### 构建并启动服务

```bash
# 在项目根目录下执行
docker-compose up -d
```

这将启动 `char-art-frontend` 服务，并将容器的80端口映射到主机的8080端口。

### 验证服务状态

```bash
# 检查容器状态
docker-compose ps

# 查看前端服务日志
docker-compose logs -f char-art-frontend
```

### 访问服务

服务启动后，可以通过 `http://localhost:8080` 访问前端应用。如果配置了 `BASE_PATH` 环境变量，则应通过 `http://localhost:8080/{BASE_PATH}` 访问。

### 停止服务

```bash
docker-compose down
```

## 使用 Docker Run 部署

### 构建镜像

在项目根目录下执行以下命令构建Docker镜像：

```bash
docker build -t char-art-frontend:latest .
```

### 运行容器

```bash
docker run -d \
  --name char-art-frontend \
  --network char-art-network \
  -p 8080:80 \
  -e BASE_PATH="" \
  -e API_URL="http://localhost:8080" \
  char-art-frontend:latest
```

这将以默认配置启动字符画转换器前端服务，并将容器的80端口映射到主机的8080端口。

### 验证容器服务状态

```bash
# 查看容器状态
docker ps

# 查看容器日志
docker logs char-art-frontend
```

服务启动后，可以通过 `http://localhost:8080` 访问前端应用。

## 配置参数

### 环境变量

无论是使用 Docker Compose 还是 docker run 命令，都可以通过环境变量自定义以下配置：

#### 资源路径前缀配置

- `BASE_PATH`: 资源路径前缀，用于在非根路径部署时设置资源路径 (默认: 空，表示部署在根路径)

  例如，如果您的应用部署在 `http://example.com/char-art/`，则应设置 `BASE_PATH=char-art`

  ```bash
  docker run -d --name char-art-frontend -p 8080:80 -e BASE_PATH=char-art char-art-frontend:latest
  ```

#### 项目后端地址配置

- `API_URL`: 项目后端地址，用于与后端服务进行通信 (默认: `http://localhost:8080`)

  例如，如果您的项目后端地址为 `http://1.2.3.4:8080`，则应设置 `API_URL=http://1.2.3.4:8080`

  ```bash
  docker run -d \
  --name char-art-frontend \
  -p 8080:80 \
  -e API_URL=http://1.2.3.4:8080 \
  char-art-frontend:latest
  ```

## 自定义构建

### 修改Dockerfile

如果需要自定义Docker镜像构建过程，可以修改 `Dockerfile`。默认的Dockerfile使用多阶段构建：

1. 第一阶段使用Node.js环境构建前端应用
2. 第二阶段使用Nginx作为Web服务器运行构建后的应用

### 修改启动脚本

`docker-entrypoint.sh` 脚本用于在容器启动时根据 `BASE_PATH` 环境变量配置资源路径前缀。如果需要添加更多自定义配置，可以修改此脚本。

## 常见问题

### 1. 资源路径问题

如果您的应用无法正确加载资源（CSS、JS、图片等），请检查以下配置：

- 确保 `BASE_PATH` 环境变量正确设置
- 检查 `docker-entrypoint.sh` 脚本是否正确执行
- 查看容器日志以确认资源路径前缀配置是否成功

### 2. Nginx配置

默认情况下，应用使用Nginx的默认配置。如果需要自定义Nginx配置，可以：

1. 创建自定义的Nginx配置文件
2. 修改Dockerfile，将自定义配置复制到容器中

```bash
# 在Dockerfile中添加
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

### 3. 与后端服务集成

前端应用需要与后端API通信。在生产环境中，您可以：

1. 使用Nginx反向代理将API请求转发到后端服务
2. 在前端构建时配置正确的API基础URL，确保 `API_URL` 环境变量正确设置
3. 使用Docker网络连接前端和后端容器

```bash
# 创建Docker网络
docker network create char-art-network

# 启动后端服务并连接到网络
docker run -d --name char-art-backend --network char-art-network -p 8080:8080 char-art-backend:latest

# 启动前端服务并连接到网络，设置API_URL指向后端容器名
docker run -d --name char-art-frontend --network char-art-network -p 8081:80 -e API_URL=http://char-art-backend:8080 char-art-frontend:latest
```

### 4. 日志查看

```bash
# 使用Docker Compose查看日志
docker-compose logs -f char-art-frontend

# 使用Docker命令查看日志
docker logs -f char-art-frontend

# 查看最近100行日志
docker logs --tail=100 char-art-frontend
```

### 5. 停止和删除容器

```bash
# 停止容器
docker stop char-art-frontend

# 删除容器
docker rm char-art-frontend
```
