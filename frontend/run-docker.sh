#!/bin/bash

# 字符画转换器前端Docker快速启动脚本 (Linux/macOS版)

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # 无颜色


# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker未安装。请先安装Docker: https://docs.docker.com/get-docker/${NC}"
    exit 1
fi

# 显示欢迎信息
echo -e "${GREEN}=== 字符画转换器前端Docker快速启动脚本 ===${NC}"
echo -e "${YELLOW}此脚本将构建并启动字符画转换器前端服务${NC}"
echo

# 设置变量
IMAGE_NAME="char-art-frontend"
CONTAINER_NAME="char-art-frontend"
HOST_PORT=8080
CONTAINER_PORT=80

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

    # 资源路径前缀配置
    echo -e "${YELLOW}资源路径前缀配置${NC}"
    echo "用于在非根路径部署时设置资源路径，例如部署在 http://example.com/char-art/"
    read -p "资源路径前缀 (默认: 空): " BASE_PATH
    BASE_PATH=${BASE_PATH:-""}

    # 项目后端地址配置
    echo -e "${YELLOW}项目后端地址配置${NC}"
    echo "用于与后端服务进行通信"
    read -p "项目后端地址 (默认: http://localhost:8080): " API_URL
    API_URL=${API_URL:-"http://localhost:8080"}

    # 启动容器
    echo -e "${GREEN}[5/5] 启动字符画转换器前端容器...${NC}"
    docker run -d \
        --name "${CONTAINER_NAME}" \
        -p "${HOST_PORT}:${CONTAINER_PORT}" \
        --network char-art-network \
        -e BASE_PATH="${BASE_PATH}" \
        -e API_URL="${API_URL}" \
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
sleep 5

# 检查服务是否正常运行
echo -e "${YELLOW}服务健康检查...${NC}"
MAX_RETRIES=10
RETRIES=0

if [ "$USE_DOCKER_RUN" = true ]; then
    HEALTH_CHECK_URL="http://localhost:${HOST_PORT}"
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        response=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_CHECK_URL)
        if [ "$response" = "200" ]; then
            echo -e "${GREEN}服务已成功启动!${NC}"
            echo -e "${GREEN}前端地址: http://localhost:${HOST_PORT}${NC}"
            if [ -n "$BASE_PATH" ]; then
                echo -e "${GREEN}应用路径: http://localhost:${HOST_PORT}/${BASE_PATH}${NC}"
            else
                echo -e "${GREEN}应用路径: http://localhost:${HOST_PORT}${NC}"
            fi
            echo
            echo -e "${YELLOW}常用命令:${NC}"
            echo -e "  查看日志: ${GREEN}docker logs ${CONTAINER_NAME}${NC}"
            echo -e "  停止服务: ${GREEN}docker stop ${CONTAINER_NAME}${NC}"
            echo -e "  启动服务: ${GREEN}docker start ${CONTAINER_NAME}${NC}"
            echo -e "  删除容器: ${GREEN}docker rm ${CONTAINER_NAME}${NC}"
            echo
            echo -e "${YELLOW}更多配置选项请参考Docker.md文档${NC}"
            exit 0
        fi
        
        echo -e "${YELLOW}等待服务启动...($((RETRIES+1))/$MAX_RETRIES)${NC}"
        RETRIES=$((RETRIES+1))
        sleep 2
    done
    
    echo -e "${RED}警告: 服务可能未正常启动，请检查日志:${NC}"
    echo -e "${GREEN}docker logs ${CONTAINER_NAME}${NC}"
    exit 1
else
    # Docker Compose 模式下的健康检查
    HEALTH_CHECK_URL="http://localhost:8080"
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        response=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_CHECK_URL)
        if [ "$response" = "200" ]; then
            echo -e "${GREEN}服务已成功启动!${NC}"
            echo -e "${GREEN}前端地址: http://localhost:8080${NC}"
            echo
            echo -e "${YELLOW}常用命令:${NC}"
            echo -e "  查看日志: ${GREEN}docker-compose logs${NC}"
            echo -e "  停止服务: ${GREEN}docker-compose down${NC}"
            echo -e "  启动服务: ${GREEN}docker-compose up -d${NC}"
            echo
            echo -e "${YELLOW}更多配置选项请参考Docker.md文档${NC}"
            exit 0
        fi
        
        echo -e "${YELLOW}等待服务启动...($((RETRIES+1))/$MAX_RETRIES)${NC}"
        RETRIES=$((RETRIES+1))
        sleep 2
    done
    
    echo -e "${RED}警告: 服务可能未正常启动，请检查日志:${NC}"
    echo -e "${GREEN}docker-compose logs${NC}"
    exit 1
fi