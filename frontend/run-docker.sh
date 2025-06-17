#!/bin/bash

# 字符画转换器前端Docker快速启动脚本

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
echo -e "${GREEN}=== 字符画转换器前端Docker快速启动脚本 ===${NC}"
echo -e "${YELLOW}此脚本将构建并启动字符画转换器前端服务${NC}"
echo

# 设置变量
IMAGE_NAME="char-art-frontend"
CONTAINER_NAME="char-art-frontend"
HOST_PORT=8080
CONTAINER_PORT=80
BASE_PATH="/char-art/"

# 构建Docker镜像
echo -e "${GREEN}[1/4] 构建Docker镜像...${NC}"
docker build -t $IMAGE_NAME:latest .

if [ $? -ne 0 ]; then
    echo -e "${RED}错误: 构建Docker镜像失败${NC}"
    exit 1
fi

echo -e "${GREEN}[2/4] 检查并停止已存在的容器...${NC}"
# 检查容器是否已存在，如果存在则停止并删除
if docker ps -a | grep -q $CONTAINER_NAME; then
    echo -e "${YELLOW}发现已存在的$CONTAINER_NAME容器，正在停止并删除...${NC}"
    docker stop $CONTAINER_NAME > /dev/null 2>&1
    docker rm $CONTAINER_NAME > /dev/null 2>&1
fi

# 创建网络（如果不存在）
if ! docker network ls | grep -q char-art-network; then
    echo -e "${YELLOW}创建Docker网络: char-art-network${NC}"
    docker network create char-art-network
fi

# 启动容器
echo -e "${GREEN}[3/4] 启动字符画转换器前端容器...${NC}"
docker run -d \
    --name $CONTAINER_NAME \
    -p $HOST_PORT:$CONTAINER_PORT \
    -e BASE_PATH=$BASE_PATH \
    --network char-art-network \
    $IMAGE_NAME:latest

if [ $? -ne 0 ]; then
    echo -e "${RED}错误: 启动容器失败${NC}"
    exit 1
fi

# 等待服务启动
echo -e "${GREEN}[4/4] 等待服务启动...${NC}"
sleep 3

# 检查服务健康状态
echo -e "${YELLOW}检查服务健康状态...${NC}"
MAX_RETRIES=10
RETRIES=0
HEALTH_CHECK_URL="http://localhost:$HOST_PORT"

while [ $RETRIES -lt $MAX_RETRIES ]; do
    response=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_CHECK_URL)
    if [ "$response" = "200" ]; then
        echo -e "${GREEN}服务已成功启动!${NC}"
        echo -e "${GREEN}前端地址: http://localhost:$HOST_PORT${NC}"
        echo -e "${GREEN}应用路径: $BASE_PATH${NC}"
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