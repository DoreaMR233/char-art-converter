# 多阶段构建Dockerfile

# 阶段1: 构建后端
FROM maven:3.8.4-openjdk-11-slim AS backend-build

# 设置工作目录
WORKDIR /app

# 配置Maven使用阿里云镜像
RUN mkdir -p /root/.m2 \
    && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">\n\
    <mirrors>\n\
        <mirror>\n\
            <id>aliyunmaven</id>\n\
            <mirrorOf>*</mirrorOf>\n\
            <name>阿里云公共仓库</name>\n\
            <url>https://maven.aliyun.com/repository/public</url>\n\
        </mirror>\n\
    </mirrors>\n\
</settings>' > /root/.m2/settings.xml

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
WORKDIR /app

# 配置npm使用淘宝镜像
RUN npm config set registry https://registry.npmmirror.com

# 复制包管理文件
COPY frontend/package*.json ./

# 安装项目依赖
RUN npm install

# 复制项目文件
COPY frontend/ .

# 确保.env文件存在
# RUN if [ ! -f .env ]; then \
#     echo "# 默认环境变量配置" > .env && \
#     echo "VITE_APP_TITLE=字符画转换器" >> .env && \
#     echo "VITE_APP_VERSION=1.0.0" >> .env && \
#     echo "VITE_API_BASE_PATH=/api" >> .env && \
#     echo "VITE_MAX_UPLOAD_SIZE=10" >> .env; \
#     fi

# 如果提供了VITE_BASE_PATH环境变量，则更新.env.production文件
ARG VITE_BASE_PATH
ARG BACKEND_PORT=8080
ARG API_URL="http://127.0.0.1:${BACKEND_PORT}"
RUN if [ -f .env.production ]; then \
    sed -i "/^VITE_BASE_PATH=/c\VITE_BASE_PATH=$VITE_BASE_PATH" .env.production; \
    sed -i "/^VITE_API_URL=/c\VITE_API_URL=$API_URL" .env.production; \
    fi

# 构建生产版本
RUN npm run build

# # 阶段3: 构建Python WebP处理器
# FROM python:3.9-slim AS webp-processor-build

# WORKDIR /app

# # 安装依赖
# COPY python_webp_processor/requirements.txt .
# RUN pip install --no-cache-dir -r requirements.txt

# 阶段4: 最终镜像 - 使用Ubuntu
FROM ubuntu:20.04

# 避免交互式提示
ENV DEBIAN_FRONTEND=noninteractive

# 配置apt以使用更可靠的镜像并添加重试逻辑
RUN sed -i 's/http:\/\/security.ubuntu.com\/ubuntu/http:\/\/archive.ubuntu.com\/ubuntu/g' /etc/apt/sources.list \
    && echo 'Acquire::Retries "5";' > /etc/apt/apt.conf.d/80retries

# 安装必要的工具和依赖
# 添加Python 3.9 PPA源
RUN apt-get update --option Acquire::Retries=5 \
    && apt-get install -y --no-install-recommends \
    software-properties-common \
    && add-apt-repository ppa:deadsnakes/ppa -y \
    && apt-get update --option Acquire::Retries=5 \
    && apt-get install -y --no-install-recommends \
    openjdk-11-jre-headless \
    nginx \
    python3.9 \
    python3.9-distutils \
    python3.9-dev \
    netcat \
    curl \
    tzdata \
    redis-server \
    redis-tools \
    fontconfig \
    libfreetype6 \
    supervisor \
    wget \
    && curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py \
    && python3.9 get-pip.py \
    && rm get-pip.py \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf /usr/bin/python3.9 /usr/bin/python \
    && ln -sf /usr/bin/python3.9 /usr/bin/python3

# 设置工作目录
WORKDIR /app

# 设置默认环境变量
ARG BACKEND_PORT=8080
#前端、后端、Flask端文件夹位置
ENV BACKEND_PATH=/app/backend \
    FRONTEND_PATH=/app/frontend \
    WEBP_PROCESSOR=/app/webp-processor \
    TIMEZONE=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    # TEST环境变量无用，仅方便注解
    TEST=TEST
    

# 前端、后端、Flask端日志文件夹和临时文件文件夹位置
ENV BACKEND_LOG_FILE_PATH=$BACKEND_PATH/logs \
    DEFAULT_TEMP_PATH=$BACKEND_PATH/data \
    DEFAULT_CONFIG_PATH=$BACKEND_PATH/config \
    WEBP_LOG_FILE_PATH=$WEBP_PROCESSOR/logs \
    TEMP_DIR_PATH=$WEBP_PROCESSOR/data \
    SUPERVISORD_LOG_PATH=/var/log \
    SUPERVISORD_PID_PATH=/var/run \
    SUPERVISORD_CONFIG_PATH=/etc/supervisor/conf.d

