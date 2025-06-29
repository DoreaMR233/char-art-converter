import logging
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger
from utils.utils import cleanup_temp_files
from datetime import timezone

logger: logging.Logger = logging.getLogger(__name__)

class BackgroundScheduler:
    """后台调度器，用于定时清理临时文件。
    
    使用APScheduler的AsyncIOScheduler实现定时任务调度，
    主要用于定期清理过期的临时文件。
    """
    
    def __init__(self):
        """初始化调度器。
        
        创建AsyncIOScheduler实例并设置时区，初始化运行状态标志。
        """
        self.scheduler = AsyncIOScheduler(timezone=timezone.utc)
        self.is_running = False
    
    def start(self):
        """启动调度器。
        
        添加定时清理临时文件的任务并启动调度器。
        任务每小时执行一次，用于清理过期的临时文件。
        
        Raises:
            Exception: 当启动调度器失败时记录错误日志
        """
        if not self.is_running:
            try:
                # 添加定时任务：每小时清理一次临时文件
                self.scheduler.add_job(
                    cleanup_temp_files,
                    'interval',
                    hours=1,
                    id='cleanup_temp_files',
                    replace_existing=True
                )
                
                self.scheduler.start()
                self.is_running = True
                logger.debug("后台调度器已启动")
            except Exception as e:
                logger.error(f"启动调度器失败: {e}")
    
    def shutdown(self):
        """关闭调度器。
        
        停止所有定时任务并关闭调度器，等待所有任务完成。
        
        Raises:
            Exception: 当关闭调度器失败时记录错误日志
        """
        if self.is_running:
            try:
                self.scheduler.shutdown(wait=True)
                self.is_running = False
                logger.debug("后台调度器已关闭")
            except Exception as e:
                logger.error(f"关闭调度器失败: {e}")

# 创建调度器实例
scheduler = BackgroundScheduler()

def init_scheduler():
    """
    初始化调度器，添加定时任务
    
    Returns:
        BackgroundScheduler: 配置好的调度器实例
    """
    scheduler.start()
    return scheduler

def shutdown_scheduler():
    """
    关闭调度器
    """
    scheduler.shutdown()