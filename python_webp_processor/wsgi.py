# wsgi.py
from app import create_app, cleanup_temp_files
from utils.scheduler import init_scheduler

# 清理过期的临时文件
cleanup_temp_files()

# 创建应用实例
application = create_app()

# 初始化调度器
init_scheduler()

# 为了兼容性，同时提供app变量
app = application