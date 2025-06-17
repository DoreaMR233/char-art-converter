import logging
import os
import tempfile

from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

# 日志配置
LOG_LEVEL: str = os.environ.get('LOG_LEVEL', 'INFO')
LOG_FILE: str = os.environ.get('LOG_FILE', '/app/logs/webp-processor.log')

# 确保日志目录存在
log_dir = os.path.dirname(LOG_FILE)
os.makedirs(log_dir, exist_ok=True)

# 配置日志
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler()
    ]
)
logger: logging.Logger = logging.getLogger(__name__)

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
TEMP_FILE_TTL: int = int(os.environ.get('TEMP_FILE_TTL', 3600))

# 最大内容长度（字节）
MAX_CONTENT_LENGTH: int = int(os.environ.get('MAX_CONTENT_LENGTH', 10 * 1024 * 1024))

# 调试模式
DEBUG: bool = os.environ.get('DEBUG', 'False').lower() in ('true', '1', 't')

# 端口
PORT: int = int(os.environ.get('PORT', 5000))