#!/bin/bash

# 字符画转换器后端Docker快速启动脚本 (Linux/macOS版)

# 颜色定义
GREEN="\033[0;32m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
NC="\033[0m" # No Color

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker未安装。请先安装Docker: https://docs.docker.com/get-docker/${NC}"
    exit 1
fi

# 显示欢迎信息
echo -e "${GREEN}=== 字符画转换器后端Docker快速启动脚本 ===${NC}"
echo -e "${YELLOW}此脚本将构建并启动字符画转换器后端服务${NC}"
echo

# 设置变量
IMAGE_NAME="char-art-backend"
CONTAINER_NAME="char-art-backend"
HOST_PORT=8080
CONTAINER_PORT=8080

# 显示选择菜单
echo -e "${GREEN}请选择启动方式:${NC}"
echo -e "${YELLOW}1. 使用Docker Run（单容器模式）${NC}"
echo -e "${YELLOW}2. 使用Docker Compose（多容器模式）${NC}"
echo

read -p "请输入选择（1或2）: " CHOICE

if [ "$CHOICE" = "1" ]; then
    USE_DOCKER_RUN=true
elif [ "$CHOICE" = "2" ]; then
    USE_DOCKER_RUN=false
else
    echo -e "${RED}错误: 无效的选择，请输入1或2${NC}"
    exit 1
fi

if [ "$USE_DOCKER_RUN" = true ]; then
    # 构建Docker镜像
    echo -e "${GREEN}[1/5] 构建Docker镜像...${NC}"
    docker build -t "${IMAGE_NAME}:latest" .

    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 构建Docker镜像失败${NC}"
        exit 1
    fi

    echo -e "${GREEN}[2/5] 检查并停止已存在的容器...${NC}"
    # 检查容器是否已存在，如果存在则停止并删除
    if docker ps -a | grep -q "${CONTAINER_NAME}"; then
        echo -e "${YELLOW}发现已存在的${CONTAINER_NAME}容器，正在停止并删除...${NC}"
        docker stop "${CONTAINER_NAME}" > /dev/null 2>&1
        docker rm "${CONTAINER_NAME}" > /dev/null 2>&1
    fi

    # 检查网络是否存在，如果不存在则创建
    echo -e "${GREEN}[3/5] 检查Docker网络...${NC}"
    if ! docker network ls | grep -q "char-art-network"; then
        echo -e "${YELLOW}创建Docker网络: char-art-network${NC}"
        docker network create char-art-network
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}错误: 创建网络失败${NC}"
            exit 1
        fi
    fi

    # 设置环境变量
    echo -e "${GREEN}[4/5] 配置环境变量...${NC}"
    echo -e "${YELLOW}请为每个环境变量输入值，或直接按回车使用默认值${NC}"
    echo

    # Redis配置
    read -p "Redis服务器地址 (默认: localhost): " REDIS_HOST
    REDIS_HOST=${REDIS_HOST:-localhost}

    read -p "Redis服务器端口 (默认: 6379): " REDIS_PORT
    REDIS_PORT=${REDIS_PORT:-6379}

    read -p "Redis数据库索引 (默认: 0): " REDIS_DATABASE
    REDIS_DATABASE=${REDIS_DATABASE:-0}

    read -p "Redis连接超时时间 (默认: 60000): " REDIS_TIMEOUT
    REDIS_TIMEOUT=${REDIS_TIMEOUT:-60000}

    # 字符画缓存配置
    read -p "缓存过期时间，单位秒 (默认: 3600): " CHAR_ART_CACHE_TTL
    CHAR_ART_CACHE_TTL=${CHAR_ART_CACHE_TTL:-3600}

    read -p "缓存键前缀 (默认: char-art:text:): " CHAR_ART_CACHE_DEFAULT_KEY_PREFIX
    CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=${CHAR_ART_CACHE_DEFAULT_KEY_PREFIX:-"char-art:text:"}

    # WebP处理服务配置
    read -p "WebP处理服务URL (默认: http://localhost:8081): " WEBP_PROCESSOR_URL
    WEBP_PROCESSOR_URL=${WEBP_PROCESSOR_URL:-"http://localhost:8081"}

    read -p "是否启用WebP处理服务 (默认: true): " WEBP_PROCESSOR_ENABLED
    WEBP_PROCESSOR_ENABLED=${WEBP_PROCESSOR_ENABLED:-true}

    read -p "连接超时时间 (默认: 600000): " WEBP_PROCESSOR_CONNECTION_TIMEOUT
    WEBP_PROCESSOR_CONNECTION_TIMEOUT=${WEBP_PROCESSOR_CONNECTION_TIMEOUT:-600000}

    read -p "最大重试次数 (默认: 2): " WEBP_PROCESSOR_MAX_RETRIES
    WEBP_PROCESSOR_MAX_RETRIES=${WEBP_PROCESSOR_MAX_RETRIES:-2}

    # 服务器配置
    read -p "服务器端口 (默认: 8080): " SERVER_PORT
    SERVER_PORT=${SERVER_PORT:-8080}
    HOST_PORT=$SERVER_PORT

    # 上传文件配置
    read -p "最大文件大小 (默认: 10MB): " MAX_FILE_SIZE
    MAX_FILE_SIZE=${MAX_FILE_SIZE:-"10MB"}

    read -p "最大请求大小 (默认: 10MB): " MAX_REQUEST_SIZE
    MAX_REQUEST_SIZE=${MAX_REQUEST_SIZE:-"10MB"}

    # 日志配置
    read -p "日志级别 (默认: INFO): " LOG_LEVEL
    LOG_LEVEL=${LOG_LEVEL:-INFO}

    read -p "日志文件最大大小 (默认: 10MB): " LOG_FILE_MAX_SIZE
    LOG_FILE_MAX_SIZE=${LOG_FILE_MAX_SIZE:-"10MB"}

    read -p "日志文件保留历史数量 (默认: 30): " LOG_FILE_MAX_HISTORY
    LOG_FILE_MAX_HISTORY=${LOG_FILE_MAX_HISTORY:-30}

    # 字符画默认配置
    read -p "默认字符密度 (默认: medium): " DEFAULT_DENSITY
    DEFAULT_DENSITY=${DEFAULT_DENSITY:-medium}

    read -p "默认颜色模式 (默认: grayscale): " DEFAULT_COLOR_MODE
    DEFAULT_COLOR_MODE=${DEFAULT_COLOR_MODE:-grayscale}

    # 启动容器
    echo -e "${GREEN}[5/5] 启动字符画转换器后端容器...${NC}"
    docker run -d --name "${CONTAINER_NAME}" \
        -p "${HOST_PORT}:${CONTAINER_PORT}" \
        -v char-art-data:/app/data \
        -v char-art-logs:/app/logs \
        --network char-art-network \
        -e REDIS_HOST="${REDIS_HOST}" \
        -e REDIS_PORT="${REDIS_PORT}" \
        -e REDIS_DATABASE="${REDIS_DATABASE}" \
        -e REDIS_TIMEOUT="${REDIS_TIMEOUT}" \
        -e CHAR_ART_CACHE_TTL="${CHAR_ART_CACHE_TTL}" \
        -e CHAR_ART_CACHE_DEFAULT_KEY_PREFIX="${CHAR_ART_CACHE_DEFAULT_KEY_PREFIX}" \
        -e WEBP_PROCESSOR_URL="${WEBP_PROCESSOR_URL}" \
        -e WEBP_PROCESSOR_ENABLED="${WEBP_PROCESSOR_ENABLED}" \
        -e WEBP_PROCESSOR_CONNECTION_TIMEOUT="${WEBP_PROCESSOR_CONNECTION_TIMEOUT}" \
        -e WEBP_PROCESSOR_MAX_RETRIES="${WEBP_PROCESSOR_MAX_RETRIES}" \
        -e SERVER_PORT="${SERVER_PORT}" \
        -e MAX_FILE_SIZE="${MAX_FILE_SIZE}" \
        -e MAX_REQUEST_SIZE="${MAX_REQUEST_SIZE}" \
        -e LOG_LEVEL="${LOG_LEVEL}" \
        -e LOG_FILE_MAX_SIZE="${LOG_FILE_MAX_SIZE}" \
        -e LOG_FILE_MAX_HISTORY="${LOG_FILE_MAX_HISTORY}" \
        -e DEFAULT_DENSITY="${DEFAULT_DENSITY}" \
        -e DEFAULT_COLOR_MODE="${DEFAULT_COLOR_MODE}" \
        "${IMAGE_NAME}:latest"

    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 容器启动失败${NC}"
        exit 1
    fi
