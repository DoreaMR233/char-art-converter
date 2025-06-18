# gunicorn.conf.py
import os
import multiprocessing

# 绑定的IP和端口
bind = f"0.0.0.0:{os.environ.get('PORT', '5000')}"

# 工作进程数
workers = int(os.environ.get('GUNICORN_WORKERS', multiprocessing.cpu_count() * 2 + 1))

# 工作模式
worker_class = 'sync'

# 超时时间
timeout = int(os.environ.get('GUNICORN_TIMEOUT', '120'))

# 最大请求数
max_requests = int(os.environ.get('GUNICORN_MAX_REQUESTS', '1000'))
max_requests_jitter = int(os.environ.get('GUNICORN_MAX_REQUESTS_JITTER', '50'))

# 日志配置
accesslog = "/app/logs/access.log"
errorlog = "/app/logs/error.log"
loglevel = os.environ.get('LOG_LEVEL', 'info').lower()

# 进程名称
proc_name = "webp_processor"

# 守护进程模式（在Docker中应设为False）
daemon = False

# 预加载应用
preload_app = True