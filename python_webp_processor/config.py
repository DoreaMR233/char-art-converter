import logging
import os
import tempfile
import time

from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

# 日志配置
LOG_LEVEL: str = os.environ.get('LOG_LEVEL', 'INFO')  # 日志级别，默认为INFO
LOG_FILE: str = os.environ.get('LOG_FILE', 'app.log')  # 日志文件路径，默认为app.log

# 确保日志目录存在
log_dir = os.path.dirname(LOG_FILE)
os.makedirs(log_dir, exist_ok=True)

# 时区设置
TIMEZONE = os.environ.get('TIMEZONE', 'Asia/Shanghai')  # 应用时区，默认为上海时区
os.environ['TZ'] = TIMEZONE
# time.tzset() 仅在Unix/Linux系统上可用，在Windows上跳过
if hasattr(time, 'tzset'):
    time.tzset()

# 配置日志
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S',
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler()
    ]
)
logger: logging.Logger = logging.getLogger(__name__)

# 记录时区设置信息
logger.info(f"设置时区为: {TIMEZONE}")

# 临时文件目录
# 首先尝试从环境变量获取临时目录
_temp_dir_from_env = os.environ.get('TEMP_DIR')
if _temp_dir_from_env:
    TEMP_DIR = _temp_dir_from_env
    SHOULD_CLEANUP_TEMP_DIR = False  # 不自动清理用户指定的目录
    os.makedirs(TEMP_DIR, exist_ok=True)
else:
    # 使用tempfile创建临时目录
    TEMP_DIR = tempfile.mkdtemp(prefix='char_art_converter_')
    SHOULD_CLEANUP_TEMP_DIR = True  # 程序结束时应清理自动创建的临时目录

logger.info(f"临时文件目录: {TEMP_DIR} (自动清理: {SHOULD_CLEANUP_TEMP_DIR})")
logger.info(f"日志文件路径: {LOG_FILE}")

# 临时文件保留时间（秒）
TEMP_FILE_TTL = int(os.environ.get('TEMP_FILE_TTL', '3600'))  # 临时文件保留时间，默认3600秒（1小时）

# 最大内容长度（字节）
MAX_CONTENT_LENGTH = int(os.environ.get('MAX_CONTENT_LENGTH', '10485760'))  # 最大请求内容长度，默认10MB

# 调试模式
DEBUG = os.environ.get('DEBUG', 'False').lower() == 'true'  # 是否启用调试模式，默认关闭

# 端口
PORT = int(os.environ.get('PORT', '8081'))  # 应用监听端口，默认8081

# Redis配置
REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')  # Redis服务器地址，默认localhost
REDIS_PORT = int(os.environ.get('REDIS_PORT', '6379'))  # Redis服务器端口，默认6379
REDIS_DB = int(os.environ.get('REDIS_DB', '0'))  # Redis数据库索引，默认0
REDIS_PASSWORD = os.environ.get('REDIS_PASSWORD', '')  # Redis密码，默认为空

# 进度更新间隔（秒）
PROGRESS_UPDATE_INTERVAL = float(os.environ.get('PROGRESS_UPDATE_INTERVAL', '0.5'))  # 进度更新间隔时间，默认0.5秒

# Java后端服务URL
JAVA_BACKEND_URL = os.environ.get('JAVA_BACKEND_URL', 'http://localhost:8080')  # Java后端服务地址，默认localhost:8080
logger.info(f"Java后端服务URL: {JAVA_BACKEND_URL}")