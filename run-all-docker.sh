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
echo -e "${YELLOW}1. 使用一体化Dockerfile（推荐，单容器模式）${NC}"
echo -e "${YELLOW}2. 使用Docker Compose（多容器模式）${NC}"
echo

read -p "请输入选择（1或2）: " CHOICE

if [ "$CHOICE" = "1" ]; then
    # 使用一体化Dockerfile
    echo -e "${GREEN}[1/3] 使用一体化Dockerfile启动服务...${NC}"
    
    # 检查镜像是否存在
    if ! docker images char-art-converter:latest | grep -q char-art-converter; then
        echo -e "${YELLOW}镜像不存在，正在构建...${NC}"
        docker build -t char-art-converter:latest .
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}错误: 构建镜像失败${NC}"
            exit 1
        fi
    fi
    
    # 检查容器是否已存在
    if docker ps -a --format "{{.Names}}" | grep -q "char-art-app"; then
        echo -e "${YELLOW}容器已存在，正在停止并移除...${NC}"
        docker stop char-art-app > /dev/null 2>&1
        docker rm char-art-app > /dev/null 2>&1
    fi
    
    # 启动容器
    docker run -d --name char-art-app -p 80:80 char-art-converter:latest
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 启动容器失败${NC}"
        exit 1
    fi
    
else
    # 使用Docker Compose
    # 检查Docker Compose是否安装
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}错误: Docker Compose未安装。请先安装Docker Compose: https://docs.docker.com/compose/install/${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}[1/3] 使用Docker Compose启动服务...${NC}"
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

if [ "$CHOICE" = "1" ]; then
    # 一体化Dockerfile模式下的健康检查
    BACKEND_URL="http://localhost/api/health"
    
    echo -e "${YELLOW}检查服务健康状态...${NC}"
    MAX_RETRIES=10
    RETRIES=0
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if curl -s $BACKEND_URL > /dev/null; then
            echo -e "${GREEN}服务已成功启动!${NC}"
            break
        fi
        
        RETRIES=$((RETRIES+1))
        if [ $RETRIES -ge $MAX_RETRIES ]; then
            echo -e "${RED}警告: 服务可能未正常启动，请检查日志:${NC}"
            echo -e "${GREEN}docker logs char-art-app${NC}"
        else
            echo -e "${YELLOW}服务正在启动中，请稍候... ($RETRIES/$MAX_RETRIES)${NC}"
            sleep 2
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
    # 检查后端服务
    echo -e "${YELLOW}检查后端服务...${NC}"
    MAX_RETRIES=10
    RETRIES=0
    BACKEND_URL="http://localhost:8080/api/health"
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if curl -s $BACKEND_URL > /dev/null; then
            echo -e "${GREEN}后端服务已成功启动!${NC}"
            break
        fi
        
        RETRIES=$((RETRIES+1))
        if [ $RETRIES -ge $MAX_RETRIES ]; then
            echo -e "${RED}警告: 后端服务可能未正常启动，请检查日志:${NC}"
            echo -e "${GREEN}docker logs char-art-backend${NC}"
        else
            echo -e "${YELLOW}后端服务正在启动中，请稍候... ($RETRIES/$MAX_RETRIES)${NC}"
            sleep 2
        fi
    done
    
    # 检查WebP处理服务
    echo -e "${YELLOW}检查WebP处理服务...${NC}"
    MAX_RETRIES=10
    RETRIES=0
    WEBP_URL="http://localhost:8081/api/health"
    
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if curl -s $WEBP_URL | grep -q "ok"; then
            echo -e "${GREEN}WebP处理服务已成功启动!${NC}"
            break
        fi
        
        RETRIES=$((RETRIES+1))
        if [ $RETRIES -ge $MAX_RETRIES ]; then
            echo -e "${RED}警告: WebP处理服务可能未正常启动，请检查日志:${NC}"
            echo -e "${GREEN}docker logs webp-processor${NC}"
        else
            echo -e "${YELLOW}WebP处理服务正在启动中，请稍候... ($RETRIES/$MAX_RETRIES)${NC}"
            sleep 2
        fi
    done
    
    echo
    echo -e "${GREEN}服务地址:${NC}"
    echo -e "${GREEN}后端服务: http://localhost:8080${NC}"
    echo -e "${GREEN}WebP处理服务: http://localhost:8081${NC}"
    echo
    echo -e "${YELLOW}常用命令:${NC}"
    echo -e "  查看所有容器: ${GREEN}docker ps${NC}"
    echo -e "  查看后端日志: ${GREEN}docker logs char-art-backend${NC}"
    echo -e "  查看WebP处理服务日志: ${GREEN}docker logs webp-processor${NC}"
    echo -e "  停止所有服务: ${GREEN}docker-compose down${NC}"
    echo -e "  重启所有服务: ${GREEN}docker-compose restart${NC}"
    echo
    echo -e "${YELLOW}更多配置选项请参考Docker.md文档${NC}"
fi