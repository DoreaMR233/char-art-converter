#!/bin/sh
set -e

# 配置文件路径
ENV_FILE="/app/.env"

# 确保数据目录存在
mkdir -p /app/logs
mkdir -p /app/data

# 创建或清空.env文件
echo "# WebP处理服务环境配置 - 由docker-entrypoint.sh生成" > "$ENV_FILE"

# 设置服务端口
if [ ! -z "$PORT" ]; then
  echo "PORT=$PORT" >> "$ENV_FILE"
else
  echo "PORT=5000" >> "$ENV_FILE"
fi

# 设置日志级别
if [ ! -z "$LOG_LEVEL" ]; then
  echo "LOG_LEVEL=$LOG_LEVEL" >> "$ENV_FILE"
else
  echo "LOG_LEVEL=INFO" >> "$ENV_FILE"
fi

# 设置临时文件保留时间
if [ ! -z "$TEMP_FILE_TTL" ]; then
  echo "TEMP_FILE_TTL=$TEMP_FILE_TTL" >> "$ENV_FILE"
else
  echo "TEMP_FILE_TTL=3600" >> "$ENV_FILE"
fi

# 设置调试模式
if [ ! -z "$DEBUG" ]; then
  echo "DEBUG=$DEBUG" >> "$ENV_FILE"
else
  echo "DEBUG=False" >> "$ENV_FILE"
fi

# 设置最大上传文件大小
if [ ! -z "$MAX_CONTENT_LENGTH" ]; then
  echo "MAX_CONTENT_LENGTH=$MAX_CONTENT_LENGTH" >> "$ENV_FILE"
else
  echo "MAX_CONTENT_LENGTH=16777216" >> "$ENV_FILE"
fi

# 设置临时文件目录
echo "TEMP_DIR=/app/data" >> "$ENV_FILE"

# 显示配置信息
echo "WebP处理服务配置:"
echo "-------------------"
cat "$ENV_FILE"
echo "-------------------"

# 设置Gunicorn工作进程数（如果未设置）
if [ ! -z "$GUNICORN_WORKERS" ]; then
  echo "GUNICORN_WORKERS=$GUNICORN_WORKERS" >> "$ENV_FILE"
else
  # 默认为CPU核心数*2+1
  WORKERS=$(( $(nproc) * 2 + 1 ))
  echo "GUNICORN_WORKERS=$WORKERS" >> "$ENV_FILE"
fi

# 设置Gunicorn超时时间（如果未设置）
if [ ! -z "$GUNICORN_TIMEOUT" ]; then
  echo "GUNICORN_TIMEOUT=$GUNICORN_TIMEOUT" >> "$ENV_FILE"
else
  echo "GUNICORN_TIMEOUT=120" >> "$ENV_FILE"
fi

# 清理过期的临时文件
python -c "from utils.utils import cleanup_temp_files; cleanup_temp_files()"

# 启动应用
echo "使用Gunicorn启动WebP处理服务..."
exec gunicorn --config gunicorn.conf.py wsgi:application