# 使用Node.js官方镜像作为构建环境
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制包管理文件
COPY package*.json ./

# 安装项目依赖
RUN npm install

# 复制项目文件
COPY . .


# 如果提供了VITE_BASE_PATH环境变量，则更新.env.production文件
ARG VITE_BASE_PATH
ARG BACKEND_HOST="http://localhost"
ARG BACKEND_PORT=8080
ARG API_URL="${BACKEND_HOST}:${BACKEND_PORT}"
RUN if [ -f .env.production ]; then \
    sed -i "/^VITE_BASE_PATH=/c\VITE_BASE_PATH=$VITE_BASE_PATH" .env.production; \
    sed -i "/^VITE_API_URL=/c\VITE_API_URL=$API_URL" .env.production; \
    fi

# 构建生产版本
# 使用生产环境配置
RUN npm run build

# 使用Nginx作为生产服务器
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制环境变量文件
COPY --from=builder /app/.env /app/.env
COPY --from=builder /app/.env.production /app/.env.production

# 复制自定义Nginx配置文件
COPY nginx.conf /nginx.conf

# 复制启动脚本
COPY docker-entrypoint.sh /docker-entrypoint.sh

# 确保脚本可执行
RUN chmod +x /docker-entrypoint.sh

# 设置环境变量
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# 暴露端口
EXPOSE 80

# 使用启动脚本替代直接启动Nginx
ENTRYPOINT ["/docker-entrypoint.sh"]

# 启动Nginx服务
CMD ["nginx", "-g", "daemon off;"]