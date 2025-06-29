"""应用程序主入口

此模块负责创建、配置和启动FastAPI应用，主要功能包括：
- 配置日志记录器。
- 定义应用生命周期事件，如启动和关闭时的清理任务。
- 创建FastAPI应用实例，并挂载所有API路由。
- 配置CORS中间件，允许跨域请求。
- 使用uvicorn作为ASGI服务器启动应用。
"""

from contextlib import asynccontextmanager
import asyncio
import logging
import shutil

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.health import router as health_router
from api.progress import router as progress_router
from api.webp import router as webp_router
from config import (DEBUG, LOG_FILE, LOG_LEVEL, PORT, SHOULD_CLEANUP_TEMP_DIR,
                    TEMP_DIR)

logger: logging.Logger = logging.getLogger(__name__)

def cleanup_on_exit():
    """在应用退出时执行清理操作。

    如果配置中 `SHOULD_CLEANUP_TEMP_DIR` 为True，则会递归删除 `TEMP_DIR` 目录。
    """
    if SHOULD_CLEANUP_TEMP_DIR:
        try:
            shutil.rmtree(TEMP_DIR)
            logger.info(f"临时目录已清理: {TEMP_DIR}")
        except OSError as e:
            logger.error(f"清理临时目录失败: {e}")

@asynccontextmanager
async def lifespan(app: FastAPI):
    """FastAPI应用的生命周期管理器。

    在应用启动时记录日志，在应用关闭时执行清理操作。

    Args:
        app (FastAPI): FastAPI应用实例。
    """
    logger.info("应用开始启动...")
    yield
    logger.info("应用正在关闭...")
    cleanup_on_exit()

def create_app() -> FastAPI:
    """创建并配置FastAPI应用实例。

    - 初始化FastAPI应用并设置生命周期管理器。
    - 配置CORS中间件以允许所有来源的跨域请求。
    - 注册健康检查、进度和WebP处理相关的API路由。

    Returns:
        FastAPI: 配置完成的FastAPI应用实例。
    """
    app = FastAPI(lifespan=lifespan)

    # 添加CORS中间件，允许所有来源、方法和头部的跨域请求
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 包含各个模块的API路由
    app.include_router(health_router, prefix="/api", tags=["Health"])
    app.include_router(progress_router, prefix="/api", tags=["Progress"])
    app.include_router(webp_router, prefix="/api", tags=["WebP"])

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