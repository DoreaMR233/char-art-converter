# 多阶段构建Dockerfile

# 阶段1: 构建后端
FROM maven:3.8.4-openjdk-11-slim AS backend-build

# 设置工作目录
WORKDIR /app/backend

# 复制pom.xml文件
COPY backend/pom.xml .

# 下载依赖项
RUN mvn dependency:go-offline -B

# 复制源代码
COPY backend/src ./src

# 构建应用
RUN mvn package -DskipTests

# 阶段2: 构建前端
FROM node:18-alpine AS frontend-build

# 设置工作目录
WORKDIR /app/frontend

# 复制包管理文件
COPY frontend/package*.json ./

# 安装项目依赖
RUN npm install

# 复制项目文件
COPY frontend/ .

# 构建生产版本
RUN npm run build

# 阶段3: 构建Python WebP处理器
FROM python:3.9-slim AS webp-processor-build

WORKDIR /app/webp-processor

# 安装依赖
COPY python_webp_processor/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY python_webp_processor/ .

# 阶段4: 最终镜像 - 使用CentOS
FROM centos:7

# 安装必要的工具和依赖
RUN yum -y update && yum -y install epel-release && \
    yum -y install \
    java-11-openjdk \
    nginx \
    python3 \
    python3-pip \
    nc \
    curl \
    redis \
    fontconfig \
    freetype \
    && yum clean all

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/backend/config \
    && mkdir -p /app/backend/data \
    && mkdir -p /app/backend/logs \
    && mkdir -p /app/webp-processor/data \
    && mkdir -p /app/webp-processor/logs

# 复制后端构建产物
COPY --from=backend-build /app/backend/target/*.jar /app/backend/app.jar
COPY backend/src/main/resources/application.properties /app/backend/config/application.properties.template

# 复制前端构建产物
COPY --from=frontend-build /app/frontend/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/nginx.conf

# 复制Python WebP处理器
COPY --from=webp-processor-build /app/webp-processor /app/webp-processor
COPY python_webp_processor/gunicorn.conf.py /app/webp-processor/

# 创建启动脚本
RUN echo '#!/bin/bash' > /app/start.sh && \
    echo 'set -e' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '# 创建必要的目录' >> /app/start.sh && \
    echo 'mkdir -p /app/data' >> /app/start.sh && \
    echo 'mkdir -p /app/logs' >> /app/start.sh && \
    echo 'mkdir -p /app/backend/data' >> /app/start.sh && \
    echo 'mkdir -p /app/backend/logs' >> /app/start.sh && \
    echo 'mkdir -p /app/webp-processor/data' >> /app/start.sh && \
    echo 'mkdir -p /app/webp-processor/logs' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '# 启动Redis服务' >> /app/start.sh && \
    echo 'redis-server --daemonize yes --appendonly yes' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '# 配置后端应用' >> /app/start.sh && \
    echo 'cp /app/backend/config/application.properties.template /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/spring.redis.host=.*/spring.redis.host=localhost/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/spring.redis.port=.*/spring.redis.port=6379/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/spring.redis.database=.*/spring.redis.database=${REDIS_DATABASE}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/spring.redis.timeout=.*/spring.redis.timeout=${REDIS_TIMEOUT}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/char-art.cache.ttl=.*/char-art.cache.ttl=${CHAR_ART_CACHE_TTL}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s|webp-processor.url=.*|webp-processor.url=http://localhost:5000|g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/webp-processor.enabled=.*/webp-processor.enabled=${WEBP_PROCESSOR_ENABLED}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/webp-processor.connection-timeout=.*/webp-processor.connection-timeout=${WEBP_PROCESSOR_CONNECTION_TIMEOUT}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/webp-processor.max-retries=.*/webp-processor.max-retries=${WEBP_PROCESSOR_MAX_RETRIES}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/server.port=.*/server.port=8080/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/spring.servlet.multipart.max-file-size=.*/spring.servlet.multipart.max-file-size=${MAX_FILE_SIZE}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/spring.servlet.multipart.max-request-size=.*/spring.servlet.multipart.max-request-size=${MAX_REQUEST_SIZE}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/logging.level.com.doreamr233.charartconverter=.*/logging.level.com.doreamr233.charartconverter=${LOG_LEVEL}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/logging.logback.rollingpolicy.max-file-size=.*/logging.logback.rollingpolicy.max-file-size=${LOG_FILE_MAX_SIZE}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/logging.logback.rollingpolicy.max-history=.*/logging.logback.rollingpolicy.max-history=${LOG_FILE_MAX_HISTORY}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/char-art.default-density=.*/char-art.default-density=${DEFAULT_DENSITY}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s/char-art.default-color-mode=.*/char-art.default-color-mode=${DEFAULT_COLOR_MODE}/g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo 'sed -i "s|java.io.tmpdir=.*|java.io.tmpdir=/app/data|g" /app/backend/config/application.properties' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '# 配置WebP处理器' >> /app/start.sh && \
    echo 'echo "# WebP处理服务环境配置" > /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "PORT=5000" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "LOG_LEVEL=${LOG_LEVEL}" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "TEMP_FILE_TTL=${TEMP_FILE_TTL}" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "DEBUG=${DEBUG}" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "MAX_CONTENT_LENGTH=${MAX_CONTENT_LENGTH}" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "TEMP_DIR=/app/webp-processor/data" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "GUNICORN_WORKERS=${GUNICORN_WORKERS}" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo 'echo "GUNICORN_TIMEOUT=${GUNICORN_TIMEOUT}" >> /app/webp-processor/.env' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '# 配置Nginx' >> /app/start.sh && \
    echo 'cat > /etc/nginx/conf.d/default.conf << EOF' >> /app/start.sh && \
    echo 'server {' >> /app/start.sh && \
    echo '    client_max_body_size ${MAX_UPLOAD_SIZE}m;' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '    listen 80;' >> /app/start.sh && \
    echo '    server_name localhost;' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '    location / {' >> /app/start.sh && \
    echo '        root /usr/share/nginx/html;' >> /app/start.sh && \
    echo '        index index.html index.htm;' >> /app/start.sh && \
    echo '        try_files \$uri \$uri/ /index.html;' >> /app/start.sh && \
    echo '    }' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '    location /api/ {' >> /app/start.sh && \
    echo '        proxy_pass http://localhost:8080/api/;' >> /app/start.sh && \
    echo '        proxy_set_header Host \$host;' >> /app/start.sh && \
    echo '        proxy_set_header X-Real-IP \$remote_addr;' >> /app/start.sh && \
    echo '        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;' >> /app/start.sh && \
    echo '        proxy_set_header X-Forwarded-Proto \$scheme;' >> /app/start.sh && \
    echo '    }' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '    location /webp/ {' >> /app/start.sh && \
    echo '        proxy_pass http://localhost:5000/api/;' >> /app/start.sh && \
    echo '        proxy_set_header Host \$host;' >> /app/start.sh && \
    echo '        proxy_set_header X-Real-IP \$remote_addr;' >> /app/start.sh && \
    echo '        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;' >> /app/start.sh && \
    echo '        proxy_set_header X-Forwarded-Proto \$scheme;' >> /app/start.sh && \
    echo '    }' >> /app/start.sh && \
    echo '}' >> /app/start.sh && \
    echo 'EOF' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo '# 启动服务' >> /app/start.sh && \
    echo 'cd /app/webp-processor && python3 -c "from utils.utils import cleanup_temp_files; cleanup_temp_files()" && gunicorn --config gunicorn.conf.py wsgi:application &' >> /app/start.sh && \
    echo 'cd /app/backend && java -jar app.jar --spring.config.location=file:/app/backend/config/application.properties &' >> /app/start.sh && \
    echo 'nginx -g "daemon off;"' >> /app/start.sh && \
    chmod +x /app/start.sh

