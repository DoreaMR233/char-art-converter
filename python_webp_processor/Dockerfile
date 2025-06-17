FROM python:3.9-slim AS build

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 运行阶段
FROM python:3.9-slim

# 设置工作目录
WORKDIR /app

# 从构建阶段复制安装好的Python包
COPY --from=build /usr/local/lib/python3.9/site-packages /usr/local/lib/python3.9/site-packages

# 复制应用代码
COPY . .

# 添加配置脚本
COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

# 设置默认环境变量
ENV PORT=5000 \
    LOG_LEVEL=INFO \
    DEBUG=False \
    MAX_CONTENT_LENGTH=16777216 \
    TEMP_FILE_TTL=3600

# 创建数据目录
RUN mkdir -p /app/data
RUN mkdir -p /app/logs

# 设置数据卷
VOLUME ["/app/data", "/app/logs"]

# 暴露端口
EXPOSE 5000

# 设置入口点
ENTRYPOINT ["/app/docker-entrypoint.sh"]