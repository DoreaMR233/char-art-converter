from __future__ import annotations

import asyncio
import json
import logging
import os
import time
import uuid
from datetime import datetime
from typing import Dict, Any, Optional

import aioredis
from aioredis import Redis
from fastapi import APIRouter, Request
from sse_starlette.sse import EventSourceResponse

from config import PROGRESS_UPDATE_INTERVAL
from model.responseModel import ProgressCreateResponse, SuccessResponse

logger: logging.Logger = logging.getLogger(__name__)

router = APIRouter()

# Redis配置
REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
REDIS_DB = int(os.environ.get('REDIS_DB', 0))
REDIS_PASSWORD = os.environ.get('REDIS_PASSWORD', '')

# Redis连接池
redis_pool: Optional[Redis] = None

# 应用引用
app_ref = None

async def get_redis() -> Redis:
    """获取Redis连接，支持自动重连"""
    global redis_pool
    if redis_pool is None:
        # 构建Redis URL，支持密码认证
        if REDIS_PASSWORD:
            redis_url = f"redis://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}"
        else:
            redis_url = f"redis://{REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}"
        
        redis_pool = aioredis.from_url(
            redis_url,
            encoding="utf-8",
            decode_responses=True,
            retry_on_timeout=True,
            socket_keepalive=True,
            socket_keepalive_options={},
            health_check_interval=30
        )
    return redis_pool

async def reset_redis_connection():
    """重置Redis连接"""
    global redis_pool
    if redis_pool is not None:
        try:
            await redis_pool.close()
        except Exception as e:
            logger.warning(f"关闭Redis连接时出错: {str(e)}")
        redis_pool = None
    # 重新创建连接
    return await get_redis()

async def execute_redis_operation(operation, max_retries=3, retry_delay=1.0):
    """执行Redis操作，支持重试机制
    
    Args:
        operation: 要执行的Redis操作函数
        max_retries: 最大重试次数
        retry_delay: 重试延迟时间
    
    Returns:
        操作结果
    """
    for attempt in range(max_retries):
        try:
            redis = await get_redis()
            return await operation(redis)
        except (ConnectionError, ConnectionResetError, OSError, asyncio.CancelledError) as e:
            logger.warning(f"Redis连接错误 (尝试 {attempt + 1}/{max_retries}): {str(e)}")
            if attempt < max_retries - 1:
                await reset_redis_connection()
                await asyncio.sleep(retry_delay * (attempt + 1))
            else:
                logger.error(f"Redis连接失败，已达到最大重试次数: {str(e)}")
                raise
        except Exception as e:
            logger.error(f"Redis操作出错: {str(e)}")
            if attempt < max_retries - 1:
                await asyncio.sleep(retry_delay)
            else:
                raise
    return None


async def close_redis():
    """关闭Redis连接"""
    global redis_pool
    if redis_pool is not None:
        await redis_pool.close()
        redis_pool = None

def set_app_ref(app):
    """设置应用引用"""
    global app_ref
    app_ref = app





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

def send_close_event(reason: str = "completed", message: str = "连接关闭中") -> Dict[str, Any]:
    """发送关闭连接消息
    
    Args:
        reason: 关闭原因 (completed: 任务完成, error: 发生错误, timeout: 超时)
        message: 关闭消息
    """
    return format_sse_data({
        "timestamp": time.time(), 
        "message": message,
        "reason": reason
    }, event="close")

