#!/bin/sh
set -e

# 设置系统时区，默认为亚洲/上海
# 可通过环境变量 TIMEZONE 修改应用程序时区配置
# 可通过环境变量 DATETIME_FORMAT 修改日期时间格式
TIMEZONE=${TIMEZONE:-Asia/Shanghai}
echo "设置时区为${TIMEZONE}..."
ln -sf /usr/share/zoneinfo/${TIMEZONE} /etc/localtime
echo "${TIMEZONE}" > /etc/timezone

# 配置文件路径
TEMPLATE_CONFIG_FILE="$DEFAULT_CONFIG_PATH/application.properties.template"
CONFIG_FILE="$DEFAULT_CONFIG_PATH/application.properties"


# 确保配置目录和数据目录存在
mkdir -p $DEFAULT_CONFIG_PATH
mkdir -p $DEFAULT_TEMP_PATH
mkdir -p $LOG_FILE_PATH

# 复制模板配置文件
cp "$TEMPLATE_CONFIG_FILE" "$CONFIG_FILE"

# 等待Redis服务可用（如果指定了REDIS_HOST）
if [ ! -z "$REDIS_HOST" ] && [ "$REDIS_HOST" != "localhost" ]; then
  echo "正在等待Redis：$REDIS_HOST:$REDIS_PORT 启动……"
  timeout=30
  while ! nc -z $REDIS_HOST $REDIS_PORT && [ $timeout -gt 0 ]; do
    echo "Redis不可用，等待中……"
    sleep 1
    timeout=$((timeout-1))
  done
  
  if [ $timeout -le 0 ]; then
    echo "警告: 无法连接到Redis： $REDIS_HOST:$REDIS_PORT 。应用程序可能无法正常运行。"
  else
    echo "Redis 在 $REDIS_HOST:$REDIS_PORT 运行"
  fi
fi

# 自定义Redis配置
if [ ! -z "$REDIS_HOST" ]; then
  sed -i "s|spring.redis.host=.*|spring.redis.host=$REDIS_HOST|g" "$CONFIG_FILE"
fi

if [ ! -z "$REDIS_PORT" ]; then
  sed -i "s|spring.redis.port=.*|spring.redis.port=$REDIS_PORT|g" "$CONFIG_FILE"
fi

if [ ! -z "$REDIS_DATABASE" ]; then
  sed -i "s|spring.redis.database=.*|spring.redis.database=$REDIS_DATABASE|g" "$CONFIG_FILE"
fi

if [ ! -z "$REDIS_TIMEOUT" ]; then
  sed -i "s|spring.redis.timeout=.*|spring.redis.timeout=$REDIS_TIMEOUT|g" "$CONFIG_FILE"
fi

# 自定义字符画缓存配置
if [ ! -z "$CHAR_ART_CACHE_TTL" ]; then
  sed -i "s|char-art.cache.ttl=.*|char-art.cache.ttl=$CHAR_ART_CACHE_TTL|g" "$CONFIG_FILE"
fi

if [ ! -z "$CHAR_ART_CACHE_DEFAULT_KEY_PREFIX" ]; then
  sed -i "s|char-art.cache.default_key_prefix=.*|char-art.cache.default_key_prefix=$CHAR_ART_CACHE_DEFAULT_KEY_PREFIX|g" "$CONFIG_FILE"
fi

# 自定义WebP处理服务配置
if [ ! -z "$WEBP_PROCESSOR_URL" ]; then
  sed -i "s|webp-processor.url=.*|webp-processor.url=$WEBP_PROCESSOR_URL|g" "$CONFIG_FILE"
fi

if [ ! -z "$WEBP_PROCESSOR_ENABLED" ]; then
  sed -i "s|webp-processor.enabled=.*|webp-processor.enabled=$WEBP_PROCESSOR_ENABLED|g" "$CONFIG_FILE"
fi

if [ ! -z "$WEBP_PROCESSOR_CONNECTION_TIMEOUT" ]; then
  sed -i "s|webp-processor.connection-timeout=.*|webp-processor.connection-timeout=$WEBP_PROCESSOR_CONNECTION_TIMEOUT|g" "$CONFIG_FILE"
fi

if [ ! -z "$WEBP_PROCESSOR_MAX_RETRIES" ]; then
  sed -i "s|webp-processor.max-retries=.*|webp-processor.max-retries=$WEBP_PROCESSOR_MAX_RETRIES|g" "$CONFIG_FILE"
