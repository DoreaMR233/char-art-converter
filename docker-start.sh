#!/bin/sh
set -e

# 打印欢迎信息
echo "=================================================="
echo "      字符画转换器 - 一体化Docker容器启动      "
echo "=================================================="

#上传文件最大大小，默认是10，单位为MB
MAX_UPLOAD_SIZE=${MAX_UPLOAD_SIZE:-10}

# 创建必要的目录
echo "[1/5] 创建必要的目录，对系统进行配置设置..."
mkdir -p $DEFAULT_CONFIG_PATH
mkdir -p $DEFAULT_TEMP_PATH
mkdir -p $BACKEND_LOG_FILE_PATH
mkdir -p $TEMP_DIR_PATH
mkdir -p $WEBP_LOG_FILE_PATH
mkdir -p $FRONTEND_PATH
mkdir -p $SUPERVISORD_LOG_PATH
mkdir -p $SUPERVISORD_PID_PATH
mkdir -p $SUPERVISORD_CONFIG_PATH
echo "目录创建完成"

# 设置系统时区，默认为亚洲/上海
# 可通过环境变量 TIMEZONE 修改应用程序时区配置
# 可通过环境变量 DATETIME_FORMAT 修改日期时间格式
TIMEZONE=${TIMEZONE:-Asia/Shanghai}
echo "设置时区为${TIMEZONE}..."
ln -sf /usr/share/zoneinfo/${TIMEZONE} /etc/localtime
echo "${TIMEZONE}" > /etc/timezone

# Redis服务将由supervisord管理
echo "[2/5] 配置Redis服务..."
echo "Redis将由supervisord管理，配置为在$REDIS_HOST:$REDIS_PORT运行"

# 配置WebP处理器
echo "[3/5] 配置WebP处理器服务..."
cd $WEBP_PROCESSOR


# 配置文件路径
TEMPLATE_WEBP_PROCESSOR_ENV_FILE="$WEBP_PROCESSOR/.env.template"
WEBP_PROCESSOR_ENV_FILE="$WEBP_PROCESSOR/.env"

# 复制模板配置文件
cp "$TEMPLATE_WEBP_PROCESSOR_ENV_FILE" "$WEBP_PROCESSOR_ENV_FILE"

# 根据环境变量覆盖配置

# 设置服务端口
if [ ! -z "$PORT" ]; then
  sed -i "/^PORT=/c\PORT=$PORT" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置日志级别
if [ ! -z "$WEBP_PROCESSOR_LOG_LEVEL" ]; then
  sed -i "/^LOG_LEVEL=/c\LOG_LEVEL=$WEBP_PROCESSOR_LOG_LEVEL" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置日志路径
if [ ! -z "$WEBP_LOG_FILE_PATH" ]; then
  sed -i "/^LOG_FILE=/c\LOG_FILE=$WEBP_LOG_FILE_PATH\/webp-processor.log" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置临时文件保留时间
if [ ! -z "$TEMP_FILE_TTL" ]; then
  sed -i "/^TEMP_FILE_TTL=/c\TEMP_FILE_TTL=$TEMP_FILE_TTL" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置调试模式
if [ ! -z "$DEBUG" ]; then
  sed -i "/^DEBUG=/c\DEBUG=$DEBUG" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置最大上传文件大小
if [ ! -z "$MAX_CONTENT_LENGTH" ]; then
  result=$(($MAX_UPLOAD_SIZE*1024*1024))
  sed -i "/^MAX_CONTENT_LENGTH=/c\MAX_CONTENT_LENGTH=$result" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置Redis主机
if [ ! -z "$REDIS_HOST" ]; then
  sed -i "/^REDIS_HOST=/c\REDIS_HOST=$REDIS_HOST" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置Redis端口
if [ ! -z "$REDIS_PORT" ]; then
  sed -i "/^REDIS_PORT=/c\REDIS_PORT=$REDIS_PORT" "$WEBP_PROCESSOR_ENV_FILE"
fi

if [ ! -z "$REDIS_DB" ]; then
  sed -i "/^REDIS_DB=/c\REDIS_DB=$REDIS_DB" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置Redis密码
if [ ! -z "$REDIS_PASSWORD" ]; then
  sed -i "/^REDIS_PASSWORD=/c\REDIS_PASSWORD=$REDIS_PASSWORD" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置进度间隔时间
if [ ! -z "$PROGRESS_UPDATE_INTERVAL" ]; then
  sed -i "/^PROGRESS_UPDATE_INTERVAL=/c\PROGRESS_UPDATE_INTERVAL=$PROGRESS_UPDATE_INTERVAL" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 设置临时文件目录