@router.post('/progress/create', response_model=ProgressCreateResponse)
async def create_progress():
    """
    创建新的进度跟踪任务
    
    Returns:
        ProgressCreateResponse: 包含新任务ID的响应
    """
    task_id = str(uuid.uuid4())
    max_retries = 3
    retry_delay = 1.0
    
    for attempt in range(max_retries):
        try:
            await get_redis()
            
            # 初始化进度存储，创建初始进度记录
            await execute_redis_operation(
                lambda redis: redis.delete(f"progress:{task_id}")
            )
            
            # 创建初始进度记录
            initial_progress = json.dumps({
                "status": "初始化",
                "timestamp": datetime.now().isoformat(),
                "message": "任务已创建",
                "progress": 30
            })
            await execute_redis_operation(
                lambda redis: redis.lpush(f"progress:{task_id}", initial_progress)
            )
            
            # 初始化客户端存储
            await execute_redis_operation(
                lambda redis: redis.delete(f"clients:{task_id}")
            )
            
            # 设置过期时间（24小时）
            await execute_redis_operation(
                lambda redis: redis.expire(f"progress:{task_id}", 86400)
            )
            await execute_redis_operation(
                lambda redis: redis.expire(f"clients:{task_id}", 86400)
            )
            
            logger.info(f"创建新的进度跟踪任务: {task_id}")
            
            return ProgressCreateResponse(
                task_id=task_id,
                message="进度跟踪任务已创建"
            )
            
        except (ConnectionError, ConnectionResetError, OSError) as e:
            logger.warning(f"Redis连接错误 (尝试 {attempt + 1}/{max_retries}): {str(e)}")
            if attempt < max_retries - 1:
                await reset_redis_connection()
                await asyncio.sleep(retry_delay * (attempt + 1))
            else:
                logger.error(f"Redis连接失败，已达到最大重试次数: {str(e)}")
                raise
        except Exception as e:
            logger.error(f"创建进度跟踪任务时出错: {str(e)}")
            if attempt < max_retries - 1:
                await asyncio.sleep(retry_delay)
            else:
                raise
    return None


