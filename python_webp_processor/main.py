from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging
import atexit
import shutil

from api.progress import set_app_ref
from config import  PORT, DEBUG, TEMP_DIR, SHOULD_CLEANUP_TEMP_DIR
from utils.utils import cleanup_temp_files
from utils.scheduler import init_scheduler, shutdown_scheduler

logger: logging.Logger = logging.getLogger(__name__)

# 注册退出时的清理函数
def cleanup_on_exit():
    """应用退出时的清理函数。
    
    执行以下清理操作：
    1. 关闭后台调度器
    2. 清理过期的临时文件
    3. 删除空的临时目录
    """
    logger.info("应用正在退出，开始清理...")
    
    # 关闭调度器
    shutdown_scheduler()
    
    # 清理临时文件
    cleanup_temp_files()
    
    # 如果需要，删除整个临时目录
    if SHOULD_CLEANUP_TEMP_DIR:
        try:
            shutil.rmtree(TEMP_DIR)
            logger.info(f"已删除临时目录: {TEMP_DIR}")
        except Exception as e:
            logger.error(f"删除临时目录时出错: {str(e)}")
    
    logger.info("清理完成")

# 注册退出处理函数
atexit.register(cleanup_on_exit)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时执行
    logger.info("FastAPI应用启动中...")
    
    # 清理过期的临时文件
    cleanup_temp_files()
    
    # 初始化并启动调度器
    init_scheduler()
    
    # 设置应用引用
    set_app_ref(app)
    
    logger.info(f"FastAPI WebP处理服务已启动，监听端口: {PORT}")
    
    yield
    
    # 关闭时执行
    logger.info("FastAPI应用关闭中...")
    
    # 关闭Redis连接
    from api.progress import close_redis
    await close_redis()
    
    cleanup_on_exit()

def create_app() -> FastAPI:
    """
    创建并配置FastAPI应用
    
    Returns:
        FastAPI: 配置好的FastAPI应用实例
    """
    app = FastAPI(
        title="WebP Processor API",
        description="WebP动图处理服务",
        version="1.0.0",
        lifespan=lifespan
    )
    
    # 配置CORS，允许所有来源的请求
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    
    # 注册路由
    from api import webp, health, progress
    app.include_router(webp.router, prefix="/api", tags=["webp"])
    app.include_router(health.router, prefix="/api", tags=["health"])
    app.include_router(progress.router, prefix="/api", tags=["progress"])
    
    return app

# 创建应用实例
app = create_app()

if __name__ == '__main__':
    import uvicorn
    
    # 启动应用
    logger.info(f"启动FastAPI WebP处理服务，监听端口: {PORT}, 调试模式: {DEBUG}")
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=PORT,
        reload=DEBUG,
        log_level="debug" if DEBUG else "info"
    )