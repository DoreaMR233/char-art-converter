"""配置模块

此模块负责加载和管理应用程序的配置参数，包括：
- 日志配置：级别、文件路径
- 时区设置
- 临时文件管理：目录、TTL
- 服务器配置：端口、调试模式
- Redis连接参数
- 进度更新间隔
- Java后端服务URL

所有配置参数优先从环境变量读取，如未设置则使用默认值。
"""

import logging
import os
import tempfile
import time

from dotenv import load_dotenv

# 加载.env文件中的环境变量
load_dotenv()

#: str: 日志输出级别，从环境变量 `LOG_LEVEL` 读取，默认为 'INFO'。
LOG_LEVEL: str = os.environ.get('LOG_LEVEL', 'INFO')

#: str: 日志文件的存储路径，从环境变量 `LOG_FILE` 读取，默认为 'app.log'。
LOG_FILE: str = os.environ.get('LOG_FILE', 'app.log')

# 确保日志目录存在
log_dir = os.path.dirname(LOG_FILE)
os.makedirs(log_dir, exist_ok=True)

#: str: 应用程序时区，从环境变量 `TIMEZONE` 读取，默认为 'Asia/Shanghai'。
TIMEZONE = os.environ.get('TIMEZONE', 'Asia/Shanghai')
os.environ['TZ'] = TIMEZONE

# 设置系统时区（仅在Unix/Linux系统上可用）
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
logger.debug(f"设置时区为: {TIMEZONE}")

# 临时文件目录配置
#: str: 临时文件存储目录路径。优先使用环境变量 `TEMP_DIR`，如未设置则自动创建。
_temp_dir_from_env = os.environ.get('TEMP_DIR')
if _temp_dir_from_env:
    TEMP_DIR = _temp_dir_from_env
    #: bool: 是否在程序退出时自动清理临时目录。用户指定目录时不自动清理。
    SHOULD_CLEANUP_TEMP_DIR = False
    os.makedirs(TEMP_DIR, exist_ok=True)
else:
    TEMP_DIR = tempfile.mkdtemp(prefix='char_art_converter_')
    #: bool: 自动创建的临时目录将在程序退出时被清理。
    SHOULD_CLEANUP_TEMP_DIR = True

logger.info(f"临时文件目录: {TEMP_DIR} (自动清理: {SHOULD_CLEANUP_TEMP_DIR})")
logger.info(f"日志文件路径: {LOG_FILE}")

#: int: 临时文件的保留时间（秒），从环境变量 `TEMP_FILE_TTL` 读取，默认3600秒（1小时）。
TEMP_FILE_TTL = int(os.environ.get('TEMP_FILE_TTL', '3600'))

#: int: 最大请求内容长度（字节），从环境变量 `MAX_CONTENT_LENGTH` 读取，默认10MB。
MAX_CONTENT_LENGTH = int(os.environ.get('MAX_CONTENT_LENGTH', '10485760'))

#: bool: 是否启用调试模式，从环境变量 `DEBUG` 读取，默认为False。
DEBUG = os.environ.get('DEBUG', 'False').lower() == 'true'

#: int: 应用监听端口，从环境变量 `PORT` 读取，默认为8081。
PORT = int(os.environ.get('PORT', '8081'))

# Redis连接配置
#: str: Redis服务器地址，从环境变量 `REDIS_HOST` 读取，默认为 'localhost'。
REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
#: int: Redis服务器端口，从环境变量 `REDIS_PORT` 读取，默认为6379。
REDIS_PORT = int(os.environ.get('REDIS_PORT', '6379'))
#: int: Redis数据库索引，从环境变量 `REDIS_DB` 读取，默认为0。
REDIS_DB = int(os.environ.get('REDIS_DB', '0'))
#: str: Redis服务器密码，从环境变量 `REDIS_PASSWORD` 读取，默认为空字符串。
REDIS_PASSWORD = os.environ.get('REDIS_PASSWORD', '')

#: float: 进度信息更新的时间间隔（秒），从环境变量 `PROGRESS_UPDATE_INTERVAL` 读取，默认为0.5秒。
PROGRESS_UPDATE_INTERVAL = float(os.environ.get('PROGRESS_UPDATE_INTERVAL', '0.5'))

#: str: Java后端服务的访问地址，从环境变量 `JAVA_BACKEND_URL` 读取，默认为 'http://localhost:8080'。
JAVA_BACKEND_URL = os.environ.get('JAVA_BACKEND_URL', 'http://localhost:8080')
logger.info(f"Java后端服务URL: {JAVA_BACKEND_URL}")