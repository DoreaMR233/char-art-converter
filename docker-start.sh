#!/bin/bash
set -e

# 打印欢迎信息
echo "=================================================="
echo "      字符画转换器 - 一体化Docker容器启动      "
echo "=================================================="

# 创建必要的目录
echo "[1/5] 创建必要的目录..."
mkdir -p /app/data
mkdir -p /app/logs
mkdir -p /app/webp-processor/data
mkdir -p /app/webp-processor/logs
echo "目录创建完成"

# 启动Redis服务
echo "[2/5] 启动Redis服务..."
redis-server --daemonize yes --appendonly yes
echo "Redis服务已启动"

# 启动WebP处理器
echo "[3/5] 启动WebP处理器服务..."
cd /app/webp-processor

# 设置Python WebP处理器环境变量
export DEBUG=${DEBUG:-False}
export MAX_CONTENT_LENGTH=${MAX_CONTENT_LENGTH:-16777216}
export TEMP_FILE_TTL=${TEMP_FILE_TTL:-3600}
export LOG_LEVEL=${LOG_LEVEL:-INFO}

# 设置Gunicorn相关环境变量
export GUNICORN_WORKERS=${GUNICORN_WORKERS:-4}
export GUNICORN_TIMEOUT=${GUNICORN_TIMEOUT:-120}
export GUNICORN_MAX_REQUESTS=${GUNICORN_MAX_REQUESTS:-1000}
export GUNICORN_MAX_REQUESTS_JITTER=${GUNICORN_MAX_REQUESTS_JITTER:-50}

/app/webp-processor/docker-entrypoint.sh &
echo "WebP处理器服务已使用Gunicorn启动在端口: 5000"

# 配置Nginx
echo "[4/5] 配置Nginx服务..."

# 如果设置了BASE_PATH，则配置前端资源路径
if [ -n "$BASE_PATH" ]; then
  echo "配置前端资源路径前缀: /$BASE_PATH/"
  cd /app/frontend
  export BASE_PATH=$BASE_PATH
  /app/frontend/docker-entrypoint.sh
fi

if [ -n "$WEBP_PROCESSOR_URL" ]; then
  echo "WebP处理器环境变量: $WEBP_PROCESSOR_URL"
    cd /app/backend
    export WEBP_PROCESSOR_URL=$WEBP_PROCESSOR_URL
    /app/backend/docker-entrypoint.sh
fi

# 创建Nginx配置文件
cat > /etc/nginx/conf.d/default.conf << EOF
server {
    #文件大小配置
    client_max_body_size 100m;


    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files \$uri \$uri/ /index.html;
    }

    # 后端API代理
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # WebP处理器API代理
    location /webp/ {
        proxy_pass http://localhost:5000/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

# 启动Nginx
nginx -g "daemon off;" &
echo "Nginx服务已启动在端口: 80"

# 启动后端服务
echo "[5/5] 启动后端服务..."
cd /app/backend

# 修改环境变量以适应内部服务URL
export WEBP_PROCESSOR_URL=http://localhost:5000
export SERVER_PORT=8080
# 设置日志相关环境变量
export LOG_FILE_MAX_SIZE=${LOG_FILE_MAX_SIZE:-10MB}
export LOG_FILE_MAX_HISTORY=${LOG_FILE_MAX_HISTORY:-30}

# 启动后端服务
java -jar app.jar