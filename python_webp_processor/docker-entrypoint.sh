#!/bin/sh
set -e

# 设置系统时区，默认为亚洲/上海
# 可通过环境变量 TIMEZONE 修改应用程序时区配置
TIMEZONE=${TIMEZONE:-Asia/Shanghai}
echo "设置时区为${TIMEZONE}..."
ln -sf /usr/share/zoneinfo/${TIMEZONE} /etc/localtime
echo "${TIMEZONE}" > /etc/timezone

# 配置文件路径
TEMPLATE_ENV_FILE="/app/.env.template"
ENV_FILE="/app/.env"

# 复制模板配置文件
cp "$TEMPLATE_ENV_FILE" "$ENV_FILE"

# 确保数据目录存在
mkdir -p $LOG_FILE_PATH
mkdir -p $TEMP_DIR_PATH
# 创建日志目录（uvicorn使用标准日志输出）

# 根据环境变量覆盖配置

# 设置服务端口
if [ ! -z "$PORT" ]; then
  sed -i "/^PORT=/c\PORT=$PORT" "$ENV_FILE"
fi

# 设置日志级别
if [ ! -z "$LOG_LEVEL" ]; then
  sed -i "/^LOG_LEVEL=/c\LOG_LEVEL=$LOG_LEVEL" "$ENV_FILE"
fi

# 设置日志路径
if [ ! -z "$LOG_FILE_PATH" ]; then
  sed -i "/^LOG_FILE=/c\LOG_FILE=$LOG_FILE_PATH\/webp-processor.log" "$ENV_FILE"
fi

# 设置临时文件保留时间
if [ ! -z "$TEMP_FILE_TTL" ]; then
  sed -i "/^TEMP_FILE_TTL=/c\TEMP_FILE_TTL=$TEMP_FILE_TTL" "$ENV_FILE"
fi

# 设置调试模式
if [ ! -z "$DEBUG" ]; then
  sed -i "/^DEBUG=/c\DEBUG=$DEBUG" "$ENV_FILE"
fi

# 设置最大上传文件大小
if [ ! -z "$MAX_CONTENT_LENGTH" ]; then
  sed -i "/^MAX_CONTENT_LENGTH=/c\MAX_CONTENT_LENGTH=$MAX_CONTENT_LENGTH" "$ENV_FILE"
fi

# 设置Redis主机
if [ ! -z "$REDIS_HOST" ]; then
  sed -i "/^REDIS_HOST=/c\REDIS_HOST=$REDIS_HOST" "$ENV_FILE"
fi

# 设置Redis端口
if [ ! -z "$REDIS_PORT" ]; then
  sed -i "/^REDIS_PORT=/c\REDIS_PORT=$REDIS_PORT" "$ENV_FILE"
fi

if [ ! -z "$REDIS_DB" ]; then
  sed -i "/^REDIS_DB=/c\REDIS_DB=$REDIS_DB" "$ENV_FILE"
fi

# 设置Redis密码
if [ ! -z "$REDIS_PASSWORD" ]; then
  sed -i "/^REDIS_PASSWORD=/c\REDIS_PASSWORD=$REDIS_PASSWORD" "$ENV_FILE"
fi

# 设置进度间隔时间
if [ ! -z "$PROGRESS_UPDATE_INTERVAL" ]; then
  sed -i "/^PROGRESS_UPDATE_INTERVAL=/c\PROGRESS_UPDATE_INTERVAL=$PROGRESS_UPDATE_INTERVAL" "$ENV_FILE"
fi

# 设置临时文件目录
if [ ! -z "$TEMP_DIR_PATH" ]; then
  if ! grep -q "^TEMP_DIR=" "$ENV_FILE"; then
    echo "" >> "$ENV_FILE"
    echo "TEMP_DIR=$TEMP_DIR_PATH" >> "$ENV_FILE"
  else
    sed -i "/^TEMP_DIR=/c\TEMP_DIR=$TEMP_DIR_PATH" "$ENV_FILE"
  fi
fi

# 设置时区
if [ ! -z "$TIMEZONE" ]; then
  sed -i "/^TIMEZONE=/c\TIMEZONE=$TIMEZONE" "$ENV_FILE"
fi

# 设置进度更新间隔
if [ ! -z "$PROGRESS_UPDATE_INTERVAL" ]; then
  sed -i "/^PROGRESS_UPDATE_INTERVAL=/c\PROGRESS_UPDATE_INTERVAL=$PROGRESS_UPDATE_INTERVAL" "$ENV_FILE"
fi

# 后端地址
if [ ! -z "$JAVA_BACKEND_URL" ]; then
  sed -i "/^JAVA_BACKEND_URL=/c\JAVA_BACKEND_URL=$JAVA_BACKEND_URL" "$ENV_FILE"
fi

# 显示配置信息
echo "WebP处理服务配置:"
echo "-------------------"
echo "配置文件路径: $ENV_FILE"
echo "-------------------"
echo "配置文件内容:"
echo "-------------------"
# 读取并打印配置文件内容，过滤掉注释行和空行
grep -v "^#" "$ENV_FILE" | grep -v "^$"
echo "-------------------"

# 清理过期的临时文件
python3 -c "from utils.utils import cleanup_temp_files; cleanup_temp_files()"

# 启动应用
echo "使用uvicorn启动FastAPI WebP处理服务..."
echo "服务端口: $(grep -E '^PORT=' "$ENV_FILE" | cut -d= -f2)"
echo "调试模式: $(grep -E '^DEBUG=' "$ENV_FILE" | cut -d= -f2)"
echo "日志级别: $(grep -E '^LOG_LEVEL=' "$ENV_FILE" | cut -d= -f2)"
echo "-------------------"
exec python main.py