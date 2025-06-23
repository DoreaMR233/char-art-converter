import sys
from datetime import datetime, timedelta
from typing import List, Dict

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()

# 应用启动时间
start_time = datetime.now()

# API端点列表
endpoints = [
    {"path": "/api/health", "method": "GET", "description": "健康检查"},
    {"path": "/api/progress/create", "method": "POST", "description": "创建进度跟踪任务"},
    {"path": "/api/progress/{task_id}", "method": "GET", "description": "获取任务进度SSE流"},
    {"path": "/api/process-webp", "method": "POST", "description": "处理WebP动图提取帧"},
    {"path": "/api/create-webp-animation", "method": "POST", "description": "创建WebP动画"},
    {"path": "/api/get-image/{file_path:path}", "method": "GET", "description": "获取图片文件"}
]

class HealthResponse(BaseModel):
    """健康检查响应模型。
    
    Attributes:
        status (str): 服务状态
        timestamp (str): 当前时间戳
        uptime (str): 服务运行时间
        version (str): 服务版本号
        endpoints (List[Dict[str, str]]): 可用的API端点列表
    """
    status: str
    timestamp: str
    uptime: str
    version: str
    endpoints: List[Dict[str, str]]

class EndpointInfo(BaseModel):
    path: str
    method: str
    description: str

class VersionResponse(BaseModel):
    """版本信息响应模型。
    
    Attributes:
        version (str): 应用版本号
        build_time (str): 构建时间
        python_version (str): Python版本
    """
    version: str
    build_time: str
    python_version: str

@router.get("/health", response_model=HealthResponse)
async def health_check():
    """健康检查接口。
    
    返回服务的健康状态、运行时间、版本信息和可用的API端点列表。
    
    Returns:
        HealthResponse: 包含服务健康状态信息的响应对象
    """
    current_time = datetime.now()
    uptime_seconds = (current_time - start_time).total_seconds()
    uptime_str = str(timedelta(seconds=int(uptime_seconds)))
    
    return HealthResponse(
        status="healthy",
        timestamp=current_time.isoformat(),
        uptime=uptime_str,
        version="1.0.0",
        endpoints=endpoints
    )

@router.get("/version", response_model=VersionResponse)
async def get_version():
    """获取版本信息接口。
    
    返回应用的版本号、构建时间和Python版本信息。
    
    Returns:
        VersionResponse: 包含版本信息的响应对象
    """
    return VersionResponse(
        version="1.0.0",
        build_time=datetime.now().isoformat(),
        python_version=sys.version
    )