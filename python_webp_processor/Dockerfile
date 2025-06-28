FROM python:3.9-slim AS build

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 运行阶段
FROM python:3.9-slim

# 设置工作目录
WORKDIR /app

# 从构建阶段复制安装好的Python包和可执行文件
COPY --from=build /usr/local/lib/python3.9/site-packages /usr/local/lib/python3.9/site-packages
COPY --from=build /usr/local/bin/uvicorn /usr/local/bin/

# 安装curl用于健康检查，安装tzdata用于时区设置
RUN apt-get clean && apt-get update && apt-get install -y curl tzdata && rm -rf /var/lib/apt/lists/*

# 复制应用代码
COPY . .

# 添加配置脚本
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# 设置默认环境变量
ENV LOG_FILE_PATH=/app/logs \
    TEMP_DIR_PATH=/app/data \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# ENV PORT=5000 \
#     LOG_LEVEL=INFO \
#     TEMP_FILE_TTL=3600 \
#     DEBUG=False \
#     MAX_CONTENT_LENGTH=10485760 \
#     REDIS_HOST=localhost \
#     REDIS_PORT=6379 \
#     REDIS_DB=0 \
#     REDIS_PASSWORD= \
#     PROGRESS_UPDATE_INTERVAL=0.5 \
#     JAVA_BACKEND_URL=http://localhost:8080


# 创建数据目录
RUN mkdir -p $TEMP_DIR_PATH
RUN mkdir -p $LOG_FILE_PATH

# 复制配置文件到临时目录
COPY ./.env  /app/.env.template

# 设置数据卷
VOLUME $TEMP_DIR_PATH
VOLUME $LOG_FILE_PATH


# 暴露端口
EXPOSE 8081

# 设置入口点
ENTRYPOINT ["/app/docker-entrypoint.sh"]