# 设置默认环境变量
ENV REDIS_DATABASE=0 \
    REDIS_TIMEOUT=60000 \
    CHAR_ART_CACHE_TTL=3600 \
    CHAR_ART_CACHE_DEFAULT_KEY_PREFIX="char-art:text:" \
    WEBP_PROCESSOR_ENABLED=true \
    WEBP_PROCESSOR_CONNECTION_TIMEOUT=600000 \
    WEBP_PROCESSOR_MAX_RETRIES=2 \
    MAX_FILE_SIZE=10MB \
    MAX_REQUEST_SIZE=10MB \
    MAX_UPLOAD_SIZE=10 \
    LOG_LEVEL=INFO \
    BASE_PATH="" \
    # 后端日志配置
    LOG_FILE_MAX_SIZE=10MB \
    LOG_FILE_MAX_HISTORY=30 \
    # 字符画默认配置
    DEFAULT_DENSITY=medium \
    DEFAULT_COLOR_MODE=grayscale \
    # Python WebP处理器配置
    DEBUG=False \
    MAX_CONTENT_LENGTH=16777216 \
    TEMP_FILE_TTL=3600 \
    # Gunicorn配置
    GUNICORN_WORKERS=4 \
    GUNICORN_TIMEOUT=120 \
    GUNICORN_MAX_REQUESTS=1000 \
    GUNICORN_MAX_REQUESTS_JITTER=50

# 暴露端口
EXPOSE 80

# 定义卷
VOLUME ["/data", "/app/backend/data", "/app/backend/logs", "/app/webp-processor/data", "/app/webp-processor/logs"]

# 设置入口点
CMD ["/app/start.sh"]