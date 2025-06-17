FROM maven:3.8.4-openjdk-11-slim AS build

# 设置工作目录
WORKDIR /app

# 复制pom.xml文件
COPY pom.xml .

# 下载依赖项
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn package -DskipTests

# 运行阶段
FROM openjdk:11-jre-slim

# 安装netcat用于检查Redis连接
RUN apt-get update && apt-get install -y netcat && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 复制构建好的jar包
COPY --from=build /app/target/*.jar app.jar

# 复制配置文件到临时目录
COPY src/main/resources/application.properties /app/config/application.properties.template

# 添加配置脚本
COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

# 设置默认环境变量
ENV REDIS_HOST=localhost \
    REDIS_PORT=6379 \
    REDIS_DATABASE=0 \
    REDIS_TIMEOUT=60000 \
    CHAR_ART_CACHE_TTL=3600 \
    CHAR_ART_CACHE_DEFAULT_KEY_PREFIX="char-art:text:" \
    WEBP_PROCESSOR_URL=http://localhost:8081 \
    WEBP_PROCESSOR_ENABLED=true \
    WEBP_PROCESSOR_CONNECTION_TIMEOUT=600000 \
    WEBP_PROCESSOR_MAX_RETRIES=2 \
    SERVER_PORT=8080 \
    MAX_FILE_SIZE=10MB \
    MAX_REQUEST_SIZE=10MB \
    LOG_LEVEL=INFO \
    LOG_FILE_MAX_SIZE=10MB \
    LOG_FILE_MAX_HISTORY=30 \
    DEFAULT_DENSITY=medium \
    DEFAULT_COLOR_MODE=grayscale

# 创建数据目录和日志目录
RUN mkdir -p /app/data
RUN mkdir -p /app/logs
VOLUME /app/data
VOLUME /app/logs

# 暴露端口
EXPOSE 8080

# 设置入口点
ENTRYPOINT ["/app/docker-entrypoint.sh"]