#!/bin/sh

# 设置错误时退出
set -e

# 定义HTML文件路径
HTML_DIR="/usr/share/nginx/html"
HTML_FILE="$HTML_DIR/index.html"
NGINX_CONF="/etc/nginx/nginx.conf"

# 定义环境变量文件路径
ENV_FILE="/app/.env"
ENV_PROD_FILE="/app/.env.production"

# 加载.env文件中的环境变量
if [ -f "$ENV_FILE" ]; then
  echo "正在加载 .env 文件中的环境变量..."
  # 创建临时文件，过滤掉注释行和空行
  grep -v "^#" "$ENV_FILE" | grep -v "^$" > /tmp/env_vars
  while IFS="=" read -r key value; do
    # 去除可能的引号
    value=$(echo $value | sed -e "s/^[\"\']//" -e "s/[\"\']$//")
    # 导出环境变量
    export "$key=$value"
    echo "从 .env 加载: $key=$value"
  done < /tmp/env_vars
  rm /tmp/env_vars
fi

# 加载.env.production文件中的环境变量（优先级更高）
if [ -f "$ENV_PROD_FILE" ]; then
  echo "正在加载 .env.production 文件中的环境变量..."
  # 创建临时文件，过滤掉注释行和空行
  grep -v "^#" "$ENV_PROD_FILE" | grep -v "^$" > /tmp/env_prod_vars
  while IFS="=" read -r key value; do
    # 去除可能的引号
    value=$(echo $value | sed -e "s/^[\"\']//" -e "s/[\"\']$//")
    # 导出环境变量
    export "$key=$value"
    echo "从 .env.production 加载: $key=$value"
  done < /tmp/env_prod_vars
  rm /tmp/env_prod_vars
fi

# 优先使用VITE_BASE_PATH，如果未设置则使用BASE_PATH环境变量
BASE_PATH_VALUE=${VITE_BASE_PATH:-${BASE_PATH:-""}}

# 优先使用VITE_API_URL，如果未设置则使用API_URL环境变量
API_URL_VALUE=${VITE_API_URL:-${API_URL:-"http://localhost:8080"}}

# 优先使用VITE_API_BASE_PATH，如果未设置则使用默认值/api
API_BASE_PATH_VALUE=${VITE_API_BASE_PATH:-"/api"}

# 优先使用VITE_MAX_UPLOAD_SIZE，如果未设置则使用MAX_UPLOAD_SIZE环境变量，默认为10
MAX_UPLOAD_SIZE_VALUE=${VITE_MAX_UPLOAD_SIZE:-${MAX_UPLOAD_SIZE:-10}}

# 检查是否设置了BASE_PATH环境变量
#if [ -n "$BASE_PATH_VALUE" ]; then
#  echo "正在配置资源路径前缀: /$BASE_PATH_VALUE/"
#
#  # 使用sed替换index.html中的所有资源路径前缀
#  # 将"/assets/替换为/$BASE_PATH_VALUE/assets/
#  sed -i "s|/assets/|/$BASE_PATH_VALUE/assets/|g" $HTML_FILE
#
#  # 替换CSS和JS文件中的资源路径
#  if [ -d "$HTML_DIR/assets" ]; then
#    # 查找所有CSS和JS文件
#    find "$HTML_DIR/assets" -type f \( -name "*.css" -o -name "*.js" \) | while read -r file; do
#      # 替换文件中的资源路径
#      sed -i "s|/assets/|/$BASE_PATH_VALUE/assets/|g" "$file"
#    done
#  fi
#
#  echo "资源路径前缀配置成功: /$BASE_PATH_VALUE/"
#else
#  echo "未设置BASE_PATH环境变量，将使用默认路径"
#fi

# 输出API相关信息
echo "API地址: $API_URL_VALUE"
echo "API基础路径: $API_BASE_PATH_VALUE"

# 复制自定义的nginx配置文件
if [ -f "/nginx.conf" ]; then
  echo "使用自定义Nginx配置文件"
  cp /nginx.conf $NGINX_CONF
  
  # 替换nginx.conf中的环境变量
  sed -i "s|\${BASE_PATH_VALUE}|$BASE_PATH_VALUE|g" $NGINX_CONF
  echo "Nginx配置文件中的BASE_PATH已替换为: $BASE_PATH_VALUE"

  sed -i "s|\${API_URL_VALUE}|$API_URL_VALUE|g" $NGINX_CONF
  echo "Nginx配置文件中的API_URL已替换为: $API_URL_VALUE"

  sed -i "s|\${API_BASE_PATH_VALUE}|$API_BASE_PATH_VALUE|g" $NGINX_CONF
  echo "Nginx配置文件中的API_BASE_PATH已替换为: $API_BASE_PATH_VALUE"
  
  sed -i "s|\${MAX_UPLOAD_SIZE_VALUE}|$MAX_UPLOAD_SIZE_VALUE|g" $NGINX_CONF
  echo "Nginx配置文件中的MAX_UPLOAD_SIZE已替换为: $MAX_UPLOAD_SIZE_VALUE"
fi

# 执行CMD命令（启动Nginx）
exec "$@"