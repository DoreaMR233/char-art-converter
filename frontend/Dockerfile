# 使用Node.js官方镜像作为构建环境
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 配置npm使用淘宝镜像
RUN npm config set registry https://registry.npmmirror.com

# 复制包管理文件
COPY package*.json ./

# 安装项目依赖
RUN npm install

# 复制项目文件
COPY . .


# 如果提供了构建参数，则更新.env.production文件中的相关变量
# 声明所有可能的构建参数
# .env.example 中的变量
ARG VITE_APP_TITLE
ARG VITE_APP_VERSION
ARG VITE_API_BASE_PATH
ARG VITE_MAX_UPLOAD_SIZE
# .env.production.example 中的变量
ARG VITE_API_URL
ARG VITE_BASE_PATH
ARG VITE_DEBUG
ARG VITE_SOURCEMAP

# 如果提供了构建参数，则更新相应的环境变量文件
RUN if [ -f .env.production ]; then \
    if [ -n "$VITE_API_URL" ]; then \
        sed -i "/^VITE_API_URL=/c\VITE_API_URL=$VITE_API_URL" .env.production; \
    fi; \
    if [ -n "$VITE_BASE_PATH" ]; then \
        sed -i "/^VITE_BASE_PATH=/c\VITE_BASE_PATH=$VITE_BASE_PATH" .env.production; \
    fi; \
    if [ -n "$VITE_DEBUG" ]; then \
        sed -i "/^VITE_DEBUG=/c\VITE_DEBUG=$VITE_DEBUG" .env.production; \
    fi; \
    if [ -n "$VITE_SOURCEMAP" ]; then \
        sed -i "/^VITE_SOURCEMAP=/c\VITE_SOURCEMAP=$VITE_SOURCEMAP" .env.production; \
    fi; \
    fi

# 处理 .env 文件中的通用变量（如果存在）
RUN if [ -f .env ]; then \
    if [ -n "$VITE_APP_TITLE" ]; then \
        sed -i "/^VITE_APP_TITLE=/c\VITE_APP_TITLE=$VITE_APP_TITLE" .env; \
    fi; \
    if [ -n "$VITE_APP_VERSION" ]; then \
        sed -i "/^VITE_APP_VERSION=/c\VITE_APP_VERSION=$VITE_APP_VERSION" .env; \
    fi; \
    if [ -n "$VITE_API_BASE_PATH" ]; then \
        sed -i "/^VITE_API_BASE_PATH=/c\VITE_API_BASE_PATH=$VITE_API_BASE_PATH" .env; \
    fi; \
    if [ -n "$VITE_MAX_UPLOAD_SIZE" ]; then \
        sed -i "/^VITE_MAX_UPLOAD_SIZE=/c\VITE_MAX_UPLOAD_SIZE=$VITE_MAX_UPLOAD_SIZE" .env; \
    fi; \
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