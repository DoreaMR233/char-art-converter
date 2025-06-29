"""
@file: health.py
@description: 健康检查和版本信息API

提供服务的健康状态、版本信息和可用的API端点列表。
"""

import sys
from datetime import datetime, timedelta
from typing import List, Dict

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()

#: datetime: 应用启动时间，用于计算服务运行时间。
start_time = datetime.now()

#: List[Dict[str, str]]: 系统中所有可用的API端点列表。
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
        status (str): 服务状态，例如 'healthy'。
        timestamp (str): ISO 8601格式的当前时间戳。
        uptime (str): 服务的总运行时间。
        version (str): 服务的版本号。
        endpoints (List[Dict[str, str]]): 可用的API端点列表。
    """
    status: str
    timestamp: str
    uptime: str
    version: str
    endpoints: List[Dict[str, str]]

class EndpointInfo(BaseModel):
    """API端点信息模型。

    Attributes:
        path (str): 端点的路径。
        method (str): HTTP方法 (例如, 'GET', 'POST')。
        description (str): 端点的功能描述。
    """

class VersionResponse(BaseModel):
    """版本信息响应模型。

    Attributes:
        version (str): 应用的版本号。
        build_time (str): 应用的构建时间（或当前时间）。
        python_version (str): 运行应用的Python解释器版本。
    """
    version: str
    build_time: str
    python_version: str

@router.get("/health", response_model=HealthResponse, summary="服务健康检查")
async def health_check():
    """提供服务的健康状态。

    此端点返回服务的整体健康状况，包括运行时间、版本和可用的API端点。
    
    Returns:
        HealthResponse: 包含服务健康状态、运行时间、版本和端点列表的响应对象。
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

@router.get("/version", response_model=VersionResponse, summary="获取服务版本信息")
async def get_version():
    """提供服务的版本信息。

    返回应用的静态版本号、构建时间以及所使用的Python版本。
    
    Returns:
        VersionResponse: 包含版本、构建时间和Python版本信息的响应对象。
    """
    return VersionResponse(
        version="1.0.0",
        build_time=datetime.now().isoformat(),
        python_version=sys.version
    )