if [ ! -z "$TEMP_DIR_PATH" ]; then
  if ! grep -q "^TEMP_DIR=" "$WEBP_PROCESSOR_ENV_FILE"; then
    echo "" >> "$WEBP_PROCESSOR_ENV_FILE"
    echo "TEMP_DIR=$TEMP_DIR_PATH" >> "$WEBP_PROCESSOR_ENV_FILE"
  else
    sed -i "/^TEMP_DIR=/c\TEMP_DIR=$TEMP_DIR_PATH" "$WEBP_PROCESSOR_ENV_FILE"
  fi
fi

# 设置时区
if [ ! -z "$TIMEZONE" ]; then
  sed -i "/^TIMEZONE=/c\TIMEZONE=$TIMEZONE" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 后端地址
if [ ! -z "$JAVA_BACKEND_URL" ]; then
  sed -i "/^JAVA_BACKEND_URL=/c\JAVA_BACKEND_URL=$JAVA_BACKEND_URL" "$WEBP_PROCESSOR_ENV_FILE"
fi

# 显示WebP处理器配置信息
echo "WebP处理服务配置:"
echo "-------------------"
echo "配置文件路径: $WEBP_PROCESSOR_ENV_FILE"
echo "-------------------"
echo "配置文件内容:"
echo "-------------------"
# 读取并打印配置文件内容，过滤掉注释行和空行
grep -v "^#" "$WEBP_PROCESSOR_ENV_FILE" | grep -v "^$"
echo "-------------------"

# 清理过期的临时文件
python3 -c "from utils.utils import cleanup_temp_files; cleanup_temp_files()"

# WebP处理器将由supervisord管理
echo "WebP处理器服务将由supervisord管理，配置为在端口: $PORT 运行"

# 配置后端
echo "[4/5] 配置后端服务..."
cd $BACKEND_PATH

# 配置文件路径
TEMPLATE_BACKEND_CONFIG_FILE="$DEFAULT_CONFIG_PATH/application.properties.template"
BACKEND_CONFIG_FILE="$DEFAULT_CONFIG_PATH/application.properties"

# 复制模板配置文件
cp "$TEMPLATE_BACKEND_CONFIG_FILE" "$BACKEND_CONFIG_FILE"

# 自定义Redis配置
if [ ! -z "$REDIS_HOST" ]; then
  sed -i "s/spring.redis.host=.*/spring.redis.host=$REDIS_HOST/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$REDIS_PORT" ]; then
  sed -i "s/spring.redis.port=.*/spring.redis.port=$REDIS_PORT/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$REDIS_DATABASE" ]; then
  sed -i "s/spring.redis.database=.*/spring.redis.database=$REDIS_DATABASE/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$REDIS_TIMEOUT" ]; then
  sed -i "s/spring.redis.timeout=.*/spring.redis.timeout=$REDIS_TIMEOUT/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$REDIS_PASSWORD" ]; then
  sed -i "s/spring.redis.password=.*/spring.redis.password=$REDIS_PASSWORD/g" "$BACKEND_CONFIG_FILE"
fi

# 自定义字符画缓存配置
if [ ! -z "$CHAR_ART_CACHE_TTL" ]; then
  sed -i "s/char-art.cache.ttl=.*/char-art.cache.ttl=$CHAR_ART_CACHE_TTL/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_CACHE_DEFAULT_KEY_PREFIX" ]; then
  sed -i "s/char-art.cache.default_key_prefix=.*/char-art.cache.default_key_prefix=$CHAR_ART_CACHE_DEFAULT_KEY_PREFIX/g" "$BACKEND_CONFIG_FILE"
fi

# 自定义WebP处理服务配置
if [ ! -z "$WEBP_PROCESSOR_URL" ]; then
  sed -i "s|webp-processor.url=.*|webp-processor.url=$WEBP_PROCESSOR_URL|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$WEBP_PROCESSOR_ENABLED" ]; then
  sed -i "s/webp-processor.enabled=.*/webp-processor.enabled=$WEBP_PROCESSOR_ENABLED/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$WEBP_PROCESSOR_CONNECTION_TIMEOUT" ]; then
  sed -i "s/webp-processor.connection-timeout=.*/webp-processor.connection-timeout=$WEBP_PROCESSOR_CONNECTION_TIMEOUT/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$WEBP_PROCESSOR_MAX_RETRIES" ]; then
  sed -i "s/webp-processor.max-retries=.*/webp-processor.max-retries=$WEBP_PROCESSOR_MAX_RETRIES/g" "$BACKEND_CONFIG_FILE"
