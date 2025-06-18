# WebP处理服务 Gunicorn 部署指南

## 简介

本文档介绍如何使用 Gunicorn 部署 WebP 处理服务，提高服务的性能和稳定性。

## 为什么使用 Gunicorn

- **并发处理能力**：Gunicorn 使用预分叉工作模式，可以同时处理多个请求
- **稳定性**：自动处理崩溃的工作进程，提高服务可靠性
- **资源利用**：更有效地利用多核处理器
- **生产级别**：适合生产环境的 WSGI HTTP 服务器

## 配置说明

### 环境变量

除了原有的环境变量外，新增了以下 Gunicorn 相关的环境变量：

| 变量名 | 说明 | 默认值 |
|-------|------|-------|
| `GUNICORN_WORKERS` | 工作进程数 | CPU核心数×2+1 |
| `GUNICORN_TIMEOUT` | 请求超时时间(秒) | 120 |
| `GUNICORN_MAX_REQUESTS` | 每个工作进程处理的最大请求数 | 1000 |
| `GUNICORN_MAX_REQUESTS_JITTER` | 最大请求数的随机抖动值 | 50 |

### 配置文件

服务使用 `gunicorn.conf.py` 作为 Gunicorn 的配置文件，包含以下主要设置：

```python
# 绑定的IP和端口
bind = "0.0.0.0:5000"

# 工作进程数
workers = 多核CPU数量×2+1

# 工作模式
worker_class = 'sync'

# 日志配置
accesslog = "/app/logs/access.log"
errorlog = "/app/logs/error.log"
```

## 使用方法

### 本地开发环境

1. 安装依赖：

```bash
pip install -r requirements.txt
```

2. 使用 Gunicorn 启动服务：

```bash
gunicorn --config gunicorn.conf.py wsgi:application
```

### Docker 环境

Docker 环境已配置为自动使用 Gunicorn 启动服务，无需额外操作。

可以通过环境变量调整 Gunicorn 的配置：

```bash
docker run -d \
  --name webp-processor \
  -p 8081:5000 \
  -e GUNICORN_WORKERS=4 \
  -e GUNICORN_TIMEOUT=180 \
  webp-processor:latest
```

## 性能调优

### 工作进程数量

工作进程数量的最佳值取决于服务器的 CPU 核心数和内存大小。一般建议：

- CPU 密集型应用：`CPU核心数 + 1`
- I/O 密集型应用：`CPU核心数 × 2 + 1`

对于 WebP 处理服务，由于涉及图像处理，属于 CPU 和 I/O 混合型，默认使用 `CPU核心数 × 2 + 1` 的配置。

### 最大请求数

设置 `GUNICORN_MAX_REQUESTS` 可以防止内存泄漏，当工作进程处理的请求数达到这个值时，会自动重启。

`GUNICORN_MAX_REQUESTS_JITTER` 添加随机抖动，防止所有工作进程同时重启。

## 监控

### 日志

Gunicorn 的访问日志和错误日志分别保存在：

- 访问日志：`/app/logs/access.log`
- 错误日志：`/app/logs/error.log`

### 健康检查

服务的健康检查端点仍然是：

```
GET /api/health
```

## 常见问题

### 1. 请求超时

如果处理大型 WebP 文件时出现超时，可以增加 `GUNICORN_TIMEOUT` 的值：

```bash
docker run -d --name webp-processor -p 8081:5000 -e GUNICORN_TIMEOUT=300 webp-processor:latest
```

### 2. 内存使用过高

如果服务器内存有限，可以减少工作进程数量：

```bash
docker run -d --name webp-processor -p 8081:5000 -e GUNICORN_WORKERS=2 webp-processor:latest
```

### 3. 进程没有自动重启

检查 Docker 的重启策略是否设置为 `unless-stopped` 或 `always`。