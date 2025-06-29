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
- **后端API**: `http://localhost:8080/api`
- **健康检查**: `http://localhost:8080/api/health`
- **WebP处理器**: `http://localhost:8081/webp/api/health`

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

### 环境变量配置

以下是可通过环境变量配置的参数列表：

| 环境变量名称                                        | 环境变量中文名             | 环境变量作用                     | 环境变量默认值                              | 环境变量对应配置文件参数                                |
|-----------------------------------------------|-------------------|--------------------------|------------------------------------|-----------------------------------------|
| `TIMEZONE`                                    | 应用时区              | 设置应用程序使用的时区              | `Asia/Shanghai`                    | `spring.jackson.time-zone`              |
| `MAX_UPLOAD_SIZE`                             | 最大上传文件大小          | 限制上传文件的最大大小              | `10`                               | `spring.servlet.multipart.max-file-size` |
| `PORT`                                        | 主服务端口             | 前端Nginx服务监听端口             | `80`                               | Nginx配置文件                             |
| `SERVER_PORT`                                 | 后端服务端口            | Spring Boot应用监听端口           | `8080`                             | `server.port`                           |
| `BACKEND_PATH`                                | 后端服务路径            | 后端服务部署路径                 | `/app/backend`                     | 系统环境变量                                |
| `DEFAULT_CONFIG_PATH`                         | 默认配置路径            | 后端配置文件路径                 | `/app/config`                      | 系统环境变量                                |
| `BACKEND_LOG_LEVEL`                           | 后端日志级别            | 后端服务日志输出级别               | `INFO`                             | `logging.level.root`                    |
| `BACKEND_LOG_FILE_PATH`                       | 后端日志文件路径          | 后端日志文件存储路径               | `/app/logs/char-art-converter.log` | `logging.file.name`                     |
| `LOG_FILE_MAX_SIZE`                           | 日志文件最大大小          | 单个日志文件的最大大小              | `10MB`                             | `logging.logback.rollingpolicy.max-file-size` |
| `LOG_FILE_MAX_HISTORY`                        | 日志文件保留数量          | 保留的历史日志文件数量              | `30`                               | `logging.logback.rollingpolicy.max-history` |
| `LOG_CHARSET_CONSOLE`                         | 控制台日志字符集          | 控制台输出日志的字符编码             | `UTF-8`                            | `logging.charset.console`               |
| `LOG_CHARSET_FILE`                            | 文件日志字符集           | 日志文件的字符编码                | `UTF-8`                            | `logging.charset.file`                  |
| `DATETIME_FORMAT`                             | 日期时间格式            | 日期时间的显示格式                | `yyyy-MM-dd HH:mm:ss`              | `spring.jackson.date-format`            |
| `DEFAULT_DENSITY`                             | 默认字符密度            | 字符画转换的默认字符密度             | `medium`                           | `char-art.default.density`              |
| `DEFAULT_COLOR_MODE`                          | 默认颜色模式            | 字符画转换的默认颜色模式             | `grayscale`                        | `char-art.default.color-mode`           |
| `DEFAULT_TEMP_PATH`                           | 默认临时路径            | 临时文件存储路径                 | `/app/data/temp`                   | `char-art.temp.path`                    |
| `FRONTEND_PATH`                               | 前端服务路径            | 前端服务部署路径                 | `/app/frontend`                    | 系统环境变量                                |
| `VITE_BASE_PATH`                              | 前端资源路径前缀          | 前端应用部署的子路径               | 空字符串                               | `VITE_BASE_PATH`                        |
| `VITE_API_URL`                                | API服务器地址          | 前端调用的后端API地址             | `http://localhost:8080`            | `VITE_API_URL`                          |
| `VITE_API_BASE_PATH`                          | API基础路径           | API请求的基础路径                | `/api`                             | `VITE_API_BASE_PATH`                    |
| `VITE_APP_TITLE`                              | 应用标题              | 前端应用显示的标题                | `字符画转换器`                         | `VITE_APP_TITLE`                        |
| `VITE_APP_VERSION`                            | 应用版本              | 前端应用版本号                  | `1.0.0`                            | `VITE_APP_VERSION`                      |
| `VITE_DEBUG`                                  | 前端调试模式            | 是否启用前端调试模式               | `false`                            | `VITE_DEBUG`                            |
| `VITE_SOURCEMAP`                              | 源码映射              | 是否启用源码映射                 | `false`                            | `VITE_SOURCEMAP`                        |
| `VITE_MAX_UPLOAD_SIZE`                        | 前端最大上传大小          | 前端限制的最大上传文件大小            | `10`                               | `VITE_MAX_UPLOAD_SIZE`                  |
| `WEBP_PROCESSOR`                              | WebP处理器路径         | WebP处理器服务部署路径            | `/app/webp-processor`              | 系统环境变量                                |
| `TEMP_DIR_PATH`                               | WebP处理器临时文件目录     | WebP处理器临时文件存储路径          | `/app/webp-processor/data`         | `TEMP_DIR_PATH`                         |
| `WEBP_PROCESSOR_URL`                          | WebP处理器URL        | 后端调用WebP处理器的URL          | `http://localhost:8081`            | `char-art.webp.processor.url`           |
| `WEBP_PROCESSOR_ENABLED`                      | WebP处理器开关         | 是否启用WebP处理服务              | `true`                             | `char-art.webp.processor.enabled`       |
| `WEBP_PROCESSOR_CONNECTION_TIMEOUT`           | WebP连接超时          | WebP处理器连接超时时间（毫秒）        | `600000`                           | `char-art.webp.processor.connection-timeout` |
| `WEBP_PROCESSOR_MAX_RETRIES`                  | WebP最大重试次数        | WebP处理器的最大重试次数            | `2`                                | `char-art.webp.processor.max-retries`   |
| `WEBP_PROCESSOR_LOG_LEVEL`                    | WebP处理器日志级别       | WebP处理器日志输出级别              | `INFO`                             | `WEBP_PROCESSOR_LOG_LEVEL`              |
| `WEBP_LOG_FILE_PATH`                          | WebP日志文件路径        | WebP处理器日志文件路径              | `/app/logs/webp_processor.log`     | `WEBP_LOG_FILE_PATH`                    |
| `JAVA_BACKEND_URL`                            | Java后端URL         | WebP处理器回调Java后端的URL       | `http://localhost:8080`            | `JAVA_BACKEND_URL`                      |
| `MAX_CONTENT_LENGTH`                          | 最大内容长度            | WebP处理器接受的最大请求大小         | `10485760`                         | `MAX_CONTENT_LENGTH`                    |
| `TEMP_FILE_TTL`                               | 临时文件生存时间          | 临时文件的生存时间（秒）             | `3600`                             | `TEMP_FILE_TTL`                         |
| `DEBUG`                                       | 调试模式              | 是否启用调试模式                 | `False`                            | `DEBUG`                                 |
| `REDIS_HOST`                                  | Redis主机地址         | Redis服务器的主机名或IP地址        | `localhost`                        | `spring.redis.host`                     |
| `REDIS_PORT`                                  | Redis端口           | Redis服务器的端口号              | `6379`                             | `spring.redis.port`                     |
| `REDIS_DATABASE`                              | Redis数据库索引       | 使用的Redis数据库索引             | `0`                                | `spring.redis.database`                 |
| `REDIS_TIMEOUT`                               | Redis连接超时         | Redis连接超时时间（毫秒）           | `60000`                            | `spring.redis.timeout`                  |
| `REDIS_PASSWORD`                              | Redis密码           | Redis服务器认证密码              | 空字符串                               | `spring.redis.password`                 |
| `REDIS_DB`                                    | Redis数据库编号       | WebP处理器使用的Redis数据库编号     | `0`                                | `REDIS_DB`                              |
| `CHAR_ART_CACHE_TTL`                          | 字符画缓存过期时间         | 字符画缓存的生存时间（秒）            | `3600`                             | `char-art.cache.ttl`                    |
| `CHAR_ART_CACHE_DEFAULT_KEY_PREFIX`           | 字符画缓存键前缀          | 字符画缓存键的默认前缀              | `char-art:text:`                   | `char-art.cache.default-key-prefix`     |
| `CHAR_ART_PARALLEL_MAX_FRAME_THREADS`         | 最大帧处理线程数          | 并行处理视频帧的最大线程数            | `4`                                | `char-art.parallel.max-frame-threads`   |
| `CHAR_ART_PARALLEL_THREAD_POOL_FACTOR`        | 线程池因子             | 线程池大小计算因子                | `2.0`                              | `char-art.parallel.thread-pool-factor`  |
| `CHAR_ART_PARALLEL_MIN_THREADS`                | 最小线程数             | 线程池的最小线程数                | `2`                                | `char-art.parallel.min-threads`         |
| `CHAR_ART_PARALLEL_PROGRESS_UPDATE_INTERVAL`   | 进度更新间隔            | 进度更新的时间间隔（毫秒）            | `1000`                             | `char-art.parallel.progress-update-interval` |
| `CHAR_ART_PARALLEL_PIXEL_PROGRESS_INTERVAL`    | 像素进度间隔            | 像素处理进度更新间隔               | `10000`                            | `char-art.parallel.pixel-progress-interval` |
| `CHAR_ART_PARALLEL_TASK_TIMEOUT`               | 任务超时时间            | 并行任务的超时时间（毫秒）            | `300000`                           | `char-art.parallel.task-timeout`        |
| `CHAR_ART_PARALLEL_PROGRESS_CLEANUP_DELAY`     | 进度清理延迟            | 进度信息清理延迟时间（毫秒）           | `60000`                            | `char-art.parallel.progress-cleanup-delay` |
| `PROGRESS_UPDATE_INTERVAL`                     | 通用进度更新间隔          | 通用进度更新间隔（毫秒）             | `1000`                             | `PROGRESS_UPDATE_INTERVAL`              |
| `CHAR_ART_TEMP_FILE_MAX_RETENTION_HOURS`       | 临时文件最大保留小时数       | 临时文件的最大保留时间（小时）          | `24`                               | `char-art.temp-file.max-retention-hours` |
| `CHAR_ART_TEMP_FILE_CLEANUP_ENABLED`           | 临时文件清理开关          | 是否启用临时文件自动清理             | `true`                             | `char-art.temp-file.cleanup-enabled`    |
| `SUPERVISORD_LOG_PATH`                         | Supervisord日志路径   | Supervisord进程管理器日志路径      | `/var/log`                         | supervisord.conf                        |
| `SUPERVISORD_PID_PATH`                         | Supervisord PID路径 | Supervisord进程ID文件路径        | `/var/run`                         | supervisord.conf                        |
| `SUPERVISORD_CONFIG_PATH`                      | Supervisord配置路径   | Supervisord配置文件路径          | `/etc/supervisor/conf.d`           | 系统环境变量                                |
| `LANG`                                        | 系统语言环境            | 系统默认语言和字符集               | `zh_CN.UTF-8`                      | 系统环境变量                                |
| `LC_ALL`                                      | 本地化设置             | 系统本地化设置                  | `zh_CN.UTF-8`                      | 系统环境变量                                |