fi

# 自定义服务器端口
if [ ! -z "$SERVER_PORT" ]; then
  sed -i "s|server.port=.*|server.port=$SERVER_PORT|g" "$CONFIG_FILE"
fi

# 自定义上传文件配置
if [ ! -z "$MAX_FILE_SIZE" ]; then
  sed -i "s|spring.servlet.multipart.max-file-size=.*|spring.servlet.multipart.max-file-size=$MAX_FILE_SIZE|g" "$CONFIG_FILE"
fi

if [ ! -z "$MAX_REQUEST_SIZE" ]; then
  sed -i "s|spring.servlet.multipart.max-request-size=.*|spring.servlet.multipart.max-request-size=$MAX_REQUEST_SIZE|g" "$CONFIG_FILE"
fi

# 自定义日志级别
if [ ! -z "$LOG_LEVEL" ]; then
  sed -i "s|logging.level.com.doreamr233.charartconverter=.*|logging.level.com.doreamr233.charartconverter=$LOG_LEVEL|g" "$CONFIG_FILE"
fi

if [ ! -z "$LOG_FILE_PATH" ]; then
  sed -i "s|logging.file.name=.*|logging.file.name=$LOG_FILE_PATH/char-art-converter.log|g" "$CONFIG_FILE"
fi

if [ ! -z "$LOG_FILE_MAX_SIZE" ]; then
  sed -i "s|logging.logback.rollingpolicy.max-file-size=.*|logging.logback.rollingpolicy.max-file-size=$LOG_FILE_MAX_SIZE|g" "$CONFIG_FILE"
fi

if [ ! -z "$LOG_FILE_MAX_HISTORY" ]; then
  sed -i "s|logging.logback.rollingpolicy.max-history=.*|logging.logback.rollingpolicy.max-history=$LOG_FILE_MAX_HISTORY|g" "$CONFIG_FILE"
fi

if [ ! -z "$LOG_CHARSET_CONSOLE" ]; then
  sed -i "s|logging.charset.console=.*|logging.charset.console=$LOG_CHARSET_CONSOLE|g" "$CONFIG_FILE"
fi

if [ ! -z "$LOG_CHARSET_FILE" ]; then
  sed -i "s|logging.charset.file=.*|logging.charset.file=$LOG_CHARSET_FILE|g" "$CONFIG_FILE"
fi

# 自定义字符画默认配置
if [ ! -z "$DEFAULT_DENSITY" ]; then
  sed -i "s|char-art.default-density=.*|char-art.default-density=$DEFAULT_DENSITY|g" "$CONFIG_FILE"
fi

if [ ! -z "$DEFAULT_COLOR_MODE" ]; then
  sed -i "s|char-art.default-color-mode=.*|char-art.default-color-mode=$DEFAULT_COLOR_MODE|g" "$CONFIG_FILE"
fi

# 设置临时文件目录为数据卷目录
if [ ! -z "$DEFAULT_TEMP_PATH" ]; then
  sed -i "s|java.io.tmpdir=.*|java.io.tmpdir=$DEFAULT_TEMP_PATH|g" "$CONFIG_FILE"
fi

# 自定义时区配置
if [ ! -z "$TIMEZONE" ]; then
  sed -i "s|spring.jackson.time-zone=.*|spring.jackson.time-zone=$TIMEZONE|g" "$CONFIG_FILE"
fi

# 自定义日期时间格式
if [ ! -z "$DATETIME_FORMAT" ]; then
  sed -i "s|spring.mvc.format.date-time=.*|spring.mvc.format.date-time=$DATETIME_FORMAT|g" "$CONFIG_FILE"
fi


# 显示配置信息
echo "使用以下配置启动char-art-converter："
echo "-------------------"
echo "配置文件路径: $CONFIG_FILE"
echo "-------------------"
echo "配置文件内容:"
echo "-------------------"
# 读取并打印配置文件内容，过滤掉注释行和空行
grep -v "^#" "$CONFIG_FILE" | grep -v "^$"
echo "-------------------"

# 显示启动信息
echo "启动Java后端..."

# 启动应用 - 添加文件编码设置
exec java -Djava.io.tmpdir=$DEFAULT_TEMP_PATH -Dfile.encoding=$LANG -Dsun.jnu.encoding=$LANG -jar /app/app.jar --spring.config.location=file:$CONFIG_FILE