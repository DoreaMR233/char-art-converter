#!/bin/bash

# 字符画转换器 完整系统 Docker 快速启动脚本

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
echo -e "${GREEN}=== 字符画转换器 完整系统 Docker 快速启动脚本 ===${NC}"
echo -e "${YELLOW}此脚本将启动字符画转换器的所有服务，包括后端服务和WebP处理服务${NC}"
echo

# 显示选择菜单
echo -e "${GREEN}请选择启动方式:${NC}"
echo -e "${YELLOW}1. 使用Docker Run（单容器模式）${NC}"
echo -e "${YELLOW}2. 使用Docker Compose（多容器模式）${NC}"
echo

read -p "请输入选择（1或2）: " CHOICE

if [ "$CHOICE" = "1" ]; then
    USE_DOCKER_RUN=true
else
    USE_DOCKER_RUN=false
fi

if [ "$USE_DOCKER_RUN" = true ]; then
    # 使用Docker Run
    echo -e "${GREEN}[1/5] 使用Docker Run启动服务...${NC}"
    
    # 检查并创建Docker网络
    echo -e "${GREEN}[2/5] 检查并创建Docker网络...${NC}"
    if ! docker network ls | grep -q "char-art-network"; then
        echo -e "${YELLOW}创建Docker网络: char-art-network${NC}"
        docker network create char-art-network
        if [ $? -ne 0 ]; then
            echo -e "${RED}错误: 创建Docker网络失败${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}Docker网络已存在: char-art-network${NC}"
    fi

    # 设置环境变量
    echo -e "${GREEN}[3/5] 设置环境变量...${NC}"
    
    # 通用配置
    read -p "请输入服务端口 (默认: 80): " SERVER_PORT
    SERVER_PORT=${SERVER_PORT:-80}
    
    read -p "请输入上传文件大小限制 (默认: 10): " UPLOAD_FILE_SIZE_LIMIT
    UPLOAD_FILE_SIZE_LIMIT=${UPLOAD_FILE_SIZE_LIMIT:-10}
    
    read -p "请输入临时文件保留时间(小时) (默认: 24): " TEMP_FILE_EXPIRATION_HOURS
    TEMP_FILE_EXPIRATION_HOURS=${TEMP_FILE_EXPIRATION_HOURS:-24}
    
    # 后端服务配置
    read -p "请输入后端服务端口 (默认: 8080): " BACKEND_PORT
    BACKEND_PORT=${BACKEND_PORT:-8080}
    
    read -p "请输入后端服务日志级别 (默认: info): " BACKEND_LOG_LEVEL
    BACKEND_LOG_LEVEL=${BACKEND_LOG_LEVEL:-info}
    
    read -p "请输入后端服务日志格式 (默认: text): " BACKEND_LOG_FORMAT
    BACKEND_LOG_FORMAT=${BACKEND_LOG_FORMAT:-text}
    
    # Redis配置
    read -p "请输入Redis主机地址 (默认: localhost): " REDIS_HOST
    REDIS_HOST=${REDIS_HOST:-localhost}
    
    read -p "请输入Redis端口 (默认: 6379): " REDIS_PORT
    REDIS_PORT=${REDIS_PORT:-6379}
    
    read -p "请输入Redis密码 (默认为空): " REDIS_PASSWORD
    
    read -p "请输入Redis数据库索引 (默认: 0): " REDIS_DB
    REDIS_DB=${REDIS_DB:-0}
    
    # 字符画缓存配置
    read -p "请输入字符画缓存过期时间(小时) (默认: 24): " CHAR_ART_CACHE_EXPIRATION_HOURS
    CHAR_ART_CACHE_EXPIRATION_HOURS=${CHAR_ART_CACHE_EXPIRATION_HOURS:-24}
    
    # WebP处理器配置
    read -p "请输入WebP处理器端口 (默认: 8081): " WEBP_PROCESSOR_PORT
    WEBP_PROCESSOR_PORT=${WEBP_PROCESSOR_PORT:-8081}
    
    # Python WebP处理器配置
    read -p "请输入Python WebP处理器端口 (默认: 8082): " PYTHON_WEBP_PROCESSOR_PORT
    PYTHON_WEBP_PROCESSOR_PORT=${PYTHON_WEBP_PROCESSOR_PORT:-8082}
    
    # 字符画默认配置
    read -p "请输入默认字符集 (默认: standard): " DEFAULT_CHARSET
    DEFAULT_CHARSET=${DEFAULT_CHARSET:-standard}
    
    read -p "请输入默认字符画宽度 (默认: 80): " DEFAULT_WIDTH
    DEFAULT_WIDTH=${DEFAULT_WIDTH:-80}
    
    read -p "请输入默认字符画高度 (默认: 40): " DEFAULT_HEIGHT
    DEFAULT_HEIGHT=${DEFAULT_HEIGHT:-40}
    
    read -p "请输入默认反色模式 (默认: false): " DEFAULT_NEGATIVE
    DEFAULT_NEGATIVE=${DEFAULT_NEGATIVE:-false}
    
    # 前端配置
    read -p "请输入前端服务端口 (默认: 3000): " FRONTEND_PORT
    FRONTEND_PORT=${FRONTEND_PORT:-3000}
    
    # 检查镜像是否存在
    echo -e "${GREEN}[4/5] 检查并构建镜像...${NC}"
    if ! docker images | grep -q "char-art-app"; then
        echo -e "${YELLOW}构建镜像...${NC}"
        docker build -t char-art-app .
        if [ $? -ne 0 ]; then
            echo -e "${RED}错误: 构建镜像失败${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}镜像已存在，跳过构建${NC}"
    fi

    # 检查容器是否存在并运行
    if docker ps -a | grep -q "char-art-app"; then
        echo -e "${YELLOW}容器已存在，正在停止并删除...${NC}"
        docker stop char-art-app > /dev/null 2>&1
        docker rm char-art-app > /dev/null 2>&1
    fi

    echo -e "${GREEN}[5/5] 启动容器...${NC}"
    docker run -d --name char-art-app \
        --network char-art-network \
        -p ${SERVER_PORT}:80 \
        -e SERVER_PORT=${SERVER_PORT} \
        -e UPLOAD_FILE_SIZE_LIMIT=${UPLOAD_FILE_SIZE_LIMIT} \
        -e TEMP_FILE_EXPIRATION_HOURS=${TEMP_FILE_EXPIRATION_HOURS} \
        -e BACKEND_PORT=${BACKEND_PORT} \
        -e BACKEND_LOG_LEVEL=${BACKEND_LOG_LEVEL} \
        -e BACKEND_LOG_FORMAT=${BACKEND_LOG_FORMAT} \
        -e REDIS_HOST=${REDIS_HOST} \
        -e REDIS_PORT=${REDIS_PORT} \
        -e REDIS_PASSWORD=${REDIS_PASSWORD} \
        -e REDIS_DB=${REDIS_DB} \
        -e CHAR_ART_CACHE_EXPIRATION_HOURS=${CHAR_ART_CACHE_EXPIRATION_HOURS} \
        -e WEBP_PROCESSOR_PORT=${WEBP_PROCESSOR_PORT} \
        -e PYTHON_WEBP_PROCESSOR_PORT=${PYTHON_WEBP_PROCESSOR_PORT} \
        -e DEFAULT_CHARSET=${DEFAULT_CHARSET} \
        -e DEFAULT_WIDTH=${DEFAULT_WIDTH} \
        -e DEFAULT_HEIGHT=${DEFAULT_HEIGHT} \
        -e DEFAULT_NEGATIVE=${DEFAULT_NEGATIVE} \
        -e FRONTEND_PORT=${FRONTEND_PORT} \
        -v "$(pwd)/data:/app/data" \
        char-art-app

    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 启动容器失败${NC}"
        exit 1
    fi
    