async def progress_event_generator(task_id: str, client_id: str):
    """生成SSE事件的异步生成器"""
    logger.info(f"开始为任务 {task_id} 生成进度事件，客户端ID: {client_id}")
    
    # 跟踪已发送的消息索引
    last_sent_index = -1
    
    try:
        # 注册客户端
        await execute_redis_operation(
            lambda redis: redis.hset(f"clients:{task_id}", client_id, time.time())
        )
        
        # 发送初始心跳
        yield send_heartbeat()
        last_heartbeat = time.time()
        
        # 发送已有的进度信息
        progress_list = await execute_redis_operation(
            lambda redis: redis.lrange(f"progress:{task_id}", 0, -1)
        )
        if progress_list:
            for index, info_str in enumerate(progress_list):
                try:
                    info = json.loads(info_str)
                    # 检查是否是自定义事件
                    if 'event_type' in info:
                        # 这是一个自定义事件
                        yield format_sse_data(info['data'], event=info['event_type'])
                    else:
                        # 这是普通的进度信息
                        yield format_sse_data(info, event="webp")
                    
                    last_sent_index = index

                    # 如果发现最后一条进度信息标记为完成，发送任务完成关闭事件并退出
                    if info.get('is_done', False):
                        logger.info(f"任务 {task_id} 已完成，发送任务完成关闭事件")
                        yield send_close_event(reason="completed", message="任务已完成")
                        # 等待一小段时间确保客户端接收到关闭事件
                        await asyncio.sleep(0.5)
                        return
                    
                    # 避免发送过快
                    await asyncio.sleep(0.1)
                except json.JSONDecodeError:
                    logger.error(f"解析进度信息失败: {info_str}")
                    continue
        
        connection_active = True
        
        while connection_active:
            current_time = time.time()
            
            try:
                # 检查任务是否存在，如果不存在则退出循环
                progress_exists = await execute_redis_operation(
                    lambda redis: redis.exists(f"progress:{task_id}")
                )
                clients_exists = await execute_redis_operation(
                    lambda redis: redis.exists(f"clients:{task_id}")
                )
                if not progress_exists or not clients_exists:
                    logger.info(f"任务 {task_id} 的数据已被清理，结束事件生成器")
                    yield send_close_event(reason="completed", message="任务数据已清理")
                    # 等待一小段时间确保客户端接收到关闭事件
                    await asyncio.sleep(0.5)
                    break
                
                # 检查是否有新的进度更新
                progress_list = await execute_redis_operation(
                    lambda redis: redis.lrange(f"progress:{task_id}", 0, -1)
                )
                if progress_list:
                    # 发送所有未发送的新消息
                    for index in range(last_sent_index + 1, len(progress_list)):
                        try:
                            info_str = progress_list[index]
                            info = json.loads(info_str)
                            
                            # 检查是否是自定义事件
                            if 'event_type' in info:
                                # 这是一个自定义事件
                                yield format_sse_data(info['data'], event=info['event_type'])
                            else:
                                # 这是普通的进度信息
                                yield format_sse_data(info, event="webp")
                            
                            last_sent_index = index
                            
                            # 如果任务已完成，发送关闭事件并退出
                            if info.get('is_done', False):
                                logger.info(f"任务 {task_id} 已完成，发送关闭事件")
                                yield send_close_event()
                                # 等待一小段时间确保客户端接收到关闭事件
                                await asyncio.sleep(0.5)
                                connection_active = False
                                break
                        except json.JSONDecodeError:
                            info_str="未知错误"
                            logger.error(f"解析进度信息失败: {info_str}")
                            continue
                    
                    # 如果没有新消息且超过10秒，发送心跳
                    if last_sent_index + 1 >= len(progress_list) and current_time - last_heartbeat > 10:
                        # 检查客户端是否仍然存在
                        client_exists = await execute_redis_operation(
                            lambda redis: redis.hexists(f"clients:{task_id}", client_id)
                        )
                        if client_exists:
                            logger.debug(f"发送心跳到客户端 {client_id} 任务 {task_id}")
                            yield send_heartbeat()
                            last_heartbeat = current_time
                        else:
                            logger.info(f"客户端 {client_id} 已不存在，结束事件生成器")
                            break

                # 更新客户端最后活动时间
                client_exists = await execute_redis_operation(
                    lambda redis: redis.hexists(f"clients:{task_id}", client_id)
                )
                if client_exists:
                    await execute_redis_operation(
                        lambda redis: redis.hset(f"clients:{task_id}", client_id, current_time)
                    )
                else:
                    logger.info(f"客户端 {client_id} 已不存在，结束事件生成器")
                    break
            except Exception as redis_error:
                logger.error(f"Redis操作失败: {str(redis_error)}")
                # 如果Redis操作失败，等待一段时间后继续
                await asyncio.sleep(2.0)
            
            await asyncio.sleep(PROGRESS_UPDATE_INTERVAL)
            
    except Exception as e:
        logger.error(f"进度事件生成器出错: {str(e)}")
        yield send_close_event(reason="error", message=f"进度事件生成器出错: {str(e)}")
        # 等待一小段时间确保客户端接收到关闭事件
        await asyncio.sleep(0.5)
    finally:
        # 清理客户端连接
        try:
            client_exists = await execute_redis_operation(
                lambda redis: redis.hexists(f"clients:{task_id}", client_id)
            )
            if client_exists:
                await execute_redis_operation(
                    lambda redis: redis.hdel(f"clients:{task_id}", client_id)
                )
                logger.info(f"客户端 {client_id} 从任务 {task_id} 断开连接")
        except Exception as cleanup_error:
            logger.error(f"清理客户端连接时出错: {str(cleanup_error)}")

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
async def close_progress_stream(task_id: str, close_reason: str = "TASK_COMPLETED"):
    """关闭进度流连接
    
    接收来自Java客户端的关闭请求，标记任务为完成状态并清理相关资源
    
    Args:
        task_id (str): 任务唯一标识符
        close_reason (str): 关闭原因，可选值: TASK_COMPLETED, ERROR_OCCURRED, HEARTBEAT_TIMEOUT
        
    Returns:
        Dict: 包含操作结果的字典
    """
    # 根据关闭原因决定日志级别
    if close_reason in ["ERROR_OCCURRED", "HEARTBEAT_TIMEOUT"]:
        logger.warning(f"收到关闭进度流请求: {task_id}, 原因: {close_reason}")
    else:
        logger.info(f"收到关闭进度流请求: {task_id}, 原因: {close_reason}")
    
    # 调用关闭连接函数，传递关闭原因
    await close_progress_connection(task_id, close_reason)
    
    return SuccessResponse(message=f"任务 {task_id} 的进度连接已关闭，原因: {close_reason}")

