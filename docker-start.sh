#!/bin/bash
set -e

# 打印欢迎信息
echo "=================================================="
echo "      字符画转换器 - 一体化Docker容器启动      "
echo "=================================================="

# 启动Redis服务
echo "[1/4] 启动Redis服务..."
redis-server --daemonize yes --appendonly yes
echo "Redis服务已启动"

# 启动WebP处理器
echo "[2/4] 启动WebP处理器服务..."
cd /app/webp-processor

# 设置Python WebP处理器环境变量
export DEBUG=${DEBUG:-False}
export MAX_CONTENT_LENGTH=${MAX_CONTENT_LENGTH:-16777216}
export TEMP_FILE_TTL=${TEMP_FILE_TTL:-3600}

/app/webp-processor/docker-entrypoint.sh &
echo "WebP处理器服务已启动在端口: 5000"

# 配置Nginx
echo "[3/4] 配置Nginx服务..."

# 如果设置了BASE_PATH，则配置前端资源路径
if [ -n "$BASE_PATH" ]; then
  echo "配置前端资源路径前缀: /$BASE_PATH/"
  cd /app/frontend
  export BASE_PATH=$BASE_PATH
  /app/frontend/docker-entrypoint.sh
fi

# 创建Nginx配置文件
cat > /etc/nginx/conf.d/default.conf << EOF
server {
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
echo "[4/4] 启动后端服务..."
cd /app/backend

# 修改环境变量以适应内部服务URL
export WEBP_PROCESSOR_URL=http://localhost:5000
export SERVER_PORT=8080

# 启动后端服务
java -jar app.jar