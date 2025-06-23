from __future__ import annotations

import json
import time
import logging
import uuid
import asyncio
from typing import Dict, Any, List, Optional
from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from config import PROGRESS_UPDATE_INTERVAL
from utils.thread_safe_dict import ThreadSafeDict

logger: logging.Logger = logging.getLogger(__name__)

router = APIRouter()

# 存储进度信息的线程安全字典，键为任务ID，值为进度信息列表
progress_store = ThreadSafeDict[str, List[Dict[str, Any]]]()

# 存储SSE客户端的线程安全字典，键为任务ID，值为客户端字典
clients = ThreadSafeDict[str, Dict[str, float]]()

# 应用引用
app_ref = None

def set_app_ref(app):
    """设置应用引用"""
    global app_ref
    app_ref = app

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
        progress (int): 进度百分比（0-100）
        message (str): 进度描述信息
        stage (str): 当前阶段
        current_frame (Optional[int]): 当前处理的帧索引
        total_frames (Optional[int]): 总帧数
        timestamp (float): 时间戳
        is_done (bool): 任务是否完成
    """
    progress: int
    message: str
    stage: str
    current_frame: Optional[int] = None
    total_frames: Optional[int] = None
    timestamp: float
    is_done: bool = False

def format_sse_data(data: Dict[str, Any], event: Optional[str] = None) -> Dict[str, Any]:
    """格式化SSE数据。
    
    将字典数据格式化为符合SSE协议的字典格式。
    
    Args:
        data (Dict[str, Any]): 要格式化的数据字典
        event (Optional[str]): 事件类型
        
    Returns:
        Dict[str, Any]: 格式化后的SSE数据字典
    """
    result = {"data": json.dumps(data)}
    if event:
        result["event"] = event
    return result

def send_heartbeat() -> Dict[str, Any]:
    """发送心跳消息"""
    return format_sse_data({"timestamp": time.time()}, event="heartbeat")

def send_close_event() -> Dict[str, Any]:
    """发送关闭连接消息"""
    return format_sse_data({"timestamp": time.time(), "message": "连接关闭中"}, event="close")

@router.post('/progress/create', response_model=ProgressCreateResponse)
async def create_progress():
    """
    创建新的进度跟踪任务
    
    Returns:
        ProgressCreateResponse: 包含新任务ID的响应
    """
    task_id = str(uuid.uuid4())
    
    # 初始化进度存储
    progress_store[task_id] = []
    
    # 初始化客户端存储
    clients[task_id] = {}
    
    logger.info(f"创建新的进度跟踪任务: {task_id}")
    
    return ProgressCreateResponse(
        task_id=task_id,
        message="进度跟踪任务已创建"
    )

async def progress_event_generator(task_id: str, client_id: str):
    """进度事件生成器"""
    try:
        # 注册客户端
        if task_id not in clients:
            clients[task_id] = {}
        clients[task_id][client_id] = time.time()
        
        logger.info(f"客户端 {client_id} 连接到任务 {task_id} 的进度流")
        
        # 发送初始心跳
        yield send_heartbeat()
        
        # 发送已有的进度信息
        if task_id in progress_store:
            for progress_info in progress_store[task_id]:
                yield format_sse_data(progress_info, event="webp")
                await asyncio.sleep(0.1)  # 避免发送过快
                
                # 如果发现最后一条进度信息标记为完成，则发送关闭事件并退出
                if progress_info.get('is_done', False):
                    logger.info(f"任务 {task_id} 已标记为完成，发送关闭事件")
                    yield send_close_event()
                    return
        
        last_heartbeat = time.time()
        connection_active = True
        
        while connection_active:
            current_time = time.time()
            
            # 检查任务是否存在，如果不存在则退出循环
            if task_id not in progress_store or task_id not in clients:
                logger.info(f"任务 {task_id} 的数据已被清理，结束事件生成器")
                yield send_close_event()
                break
            
            # 检查是否有新的进度更新
            if task_id in progress_store:
                progress_list = progress_store[task_id]
                if progress_list:
                    latest_progress = progress_list[-1]

                    if current_time - latest_progress['timestamp'] < PROGRESS_UPDATE_INTERVAL:
                        yield format_sse_data(latest_progress, event="webp")
                        # 如果任务已完成，发送最后的进度并关闭连接
                        if latest_progress.get('is_done', False):
                            logger.info(f"任务 {task_id} 已完成，发送关闭事件")
                            yield send_close_event()
                            connection_active = False
                            break

                    else:
                        # 发送心跳（每10秒且进度没有更新）
                        if current_time - last_heartbeat > 10:
                            # 检查客户端是否仍然存在
                            if task_id in clients and client_id in clients[task_id]:
                                logger.debug(f"发送心跳到客户端 {client_id} 任务 {task_id}")
                                yield send_heartbeat()
                                last_heartbeat = current_time
                            else:
                                logger.info(f"客户端 {client_id} 已不存在，结束事件生成器")
                                connection_active = False
                                break

            # 更新客户端最后活动时间
            if task_id in clients and client_id in clients[task_id]:
                clients[task_id][client_id] = current_time
            else:
                logger.info(f"客户端 {client_id} 已不存在，结束事件生成器")
                connection_active = False
                break
            
            await asyncio.sleep(PROGRESS_UPDATE_INTERVAL)
            
    except Exception as e:
        logger.error(f"进度事件生成器出错: {str(e)}")
        yield send_close_event()
    finally:
        # 清理客户端连接
        if task_id in clients and client_id in clients[task_id]:
            del clients[task_id][client_id]
            logger.info(f"客户端 {client_id} 从任务 {task_id} 断开连接")

@router.get('/progress/{task_id}')
async def get_progress(task_id: str, request: Request):
    """获取任务进度的SSE流"""
    logger.info(f"客户端连接到进度流: {task_id}, 请求头: {dict(request.headers)}")
    
    # 生成客户端ID
    client_id = f"{request.client.host}-{request.headers.get('user-agent', 'unknown')}-{time.time()}"
    
    return EventSourceResponse(
        progress_event_generator(task_id, client_id),
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "Cache-Control"
        }
    )

@router.post('/progress/close/{task_id}')
async def close_progress_stream(task_id: str):
    """关闭进度流连接
    
    接收来自Java客户端的关闭请求，标记任务为完成状态并清理相关资源
    
    Args:
        task_id (str): 任务唯一标识符
        
    Returns:
        Dict: 包含操作结果的字典
    """
    logger.info(f"收到关闭进度流请求: {task_id}")
    
    # 调用关闭连接函数
    close_progress_connection(task_id)
    
    return {"status": "success", "message": f"任务 {task_id} 的进度连接已关闭"}

def update_progress(
    task_id: str,
    progress: int,
    message: str,
    stage: str,
    current_frame: Optional[int] = None,
    total_frames: Optional[int] = None,
    is_done: bool = False
) -> None:
    """更新任务进度。
    
    更新指定任务的进度信息并存储到进度存储中。
    
    Args:
        task_id (str): 任务唯一标识符
        progress (int): 进度百分比（0-100）
        message (str): 进度描述信息
        stage (str): 当前处理阶段
        current_frame (Optional[int]): 当前处理的帧索引
        total_frames (Optional[int]): 总帧数
        is_done (bool): 任务是否完成
    """
    try:
        progress_info = {
            "progress": progress,
            "message": message,
            "stage": stage,
            "timestamp": time.time(),
            "is_done": is_done
        }
        
        if current_frame is not None:
            progress_info["current_frame"] = current_frame
        if total_frames is not None:
            progress_info["total_frames"] = total_frames
        
        # 存储进度信息
        if task_id not in progress_store:
            progress_store[task_id] = []
        
        progress_store[task_id].append(progress_info)
        
        # 限制存储的进度信息数量，避免内存泄漏
        if len(progress_store[task_id]) > 100:
            progress_store[task_id] = progress_store[task_id][-50:]  # 保留最近50条
        
        logger.info(f"更新进度 {task_id} : {progress_info}")
        
        # 如果任务完成，延迟清理进度数据
        if is_done:
            def cleanup_progress():
                time.sleep(5)  # 等待5秒确保客户端收到最终进度
                if task_id in progress_store:
                    del progress_store[task_id]
                    logger.info(f"任务 {task_id} 的进度数据已清理")
            
            # 在后台线程中执行清理
            import threading
            cleanup_thread = threading.Thread(target=cleanup_progress)
            cleanup_thread.daemon = True
            cleanup_thread.start()
        
    except Exception as e:
        logger.error(f"更新进度时出错: {str(e)}")

def close_progress_connection(task_id: str) -> None:
    """关闭进度连接。
    
    标记指定任务的SSE连接为关闭状态，停止进度事件推送。
    
    Args:
        task_id (str): 任务唯一标识符
    """
    try:
        logger.info(f"正在关闭任务 {task_id} 的进度连接")
        
        # 标记任务为完成
        if task_id in progress_store:
            progress_list = progress_store[task_id]
            if progress_list:
                latest_progress = progress_list[-1].copy()
                latest_progress['is_done'] = True
                latest_progress['timestamp'] = time.time()  # 更新时间戳确保最新状态被发送
                progress_store[task_id].append(latest_progress)
                logger.info(f"任务 {task_id} 已标记为完成状态")
            else:
                # 如果没有进度信息，创建一个完成状态的进度信息
                progress_store[task_id] = [{
                    'progress': 100,
                    'message': '任务已完成',
                    'stage': '完成',
                    'timestamp': time.time(),
                    'is_done': True
                }]
                logger.info(f"任务 {task_id} 创建了完成状态的进度信息")
        
        logger.info(f"任务 {task_id} 的进度连接已标记为关闭")
        
        # 清理过期的进度数据（延迟清理，给客户端时间接收最后的消息）
        def cleanup_later():
            import threading
            
            def cleanup():
                # 等待3秒，给客户端时间接收最后的消息
                time.sleep(3)
                
                # 清理资源
                logger.info(f"开始清理任务 {task_id} 的资源")
                
                # 清理进度存储
                if task_id in progress_store:
                    del progress_store[task_id]
                    logger.info(f"已清理任务 {task_id} 的进度存储")
                
                # 清理客户端连接
                if task_id in clients:
                    client_count = len(clients[task_id])
                    del clients[task_id]
                    logger.info(f"已清理任务 {task_id} 的 {client_count} 个客户端连接")
                
                logger.info(f"任务 {task_id} 的所有资源已清理完毕")
            
            thread = threading.Thread(target=cleanup)
            thread.daemon = True
            thread.start()
            logger.info(f"已启动任务 {task_id} 的资源清理线程")
        
        cleanup_later()
        
    except Exception as e:
        logger.error(f"关闭进度连接时出错: {str(e)}")
        # 即使出错，也尝试清理资源
        try:
            if task_id in progress_store:
                del progress_store[task_id]
            if task_id in clients:
                del clients[task_id]
            logger.info(f"在错误处理中清理了任务 {task_id} 的资源")
        except Exception as cleanup_error:
            logger.error(f"清理任务 {task_id} 资源时出错: {str(cleanup_error)}")