# 前端、后端、Flask端的端口和路径配置
ENV REDIS_HOST=localhost \
    REDIS_PORT=6379 \
    SERVER_PORT=${BACKEND_PORT} \
    PORT=8081
ENV WEBP_PROCESSOR_URL=http://localhost:$PORT \
    JAVA_BACKEND_URL=http://localhost:${BACKEND_PORT}

# ENV REDIS_DATABASE=0 \
#     REDIS_TIMEOUT=60000 \
#     CHAR_ART_CACHE_TTL=3600 \
#     CHAR_ART_CACHE_DEFAULT_KEY_PREFIX="char-art:text:" \
#     WEBP_PROCESSOR_ENABLED=true \
#     WEBP_PROCESSOR_CONNECTION_TIMEOUT=600000 \
#     WEBP_PROCESSOR_MAX_RETRIES=2 \
#     MAX_FILE_SIZE=${MAX_UPLOAD_SIZE}MB \
#     MAX_REQUEST_SIZE=${MAX_UPLOAD_SIZE}MB \
#     BACKEND_LOG_LEVEL=INFO \
#     # 后端日志配置
#     LOG_FILE_MAX_SIZE=10MB \
#     LOG_FILE_MAX_HISTORY=30 \
#     LOG_CHARSET_CONSOLE=UTF-8 \
#     LOG_CHARSET_FILE=UTF-8 \
#     DATETIME_FORMAT=yyyy-MM-dd HH:mm:ss \
#     # 字符画默认配置
#     DEFAULT_DENSITY=medium \
#     DEFAULT_COLOR_MODE=grayscale \
#     # 前端配置
#     VITE_BASE_PATH= \
#     VITE_API_URL=http://127.0.0.1:$SERVER_PORT \
#     VITE_API_BASE_PATH=/api \
#     VITE_MAX_UPLOAD_SIZE=${MAX_UPLOAD_SIZE} \
#     # Python WebP处理器配置
#     WEBP_PROCESSOR_LOG_LEVEL=INFO \
#     DEBUG=False \
#     MAX_CONTENT_LENGTH=${MAX_UPLOAD_SIZE}*1024*1024 \
#     TEMP_FILE_TTL=3600 \
#     REDIS_CHANNEL=sse \
#     PROGRESS_UPDATE_INTERVAL=0.5

# 创建必要的目录
RUN mkdir -p $DEFAULT_CONFIG_PATH \
    && mkdir -p $DEFAULT_TEMP_PATH \
    && mkdir -p $BACKEND_LOG_FILE_PATH \
    && mkdir -p $TEMP_DIR_PATH \
    && mkdir -p $WEBP_LOG_FILE_PATH \
    && mkdir -p $FRONTEND_PATH

# 复制后端构建产物
COPY --from=backend-build /app/target/*.jar $BACKEND_PATH/app.jar
COPY backend/src/main/resources/application.properties $DEFAULT_CONFIG_PATH/application.properties.template

# 复制前端构建产物
COPY --from=frontend-build /app/dist /usr/share/nginx/html
COPY --from=frontend-build /app/.env $FRONTEND_PATH/.env
COPY --from=frontend-build /app/.env.production $FRONTEND_PATH/.env.production
COPY frontend/docker-entrypoint.sh $FRONTEND_PATH/docker-entrypoint.sh
RUN chmod +x $FRONTEND_PATH/docker-entrypoint.sh

# 复制Python WebP处理器
COPY python_webp_processor/ $WEBP_PROCESSOR/
COPY python_webp_processor/docker-entrypoint.sh $WEBP_PROCESSOR/docker-entrypoint.sh
COPY python_webp_processor/.env $WEBP_PROCESSOR/.env
COPY python_webp_processor/.env  $WEBP_PROCESSOR/.env.template
RUN chmod +x $WEBP_PROCESSOR/docker-entrypoint.sh

#复制Nginx配置文件模版
COPY nginx.conf /nginx.conf

# 安装依赖
COPY python_webp_processor/requirements.txt $WEBP_PROCESSOR/requirements.txt
RUN pip install --no-cache-dir -r $WEBP_PROCESSOR/requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 复制启动脚本和supervisord配置文件
COPY docker-start.sh /app/start.sh
COPY supervisord.conf /app/supervisord.conf
RUN chmod +x /app/start.sh

# 暴露端口
EXPOSE 80

# 定义卷
VOLUME $DEFAULT_TEMP_PATH
VOLUME $BACKEND_LOG_FILE_PATH
VOLUME $TEMP_DIR_PATH
VOLUME $WEBP_LOG_FILE_PATH


# 设置入口点
CMD ["/app/start.sh"]