else
    # 检查Docker Compose是否安装
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}错误: Docker Compose未安装。请先安装Docker Compose: https://docs.docker.com/compose/install/${NC}"
        exit 1
    fi

    echo -e "${GREEN}[1/2] 使用Docker Compose启动服务...${NC}"
    docker-compose up -d

    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 启动服务失败${NC}"
        exit 1
    fi
fi

# 等待服务启动
echo -e "${GREEN}[4/4] 等待服务启动...${NC}"
sleep 3

# 检查服务健康状态
echo -e "${YELLOW}检查服务健康状态...${NC}"
MAX_RETRIES=10
RETRIES=0
HEALTH_CHECK_URL="http://localhost:$HOST_PORT/api/health"

while [ $RETRIES -lt $MAX_RETRIES ]; do
    response=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_CHECK_URL)
    if [ "$response" = "200" ]; then
        echo -e "${GREEN}服务已成功启动!${NC}"
        echo -e "${GREEN}API地址: http://localhost:$HOST_PORT/api${NC}"
        echo -e "${GREEN}健康检查: $HEALTH_CHECK_URL${NC}"
        echo
        echo -e "${YELLOW}常用命令:${NC}"
        echo -e "  查看日志: ${GREEN}docker logs $CONTAINER_NAME${NC}"
        echo -e "  停止服务: ${GREEN}docker stop $CONTAINER_NAME${NC}"
        echo -e "  启动服务: ${GREEN}docker start $CONTAINER_NAME${NC}"
        echo -e "  删除容器: ${GREEN}docker rm $CONTAINER_NAME${NC}"
        echo
        echo -e "${YELLOW}更多配置选项请参考Docker.md文档${NC}"
        exit 0
    fi
    
    echo -e "${YELLOW}服务正在启动中，请稍候... ($((RETRIES+1))/$MAX_RETRIES)${NC}"
    RETRIES=$((RETRIES+1))
    sleep 2
done

echo -e "${RED}警告: 服务可能未正常启动，请检查日志:${NC}"
echo -e "${GREEN}docker logs $CONTAINER_NAME${NC}"
exit 1