fi

# 自定义服务器端口
if [ ! -z "$SERVER_PORT" ]; then
  sed -i "s/server.port=.*/server.port=$SERVER_PORT/g" "$BACKEND_CONFIG_FILE"
fi

# 自定义上传文件配置
if [ ! -z "$MAX_UPLOAD_SIZE" ]; then
  MAX_FILE_SIZE="${MAX_UPLOAD_SIZE}MB"
  sed -i "s/spring.servlet.multipart.max-file-size=.*/spring.servlet.multipart.max-file-size=$MAX_FILE_SIZE/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$MAX_UPLOAD_SIZE" ]; then
  MAX_REQUEST_SIZE="${MAX_UPLOAD_SIZE}MB"
  sed -i "s/spring.servlet.multipart.max-request-size=.*/spring.servlet.multipart.max-request-size=$MAX_REQUEST_SIZE/g" "$BACKEND_CONFIG_FILE"
fi

# 自定义日志级别
if [ ! -z "$BACKEND_LOG_LEVEL" ]; then
  sed -i "s/logging.level.com.doreamr233.charartconverter=.*/logging.level.com.doreamr233.charartconverter=$BACKEND_LOG_LEVEL/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$BACKEND_LOG_FILE_PATH" ]; then
  sed -i "s|logging.file.name=.*|logging.file.name=$BACKEND_LOG_FILE_PATH/char-art-converter.log|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$LOG_FILE_MAX_SIZE" ]; then
  sed -i "s/logging.logback.rollingpolicy.max-file-size=.*/logging.logback.rollingpolicy.max-file-size=$LOG_FILE_MAX_SIZE/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$LOG_FILE_MAX_HISTORY" ]; then
  sed -i "s/logging.logback.rollingpolicy.max-history=.*/logging.logback.rollingpolicy.max-history=$LOG_FILE_MAX_HISTORY/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$LOG_CHARSET_CONSOLE" ]; then
  sed -i "s/logging.charset.console=.*/logging.charset.console=$LOG_CHARSET_CONSOLE/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$LOG_CHARSET_FILE" ]; then
  sed -i "s/logging.charset.file=.*/logging.charset.file=$LOG_CHARSET_FILE/g" "$BACKEND_CONFIG_FILE"
fi

# 自定义字符画默认配置
if [ ! -z "$DEFAULT_DENSITY" ]; then
  sed -i "s/char-art.default-density=.*/char-art.default-density=$DEFAULT_DENSITY/g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$DEFAULT_COLOR_MODE" ]; then
  sed -i "s/char-art.default-color-mode=.*/char-art.default-color-mode=$DEFAULT_COLOR_MODE/g" "$BACKEND_CONFIG_FILE"
fi

# 设置临时文件目录为数据卷目录
if [ ! -z "$DEFAULT_TEMP_PATH" ]; then
  sed -i "s|char-art.temp-directory=.*|char-art.temp-directory=$DEFAULT_TEMP_PATH|g" "$BACKEND_CONFIG_FILE"
fi

# 自定义时区配置
if [ ! -z "$TIMEZONE" ]; then
  sed -i "s|spring.jackson.time-zone=.*|spring.jackson.time-zone=$TIMEZONE|g" "$BACKEND_CONFIG_FILE"
fi

# 自定义日期时间格式
if [ ! -z "$DATETIME_FORMAT" ]; then
  sed -i "s|spring.mvc.format.date-time=.*|spring.mvc.format.date-time=$DATETIME_FORMAT|g" "$BACKEND_CONFIG_FILE"
fi

