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

# 阶段4: 最终镜像
FROM openjdk:11-jre-slim

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    nginx \
    python3 \
    python3-pip \
    netcat \
    curl \
    redis-server \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/backend/config \
    && mkdir -p /app/backend/data \
    && mkdir -p /app/webp-processor/data \
    && mkdir -p /app/webp-processor/logs

# 复制后端构建产物
COPY --from=backend-build /app/backend/target/*.jar /app/backend/app.jar
COPY backend/docker-entrypoint.sh /app/backend/
COPY backend/src/main/resources/application.properties /app/backend/config/application.properties.template
RUN chmod +x /app/backend/docker-entrypoint.sh

# 复制前端构建产物
COPY --from=frontend-build /app/frontend/dist /usr/share/nginx/html
COPY frontend/docker-entrypoint.sh /app/frontend/
RUN chmod +x /app/frontend/docker-entrypoint.sh

# 复制Python WebP处理器
COPY --from=webp-processor-build /app/webp-processor /app/webp-processor
COPY python_webp_processor/docker-entrypoint.sh /app/webp-processor/
RUN chmod +x /app/webp-processor/docker-entrypoint.sh

# 复制启动脚本
COPY docker-start.sh /app/
RUN chmod +x /app/docker-start.sh

# 设置默认环境变量
ENV REDIS_DATABASE=0 \
    REDIS_TIMEOUT=60000 \
    CHAR_ART_CACHE_TTL=3600 \
    WEBP_PROCESSOR_CONNECTION_TIMEOUT=5000 \
    WEBP_PROCESSOR_MAX_RETRIES=2 \
    MAX_FILE_SIZE=10MB \
    MAX_REQUEST_SIZE=10MB \
    LOG_LEVEL=INFO \
    BASE_PATH="" \
    # Python WebP处理器配置
    DEBUG=False \
    MAX_CONTENT_LENGTH=16777216 \
    TEMP_FILE_TTL=3600

# 暴露端口
EXPOSE 80

# 设置入口点
ENTRYPOINT ["/app/docker-start.sh"]