else
    # 使用Docker Compose
    echo -e "${GREEN}[1/3] 使用Docker Compose启动服务...${NC}"
    
    # 检查Docker Compose是否安装
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}错误: Docker Compose未安装。请先安装Docker Compose: https://docs.docker.com/compose/install/${NC}"
        exit 1
    fi
    
    # 启动服务
    docker-compose up -d
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 启动服务失败${NC}"
        exit 1
    fi
fi

# 等待服务启动
echo -e "${GREEN}[2/3] 等待服务启动...${NC}"
sleep 5

# 检查服务健康状态
echo -e "${GREEN}[3/3] 检查服务健康状态...${NC}"

if [ "$USE_DOCKER_RUN" = true ]; then
    # Docker Run模式下的健康检查
    BACKEND_URL="http://localhost/api/health"
    
    echo -e "${YELLOW}检查服务健康状态...${NC}"
    MAX_RETRIES=10
    RETRIES=0
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if curl -s -f "$BACKEND_URL" > /dev/null; then
            echo -e "${GREEN}服务已成功启动!${NC}"
            break
        fi
        
        RETRIES=$((RETRIES+1))
        echo -e "${YELLOW}服务正在启动中，请稍候... ($RETRIES/$MAX_RETRIES)${NC}"
        sleep 2
        
        if [ $RETRIES -eq $MAX_RETRIES ]; then
            echo -e "${RED}警告: 服务可能未正常启动，请检查日志:${NC}"
            echo -e "${GREEN}docker logs char-art-app${NC}"
        fi
    done
    
    echo
    echo -e "${GREEN}服务地址:${NC}"
    echo -e "${GREEN}应用前端: http://localhost${NC}"
    echo
    echo -e "${YELLOW}常用命令:${NC}"
    echo -e "  查看容器状态: ${GREEN}docker ps${NC}"
    echo -e "  查看应用日志: ${GREEN}docker logs char-art-app${NC}"
    echo -e "  停止应用: ${GREEN}docker stop char-art-app${NC}"
    echo -e "  重启应用: ${GREEN}docker restart char-art-app${NC}"
    echo
    echo -e "${YELLOW}更多配置选项请参考Docker.md文档${NC}"
else
    # Docker Compose模式下的健康检查
    echo -e "${GREEN}服务已启动，请使用以下命令查看服务状态:${NC}"
    echo -e "${GREEN}docker-compose ps${NC}"
    echo
    echo -e "${YELLOW}常用命令:${NC}"
    echo -e "  查看服务状态: ${GREEN}docker-compose ps${NC}"
    echo -e "  查看服务日志: ${GREEN}docker-compose logs${NC}"
    echo -e "  停止服务: ${GREEN}docker-compose down${NC}"
    echo -e "  重启服务: ${GREEN}docker-compose restart${NC}"
    echo
    echo -e "${YELLOW}更多配置选项请参考Docker.md文档${NC}"
fi