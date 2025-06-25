FROM maven:3.8.4-openjdk-11-slim AS build

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
COPY pom.xml .

# 下载依赖项
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn package -DskipTests

# 运行阶段
FROM openjdk:11-jre-slim

# 安装netcat用于检查Redis连接以及字体相关依赖，安装tzdata用于时区设置
RUN apt-get update && apt-get install -y netcat fontconfig libfreetype6 tzdata && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 复制构建好的jar包
COPY --from=build /app/target/*.jar app.jar

# 安装curl用于健康检查
RUN apt-get clean && apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 添加配置脚本
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# 设置默认环境变量
ENV DEFAULT_CONFIG_PATH=/app/config \
    DEFAULT_TEMP_PATH=/app/data \
    LOG_FILE_PATH=/app/logs \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# ENV REDIS_HOST=localhost \
#     REDIS_PORT=6379 \
#     REDIS_DATABASE=0 \
#     REDIS_TIMEOUT=60000 \
#     CHAR_ART_CACHE_TTL=3600 \
#     CHAR_ART_CACHE_DEFAULT_KEY_PREFIX="char-art:text:" \
#     WEBP_PROCESSOR_URL=http://localhost:8081 \
#     WEBP_PROCESSOR_ENABLED=true \
#     WEBP_PROCESSOR_CONNECTION_TIMEOUT=600000 \
#     WEBP_PROCESSOR_MAX_RETRIES=2 \
#     SERVER_PORT=8080 \
#     MAX_FILE_SIZE=10MB \
#     MAX_REQUEST_SIZE=10MB \
#     LOG_LEVEL=INFO \
#     LOG_FILE_MAX_SIZE=10MB \
#     LOG_FILE_MAX_HISTORY=30 \
#     LOG_CHARSET_CONSOLE=UTF-8 \
#     LOG_CHARSET_FILE=UTF-8 \
#     TIMEZONE=Asia/Shanghai \
#     DATETIME_FORMAT=yyyy-MM-dd HH:mm:ss \
#     DEFAULT_DENSITY=medium \
#     DEFAULT_COLOR_MODE=grayscale
    

# 复制配置文件到临时目录
COPY src/main/resources/application.properties $DEFAULT_CONFIG_PATH/application.properties.template

# 创建数据目录和日志目录
RUN mkdir -p $DEFAULT_TEMP_PATH
RUN mkdir -p $LOG_FILE_PATH

# 设置挂载卷
VOLUME $DEFAULT_TEMP_PATH
VOLUME $LOG_FILE_PATH

# 暴露端口
EXPOSE 8080

# 设置入口点
ENTRYPOINT ["/app/docker-entrypoint.sh"]