async def update_progress(
    task_id: str,
    progress: float,
    message: str,
    stage: str,
    current_frame: Optional[int] = None,
    total_frames: Optional[int] = None,
    is_done: bool = False
) -> None:
    """更新任务进度。
    
    更新指定任务的进度信息并存储到Redis中。
    
    Args:
        task_id (str): 任务唯一标识符
        progress (float): 进度百分比（0-100）
        message (str): 进度描述信息
        stage (str): 当前处理阶段
        current_frame (Optional[int]): 当前处理的帧索引
        total_frames (Optional[int]): 总帧数
        is_done (bool): 任务是否完成
    """
    max_retries = 3
    retry_delay = 1.0
    
    for attempt in range(max_retries):
        try:
            await get_redis()
            
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
            
            # 存储进度信息到Redis
            progress_json = json.dumps(progress_info)
            await execute_redis_operation(
                lambda redis: redis.rpush(f"progress:{task_id}", progress_json)
            )
            
            # 限制存储的进度信息数量，避免内存泄漏
            progress_count = await execute_redis_operation(
                lambda redis: redis.llen(f"progress:{task_id}")
            )
            if progress_count > 100:
                # 保留最近50条
                await execute_redis_operation(
                    lambda redis: redis.ltrim(f"progress:{task_id}", -50, -1)
                )
            
            # 设置过期时间（24小时）
            await execute_redis_operation(
                lambda redis: redis.expire(f"progress:{task_id}", 86400)
            )
            
            logger.info(f"更新进度 {task_id} : {progress_info}")
            
            # 如果任务完成，延迟清理进度数据
            if is_done:
                async def cleanup_progress():
                    await asyncio.sleep(5)  # 等待5秒确保客户端收到最终进度
                    try:
                        await execute_redis_operation(
                            lambda redis: redis.delete(f"progress:{task_id}")
                        )
                        await execute_redis_operation(
                            lambda redis: redis.delete(f"clients:{task_id}")
                        )
                        logger.info(f"任务 {task_id} 的进度数据已清理")
                    except Exception as cleanup_e:
                        logger.error(f"清理进度数据时出错: {str(cleanup_e)}")
                
                # 在后台任务中执行清理
                asyncio.create_task(cleanup_progress())
            
            # 成功执行，跳出重试循环
            break
            
        except (ConnectionError, ConnectionResetError, OSError) as e:
            logger.warning(f"Redis连接错误 (尝试 {attempt + 1}/{max_retries}): {str(e)}")
            if attempt < max_retries - 1:
                # 重置连接并重试
                await reset_redis_connection()
                await asyncio.sleep(retry_delay * (attempt + 1))
            else:
                logger.error(f"Redis连接失败，已达到最大重试次数: {str(e)}")
                raise
        except Exception as e:
            logger.error(f"更新进度时出错: {str(e)}")
            if attempt < max_retries - 1:
                await asyncio.sleep(retry_delay)
            else:
                raise

async def send_event(task_id: str, event_type: str, data: Dict[str, Any]) -> None:
    """发送自定义事件到SSE流。
    
    向指定任务的SSE流发送自定义事件数据。
    
    Args:
        task_id (str): 任务唯一标识符
        event_type (str): 事件类型（如 'webp_result', 'webp_error'）
        data (Dict[str, Any]): 要发送的事件数据
    """
    max_retries = 3
    retry_delay = 1.0
    
    for attempt in range(max_retries):
        try:
            await get_redis()
            
            # 构建事件信息
            event_info = {
                "event_type": event_type,
                "data": data,
                "timestamp": time.time()
            }
            
            # 存储事件信息到Redis中，这样SSE流会自动发送
            event_json = json.dumps(event_info)
            await execute_redis_operation(
                lambda redis: redis.rpush(f"progress:{task_id}", event_json)
            )
            
            # 限制存储的事件信息数量，避免内存泄漏
            progress_count = await execute_redis_operation(
                lambda redis: redis.llen(f"progress:{task_id}")
            )
            if progress_count > 100:
                # 保留最近50条
                await execute_redis_operation(
                    lambda redis: redis.ltrim(f"progress:{task_id}", -50, -1)
                )
            
            # 设置过期时间（24小时）
            await execute_redis_operation(
                lambda redis: redis.expire(f"progress:{task_id}", 86400)
            )
            
            # 成功执行，跳出重试循环
            break
            
        except (ConnectionError, ConnectionResetError, OSError) as e:
            logger.warning(f"Redis连接错误 (尝试 {attempt + 1}/{max_retries}): {str(e)}")
            if attempt < max_retries - 1:
                # 重置连接并重试
                await reset_redis_connection()
                await asyncio.sleep(retry_delay * (attempt + 1))
            else:
                logger.error(f"Redis连接失败，已达到最大重试次数: {str(e)}")
                raise
        except Exception as e:
            logger.error(f"发送事件时出错: {str(e)}")
            if attempt < max_retries - 1:
                await asyncio.sleep(retry_delay)
            else:
                raise
        
        logger.info(f"发送事件 {event_type} 到任务 {task_id}: {data}")