### Docker Run 使用示例

```bash
# 基本运行
docker run -d --name char-art-converter \
  -p 80:80 \
  -e TIMEZONE=Asia/Shanghai \
  -e MAX_UPLOAD_SIZE=20 \
  -e BACKEND_LOG_LEVEL=DEBUG \
  char-art-converter:latest

# 完整配置运行
docker run -d --name char-art-converter \
  -p 80:80 \
  -e TIMEZONE=Asia/Shanghai \
  -e MAX_UPLOAD_SIZE=50 \
  -e SERVER_PORT=8080 \
  -e BACKEND_LOG_LEVEL=INFO \
  -e REDIS_HOST=redis-server \
  -e REDIS_PORT=6379 \
  -e REDIS_PASSWORD=your_password \
  -e WEBP_PROCESSOR_ENABLED=true \
  -e DEFAULT_DENSITY=high \
  -e DEFAULT_COLOR_MODE=color \
  -v char-art-data:/app/data \
  -v char-art-logs:/app/logs \
  char-art-converter:latest
```

### Docker Compose 使用示例

```yaml
version: '3.8'
services:
  char-art-converter:
    build: .
    ports:
      - "80:80"
    environment:
      # 通用配置
      - TIMEZONE=Asia/Shanghai
      - MAX_UPLOAD_SIZE=20
      
      # 后端配置
      - SERVER_PORT=8080
      - BACKEND_LOG_LEVEL=INFO
      - DEFAULT_DENSITY=medium
      - DEFAULT_COLOR_MODE=grayscale
      
      # Redis配置
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_DATABASE=0
      - REDIS_PASSWORD=your_password
      
      # WebP处理器配置
      - WEBP_PROCESSOR_ENABLED=true
      - WEBP_PROCESSOR_LOG_LEVEL=INFO
      
      # 前端配置
      - VITE_APP_TITLE=我的字符画转换器
      - VITE_API_URL=http://localhost:8080
      - VITE_DEBUG=false
    volumes:
      - char-art-data:/app/data
      - char-art-logs:/app/logs
      - char-art-config:/app/config
    depends_on:
      - redis
    networks:
      - char-art-network

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass your_password
    volumes:
      - redis-data:/data
    networks:
      - char-art-network

volumes:
  char-art-data:
  char-art-logs:
  char-art-config:
  redis-data:

networks:
  char-art-network:
    driver: bridge
```

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

# 查看卷信息char-art-overlay` | 跨主机通信  | 用于Docker Swarm集群部署 |

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

docker volume ls
docker volume inspect char-art-data