import logging
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.interval import IntervalTrigger
from utils.utils import cleanup_temp_files

logger: logging.Logger = logging.getLogger(__name__)

# 创建调度器
scheduler = BackgroundScheduler()

def init_scheduler():
    """
    初始化调度器，添加定时任务
    
    Returns:
        BackgroundScheduler: 配置好的调度器实例
    """
    # 添加每小时清理临时文件的任务
    scheduler.add_job(
        func=cleanup_temp_files,
        trigger=IntervalTrigger(hours=1),
        id='cleanup_temp_files_job',
        name='清理临时文件',
        replace_existing=True
    )
    
    logger.info("已添加临时文件清理定时任务，每小时执行一次")
    
    # 启动调度器
    if not scheduler.running:
        scheduler.start()
        logger.info("调度器已启动")
    
    return scheduler

def shutdown_scheduler():
    """
    关闭调度器
    """
    if scheduler.running:
        scheduler.shutdown()
        logger.info("调度器已关闭")