async def close_progress_connection(task_id: str, close_reason: str = "TASK_COMPLETED") -> None:
    """关闭进度连接。
    
    标记指定任务的SSE连接为关闭状态，停止进度事件推送。
    
    Args:
        task_id (str): 任务唯一标识符
        close_reason (str): 关闭原因，可选值: TASK_COMPLETED, ERROR_OCCURRED, HEARTBEAT_TIMEOUT
    """
    try:
        await get_redis()
        # 根据关闭原因决定日志级别
        if close_reason in ["ERROR_OCCURRED", "HEARTBEAT_TIMEOUT"]:
            logger.warning(f"正在关闭任务 {task_id} 的进度连接，原因: {close_reason}")
        else:
            logger.info(f"正在关闭任务 {task_id} 的进度连接，原因: {close_reason}")
        
        # 检查任务是否存在
        progress_exists = await execute_redis_operation(
            lambda redis: redis.exists(f"progress:{task_id}")
        )
        if progress_exists:
            # 获取最新的进度信息
            latest_progress_json = await execute_redis_operation(
                lambda redis: redis.lindex(f"progress:{task_id}", -1)
            )
            if latest_progress_json:
                latest_progress = json.loads(latest_progress_json)
                latest_progress['is_done'] = True
                latest_progress['timestamp'] = time.time()  # 更新时间戳确保最新状态被发送
                
                # 添加完成状态的进度信息
                progress_json = json.dumps(latest_progress)
                await execute_redis_operation(
                    lambda redis: redis.rpush(f"progress:{task_id}", progress_json)
                )
                logger.info(f"任务 {task_id} 已标记为完成状态")
            else:
                # 如果没有进度信息，创建一个完成状态的进度信息
                completion_progress = {
                    'progress': 100.0,
                    'message': '任务已完成',
                    'stage': '完成',
                    'timestamp': time.time(),
                    'is_done': True
                }
                progress_json = json.dumps(completion_progress)
                await execute_redis_operation(
                    lambda redis: redis.rpush(f"progress:{task_id}", progress_json)
                )
                logger.info(f"任务 {task_id} 创建了完成状态的进度信息")
        
        logger.info(f"任务 {task_id} 的进度连接已标记为关闭")
        
        # 清理过期的进度数据（延迟清理，给客户端时间接收最后的消息）
        async def cleanup_later():
            
            # 清理资源
            logger.info(f"开始清理任务 {task_id} 的资源")
            
            # 清理进度存储
            await execute_redis_operation(
                lambda redis: redis.delete(f"progress:{task_id}")
            )
            logger.info(f"已清理任务 {task_id} 的进度存储")
            
            # 清理客户端连接
            client_count = await execute_redis_operation(
                lambda redis: redis.hlen(f"clients:{task_id}")
            )
            await execute_redis_operation(
                lambda redis: redis.delete(f"clients:{task_id}")
            )
            logger.info(f"已清理任务 {task_id} 的 {client_count} 个客户端连接")
            
            logger.info(f"任务 {task_id} 的所有资源已清理完毕")
        
        # 在后台任务中执行清理
        asyncio.create_task(cleanup_later())
        logger.info(f"已启动任务 {task_id} 的资源清理任务")
        
    except Exception as e:
        # 根据关闭原因决定错误日志级别
        if close_reason == "ERROR_OCCURRED":
            logger.error(f"关闭进度连接时出错: {str(e)}，原因: {close_reason}")
        else:
            logger.debug(f"关闭进度连接时出现异常: {str(e)}，原因: {close_reason}")
        # 即使出错，也尝试清理资源
        try:
            await execute_redis_operation(
                lambda redis: redis.delete(f"progress:{task_id}")
            )
            await execute_redis_operation(
                lambda redis: redis.delete(f"clients:{task_id}")
            )
            logger.info(f"在错误处理中清理了任务 {task_id} 的资源")
        except Exception as cleanup_error:
            logger.error(f"清理任务 {task_id} 资源时出错: {str(cleanup_error)}")