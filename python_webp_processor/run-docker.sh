#!/bin/bash

# 字符画转换器 WebP处理服务 Docker 快速启动脚本

# 颜色定义
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m" # 无颜色

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker未安装。请先安装Docker: https://docs.docker.com/get-docker/${NC}"
    exit 1
fi

# 显示欢迎信息
echo -e "${GREEN}=== 字符画转换器 WebP处理服务 Docker 快速启动脚本 ===${NC}"
echo -e "${YELLOW}此脚本将构建并启动WebP处理服务Docker容器${NC}"
echo

# 设置变量
IMAGE_NAME="webp-processor"
CONTAINER_NAME="webp-processor"
HOST_PORT=8081
CONTAINER_PORT=5000

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

    # 配置环境变量
    echo -e "${GREEN}[4/5] 配置环境变量...${NC}"
    echo -e "${YELLOW}请为每个环境变量输入值，或直接按回车使用默认值${NC}"
    echo

    read -p "服务监听端口 (默认: 5000): " PORT
    PORT=${PORT:-5000}

    read -p "日志级别 (DEBUG, INFO, WARNING, ERROR, CRITICAL) (默认: INFO): " LOG_LEVEL
    LOG_LEVEL=${LOG_LEVEL:-INFO}

    read -p "调试模式 (默认: False): " DEBUG
    DEBUG=${DEBUG:-False}

    read -p "最大上传文件大小（字节） (默认: 16777216): " MAX_CONTENT_LENGTH
    MAX_CONTENT_LENGTH=${MAX_CONTENT_LENGTH:-16777216}

    read -p "临时文件保留时间（秒） (默认: 3600): " TEMP_FILE_TTL
    TEMP_FILE_TTL=${TEMP_FILE_TTL:-3600}

    # 启动容器
    echo -e "${GREEN}[5/5] 启动WebP处理服务容器...${NC}"
    docker run -d \
        --name "${CONTAINER_NAME}" \
        -p "${HOST_PORT}:${PORT}" \
        -v webp-processor-data:/app/data \
        -v webp-processor-logs:/app/logs \
        --network char-art-network \
        -e PORT="${PORT}" \
        -e LOG_LEVEL="${LOG_LEVEL}" \
        -e DEBUG="${DEBUG}" \
        -e MAX_CONTENT_LENGTH="${MAX_CONTENT_LENGTH}" \
        -e TEMP_FILE_TTL="${TEMP_FILE_TTL}" \
        "${IMAGE_NAME}:latest"

    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 启动容器失败${NC}"
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
echo -e "${GREEN}[*] 等待服务启动...${NC}"
sleep 3

# 检查服务健康状态
echo -e "${YELLOW}检查服务健康状态...${NC}"
MAX_RETRIES=10
RETRIES=0

if [ "$USE_DOCKER_RUN" = true ]; then
    HEALTH_CHECK_URL="http://localhost:$HOST_PORT/api/health"
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if curl -s $HEALTH_CHECK_URL | grep -q "ok"; then
            echo -e "${GREEN}服务已成功启动!${NC}"
            echo -e "${GREEN}WebP处理服务地址: http://localhost:$HOST_PORT${NC}"
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
else
    HEALTH_CHECK_URL="http://localhost:8081/api/health"
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if curl -s $HEALTH_CHECK_URL | grep -q "ok"; then
            echo -e "${GREEN}服务已成功启动!${NC}"
            echo -e "${GREEN}WebP处理服务地址: http://localhost:8081${NC}"
            echo -e "${GREEN}健康检查: $HEALTH_CHECK_URL${NC}"
            echo
            echo -e "${YELLOW}常用命令:${NC}"
            echo -e "  查看日志: ${GREEN}docker-compose logs${NC}"
            echo -e "  停止服务: ${GREEN}docker-compose down${NC}"
            echo -e "  启动服务: ${GREEN}docker-compose up -d${NC}"
            echo
            echo -e "${YELLOW}更多配置选项请参考Docker.md文档${NC}"
            exit 0
        fi
        
        echo -e "${YELLOW}服务正在启动中，请稍候... ($((RETRIES+1))/$MAX_RETRIES)${NC}"
        RETRIES=$((RETRIES+1))
        sleep 2
    done
    
    echo -e "${RED}警告: 服务可能未正常启动，请检查日志:${NC}"
    echo -e "${GREEN}docker-compose logs${NC}"
    exit 1
fi