# 自定义并行处理配置
if [ ! -z "$CHAR_ART_PARALLEL_MAX_FRAME_THREADS" ]; then
  sed -i "s|char-art.parallel.max-frame-threads=.*|char-art.parallel.max-frame-threads=$CHAR_ART_PARALLEL_MAX_FRAME_THREADS|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_PARALLEL_THREAD_POOL_FACTOR" ]; then
  sed -i "s|char-art.parallel.thread-pool-factor=.*|char-art.parallel.thread-pool-factor=$CHAR_ART_PARALLEL_THREAD_POOL_FACTOR|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_PARALLEL_MIN_THREADS" ]; then
  sed -i "s|char-art.parallel.min-threads=.*|char-art.parallel.min-threads=$CHAR_ART_PARALLEL_MIN_THREADS|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_PARALLEL_PROGRESS_UPDATE_INTERVAL" ]; then
  sed -i "s|char-art.parallel.progress-update-interval=.*|char-art.parallel.progress-update-interval=$CHAR_ART_PARALLEL_PROGRESS_UPDATE_INTERVAL|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_PARALLEL_PIXEL_PROGRESS_INTERVAL" ]; then
  sed -i "s|char-art.parallel.pixel-progress-interval=.*|char-art.parallel.pixel-progress-interval=$CHAR_ART_PARALLEL_PIXEL_PROGRESS_INTERVAL|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_PARALLEL_TASK_TIMEOUT" ]; then
  sed -i "s|char-art.parallel.task-timeout=.*|char-art.parallel.task-timeout=$CHAR_ART_PARALLEL_TASK_TIMEOUT|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_PARALLEL_PROGRESS_CLEANUP_DELAY" ]; then
  sed -i "s|char-art.parallel.progress-cleanup-delay=.*|char-art.parallel.progress-cleanup-delay=$CHAR_ART_PARALLEL_PROGRESS_CLEANUP_DELAY|g" "$BACKEND_CONFIG_FILE"
fi

# 自定义临时文件清理配置
if [ ! -z "$CHAR_ART_TEMP_FILE_MAX_RETENTION_HOURS" ]; then
  sed -i "s|char-art.temp-file.max-retention-hours=.*|char-art.temp-file.max-retention-hours=$CHAR_ART_TEMP_FILE_MAX_RETENTION_HOURS|g" "$BACKEND_CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_TEMP_FILE_CLEANUP_ENABLED" ]; then
  sed -i "s|char-art.temp-file.cleanup-enabled=.*|char-art.temp-file.cleanup-enabled=$CHAR_ART_TEMP_FILE_CLEANUP_ENABLED|g" "$BACKEND_CONFIG_FILE"
fi

# 自定义Java系统临时目录配置
if [ ! -z "$DEFAULT_TEMP_PATH" ]; then
  sed -i "s|java.io.tmpdir=.*|java.io.tmpdir=$DEFAULT_TEMP_PATH|g" "$BACKEND_CONFIG_FILE"
fi

# 自定义multipart临时文件位置配置
if [ ! -z "$DEFAULT_TEMP_PATH" ]; then
  sed -i "s|spring.servlet.multipart.location=.*|spring.servlet.multipart.location=$DEFAULT_TEMP_PATH|g" "$BACKEND_CONFIG_FILE"
fi

# 显示配置信息
echo "使用以下配置启动char-art-converter："
echo "-------------------"
echo "配置文件路径: $BACKEND_CONFIG_FILE"
echo "-------------------"
echo "配置文件内容:"
echo "-------------------"
# 读取并打印配置文件内容，过滤掉注释行和空行
grep -v "^#" "$BACKEND_CONFIG_FILE" | grep -v "^$"
echo "-------------------"

# 后端服务将由supervisord管理
echo "后端服务将由supervisord管理，配置为在端口: $SERVER_PORT 运行"

# 配置前端
echo "[5/5] 配置前端..."
cd $FRONTEND_PATH

# 定义HTML文件路径
HTML_DIR="/usr/share/nginx/html"
HTML_FILE="$HTML_DIR/index.html"
NGINX_CONF="/etc/nginx/nginx.conf"

# 定义环境变量文件路径
FRONTEND_ENV_FILE="$FRONTEND_PATH/.env"
FRONTEND_ENV_PROD_FILE="$FRONTEND_PATH/.env.production"

# 加载.env文件中的环境变量
if [ -f "$FRONTEND_ENV_FILE" ]; then
  echo "正在加载 .env 文件中的环境变量..."
  # 创建临时文件，过滤掉注释行和空行
  grep -v "^#" "$FRONTEND_ENV_FILE" | grep -v "^$" > /tmp/env_vars
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
if [ -f "$FRONTEND_ENV_PROD_FILE" ]; then
  echo "正在加载 .env.production 文件中的环境变量..."
  # 创建临时文件，过滤掉注释行和空行
  grep -v "^#" "$FRONTEND_ENV_PROD_FILE" | grep -v "^$" > /tmp/env_prod_vars
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
BASE_PATH_VALUE=${VITE_BASE_PATH:-$BASE_PATH}

# 优先使用VITE_API_URL，如果未设置则使用API_URL环境变量
API_URL_VALUE=${VITE_API_URL:-$API_URL}

