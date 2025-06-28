from pydantic import BaseModel
from typing import Optional, List

class AsyncTaskResponse(BaseModel):
    """异步任务响应模型。

    Attributes:
        task_id (str): 任务唯一标识符
        message (str): 响应消息
        status (str): 任务状态
    """
    task_id: str
    message: str
    status: str = "processing"

class CreateWebpResponse(BaseModel):
    """创建WebP响应模型。

    Attributes:
        webp (str): 生成的WebP动画文件路径
        task_id (str): 任务唯一标识符
    """
    webp: str
    task_id: str

class ErrorResponse(BaseModel):
    """错误响应模型。

    Attributes:
        error (str): 错误信息描述
        task_id (Optional[str]): 相关的任务标识符（如果有）
    """
    error: str
    task_id: Optional[str] = None

class ProcessWebpResponse(BaseModel):
    """处理WebP响应模型。

    Attributes:
        frameCount (int): 动画帧数
        delays (List[int]): 每帧延迟时间列表（毫秒）
        frames (List[str]): 提取的帧文件路径列表
        task_id (str): 任务唯一标识符
    """
    frameCount: int
    delays: List[int]
    frames: List[str]
    task_id: str

class ProgressCreateResponse(BaseModel):
    """创建进度跟踪响应模型。

    Attributes:
        task_id (str): 新创建的任务唯一标识符
        message (str): 创建结果消息
    """
    task_id: str
    message: str

class ProgressInfo(BaseModel):
    """进度信息模型。

    Attributes:
        progress (float): 进度百分比（0-100）
        message (str): 进度描述信息
        stage (str): 当前阶段
        current_frame (Optional[int]): 当前处理的帧索引
        total_frames (Optional[int]): 总帧数
        timestamp (float): 时间戳
        is_done (bool): 任务是否完成
    """
    progress: float
    message: str
    stage: str
    current_frame: Optional[int] = None
    total_frames: Optional[int] = None
    timestamp: float
    is_done: bool = False

class SuccessResponse(BaseModel):
    """成功响应模型。

    Attributes:
        status (str): 响应状态
        message (str): 响应消息
    """
    status: str = "success"
    message: str