# 优先使用VITE_API_BASE_PATH，如果未设置则使用默认值/api
API_BASE_PATH_VALUE=${VITE_API_BASE_PATH:-"/api"}


# 检查是否设置了BASE_PATH环境变量
# if [ -n "$BASE_PATH_VALUE" ]; then
#   echo "配置前端资源路径前缀: /$BASE_PATH_VALUE/"
  
#   # 使用sed替换index.html中的所有资源路径前缀
#   # 将"/assets/替换为/$BASE_PATH_VALUE/assets/
#   sed -i "s|/assets/|/$BASE_PATH_VALUE/assets/|g" $HTML_FILE
  
#   # 替换CSS和JS文件中的资源路径
#   if [ -d "$HTML_DIR/assets" ]; then
#     # 查找所有CSS和JS文件
#     find "$HTML_DIR/assets" -type f \( -name "*.css" -o -name "*.js" \) | while read -r file; do
#       # 替换文件中的资源路径
#       sed -i "s|/assets/|/$BASE_PATH_VALUE/assets/|g" "$file"
#     done
#   fi
  
#   echo "资源路径前缀配置成功: /$BASE_PATH_VALUE/"
# else
#   echo "未设置BASE_PATH环境变量，将使用默认路径"
# fi

# 输出API相关信息
echo "API地址: $API_URL_VALUE"
echo "API基础路径: $API_BASE_PATH_VALUE"
echo "上传文件最大大小: ${MAX_UPLOAD_SIZE}MB"

sleep 2

# 配置Nginx
echo "配置Nginx服务..."

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
  
  sed -i "s|\${MAX_UPLOAD_SIZE_VALUE}|$MAX_UPLOAD_SIZE|g" $NGINX_CONF
  echo "Nginx配置文件中的MAX_UPLOAD_SIZE已替换为: $MAX_UPLOAD_SIZE"
fi

# 创建Redis配置文件
mkdir -p /etc/redis
echo "daemonize no" > /etc/redis/redis.conf
echo "Redis配置文件已创建，设置为非守护进程模式"

# 使用envsubst替换supervisord.conf中的环境变量
envsubst < /app/supervisord.conf > /etc/supervisor/supervisord.conf

# 替换supervisord.conf中的环境变量
if [ -f "$SUPERVISORD_CONFIG_PATH/supervisord.conf" ]; then
  echo "配置supervisord环境变量..."
  
  sed -i "s|\${SUPERVISORD_LOG_PATH}|$SUPERVISORD_LOG_PATH|g" $SUPERVISORD_CONFIG_PATH/supervisord.conf
  echo "supervisord配置文件中的SUPERVISORD_LOG_PATH已替换为: $SUPERVISORD_LOG_PATH"
  
  sed -i "s|\${SUPERVISORD_PID_PATH}|$SUPERVISORD_PID_PATH|g" $SUPERVISORD_CONFIG_PATH/supervisord.conf
  echo "supervisord配置文件中的SUPERVISORD_PID_PATH已替换为: $SUPERVISORD_PID_PATH"
  
  sed -i "s|\${WEBP_PROCESSOR}|$WEBP_PROCESSOR|g" $SUPERVISORD_CONFIG_PATH/supervisord.conf
  echo "supervisord配置文件中的WEBP_PROCESSOR已替换为: $WEBP_PROCESSOR"
  
  sed -i "s|\${DEFAULT_CONFIG_PATH}|$DEFAULT_CONFIG_PATH|g" $SUPERVISORD_CONFIG_PATH/supervisord.conf
  echo "supervisord配置文件中的DEFAULT_CONFIG_PATH已替换为: $DEFAULT_CONFIG_PATH"
  
  sed -i "s|\${BACKEND_PATH}|$BACKEND_PATH|g" $SUPERVISORD_CONFIG_PATH/supervisord.conf
  echo "supervisord配置文件中的BACKEND_PATH已替换为: $BACKEND_PATH"
  
  sed -i "s|\${DEFAULT_TEMP_PATH}|$DEFAULT_TEMP_PATH|g" $SUPERVISORD_CONFIG_PATH/supervisord.conf
  echo "supervisord配置文件中的DEFAULT_TEMP_PATH已替换为: $DEFAULT_TEMP_PATH"
  
  sed -i "s|\${LANG}|$LANG|g" $SUPERVISORD_CONFIG_PATH/supervisord.conf
  echo "supervisord配置文件中的LANG已替换为: $LANG"
fi



# 启动supervisord管理所有服务
echo "=================================================="
echo "      启动supervisord管理所有服务      "
echo "=================================================="
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
