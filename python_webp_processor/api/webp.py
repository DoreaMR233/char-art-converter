"""
@file: webp.py
@description: WebP图像处理API

提供WebP动图的处理、帧提取、字符画转换以及结果获取等功能。
"""

import asyncio
import json
import logging
import os
import threading
import traceback
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from typing import Optional, List
from urllib.parse import quote

import requests
from PIL import Image, ImageSequence
from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Path, BackgroundTasks
from fastapi.responses import FileResponse

from api.progress import update_progress, create_progress, send_event
from config import TEMP_DIR, MAX_CONTENT_LENGTH, PROGRESS_UPDATE_INTERVAL, JAVA_BACKEND_URL
from model.responseModel import AsyncTaskResponse, ProcessWebpResponse, CreateWebpResponse, ErrorResponse
from utils.utils import cleanup_temp_files

#: logging.Logger: 日志记录器实例。
logger: logging.Logger = logging.getLogger(__name__)

router = APIRouter()


@router.get('/get-image/{file_path:path}', summary="获取处理后的图片文件")
async def get_image(file_path: str = Path(..., description="图片文件的路径（相对于TEMP_DIR的路径）")):
    """根据文件路径返回临时存储的图片文件。

    此端点用于获取由其他处理过程（如WebP帧提取）生成的临时图片文件。
    文件在传输后会被自动清理。

    Args:
        file_path (str): 要获取的图片文件的相对路径。

    Returns:
        FileResponse: 图片文件响应。

    Raises:
        HTTPException: 如果文件不存在、路径非法或不是支持的图片格式，则返回404, 403或400错误。
    """
    try:
        # 构建完整的文件路径
        full_path = os.path.join(TEMP_DIR, file_path)
        
        # 检查文件是否存在
        if not os.path.exists(full_path):
            logger.warning(f"请求的文件不存在: {full_path}")
            raise HTTPException(status_code=404, detail="请求的文件不存在")
        
        # 检查文件是否在允许的目录内（安全检查）
        if not os.path.abspath(full_path).startswith(os.path.abspath(TEMP_DIR)):
            logger.warning(f"非法文件路径: {full_path}")
            raise HTTPException(status_code=403, detail="非法文件路径")
        
        # 检查文件是否是图片
        if not full_path.lower().endswith(('.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp')):
            logger.warning(f"请求的文件不是支持的图片格式: {full_path}")
            raise HTTPException(status_code=400, detail="请求的文件不是支持的图片格式")
        
        # 根据文件扩展名确定媒体类型
        file_ext = os.path.splitext(file_path)[1].lower()
        if file_ext == '.webp':
            media_type = 'image/webp'
        elif file_ext in ['.png']:
            media_type = 'image/png'
        elif file_ext in ['.jpg', '.jpeg']:
            media_type = 'image/jpeg'
        elif file_ext == '.gif':
            media_type = 'image/gif'
        else:
            media_type = 'application/octet-stream'
        
        # 创建文件响应
        response = FileResponse(
            path=full_path,
            media_type=media_type,
            filename=os.path.basename(full_path)
        )
        
        # 在后台任务中删除文件
        async def cleanup_file():
            try:
                if os.path.exists(full_path):
                    # 获取文件所在的目录路径
                    dir_path = os.path.dirname(full_path)
                    # 删除文件
                    os.remove(full_path)
                    logger.debug(f"文件已成功传输并删除: {full_path}")
                    
                    # 检查目录是否为空，如果为空则删除
                    if os.path.exists(dir_path) and len(os.listdir(dir_path)) == 0:
                        os.rmdir(dir_path)
                        logger.debug(f"空文件夹已删除: {dir_path}")
            except Exception as ex:
                logger.error(f"删除文件或文件夹时出错: {str(ex)}")
        
        # 注册后台任务
        response.background = cleanup_file
        
        logger.debug(f"返回文件: {full_path}, 媒体类型: {media_type}")
        return response
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"获取图片时出错: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取图片时出错: {str(e)}")

@router.post('/process-webp', response_model=AsyncTaskResponse, summary="异步处理WebP动图")
async def process_webp(
    background_tasks: BackgroundTasks,
    image: UploadFile = File(..., description="WebP动图文件"),
    task_id: Optional[str] = Form(None, description="任务ID（可选）")
):
    """接收一个WebP动图文件，并在后台异步进行处理。

    此端点会立即返回一个任务ID，客户端可以通过SSE订阅该ID以获取实时进度和最终结果。

    Args:
        background_tasks (BackgroundTasks): FastAPI后台任务调度器。
        image (UploadFile): 用户上传的WebP格式动图文件。
        task_id (Optional[str]): 客户端提供的可选任务ID，用于跟踪。

    Returns:
        AsyncTaskResponse: 包含任务ID和初始状态的响应对象。

    Raises:
        HTTPException: 如果文件无效或过大，则返回400或413错误。
    """
    try:
        # 检查文件名
        if not image.filename:
            logger.warning("上传的文件没有文件名")
            raise HTTPException(status_code=400, detail="未选择文件")
        
        # 检查文件类型
        if not image.filename.lower().endswith('.webp'):
            logger.warning(f"上传的文件不是WebP格式: {image.filename}")
            raise HTTPException(status_code=400, detail="只支持WebP格式")
        
        # 读取文件内容以检查大小
        file_content = await image.read()
        file_size = len(file_content)
        
        if file_size > MAX_CONTENT_LENGTH:
            logger.warning(f"文件过大: {file_size} 字节, 超过最大限制 {MAX_CONTENT_LENGTH} 字节")
            raise HTTPException(
                status_code=413, 
                detail=f"文件过大，最大允许 {MAX_CONTENT_LENGTH/1024/1024:.1f}MB"
            )
        
        # 创建任务ID用于进度跟踪
        if not task_id:
            progress_response = await create_progress()
            task_id = progress_response.task_id
            logger.debug(f"为WebP处理创建新的任务ID: {task_id}")
        else:
            logger.debug(f"使用请求提供的任务ID: {task_id}")
        
        # 在后台执行处理任务
        background_tasks.add_task(_process_webp_sync, task_id, file_content, image.filename)
        
        return AsyncTaskResponse(
            task_id=task_id,
            message="WebP处理任务已启动，请通过SSE监听处理结果",
            status="processing"
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"启动WebP处理任务时出错: {str(e)}")
        raise HTTPException(status_code=500, detail=f"启动任务失败: {str(e)}")

async def _process_webp_sync(
    task_id: str,
    file_content: bytes,
    filename: str
):
    """在后台同步处理WebP文件，包括提取帧和调用字符画转换。

    这是一个后台任务，负责处理WebP文件的核心逻辑。它会更新任务进度，
    提取所有帧，然后将每一帧发送到Java后端进行字符画转换。

    Args:
        task_id (str): 用于跟踪进度的任务ID。
        file_content (bytes): WebP文件的二进制内容。
        filename (str): 原始文件名。
    """
    temp_path = None
    frames_dir = None
    try:
        file_size = len(file_content)
        
        # 更新进度：开始处理
        logger.debug(f"任务 {task_id}: 开始处理WebP文件，大小: {file_size} 字节")
        await update_progress(task_id, 31.0, "WebP文件已接收，开始处理", "初始化")
        logger.debug(f"任务 {task_id}: 进度更新 31%")
        
        # 保存上传的文件到临时目录
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        temp_path = os.path.join(TEMP_DIR, f"{timestamp}_{filename}")
        
        with open(temp_path, 'wb') as f:
            f.write(file_content)
        
        logger.debug(f"任务 {task_id}: WebP文件已保存到临时路径: {temp_path}")
        await update_progress(task_id, 32.0, "WebP文件已保存，准备解析", "文件保存")
        logger.debug(f"任务 {task_id}: 进度更新 32%")
        
        # 使用Pillow打开WebP文件
        with Image.open(temp_path) as img:
            # 检查是否为动图
            is_animated = getattr(img, "is_animated", False)
            logger.debug(f"任务 {task_id}: WebP是否为动图: {is_animated}")
            if not is_animated:
                logger.warning(f"任务 {task_id}: 上传的WebP不是动图")
                await update_progress(task_id, 100.0, "处理失败：上传的WebP不是动图", "错误", is_done=True)
                logger.debug(f"任务 {task_id}: 关闭进度连接")
                
                raise HTTPException(status_code=400, detail="上传的WebP不是动图")
            
            # 获取帧数
            frame_count = getattr(img, "n_frames", 0)
            if frame_count <= 0:
                await update_progress(task_id, 100.0, "处理失败：无法获取WebP帧数", "错误", is_done=True)
                
                raise HTTPException(status_code=400, detail="无法获取WebP帧数")
            
            logger.debug(f"任务 {task_id}: WebP动图帧数: {frame_count}")
            await update_progress(task_id, 33.0, f"WebP动图帧数: {frame_count}，开始提取帧", "帧提取")
            logger.debug(f"任务 {task_id}: 进度更新 33%")
            
            # 提取每一帧和延迟信息
            delays = []
            frame_paths = []
            
            # 创建临时目录来存储帧
            timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
            frames_dir = os.path.join(TEMP_DIR, f"webp_frames_{timestamp}")
            os.makedirs(frames_dir, exist_ok=True)
            logger.debug(f"任务 {task_id}: 创建临时目录存储帧: {frames_dir}")
            
            for i, frame in enumerate(ImageSequence.Iterator(img)):
                # 计算当前进度
                current_progress = 33.0 + ((i+1) / float(frame_count)) * (37.0 - 33.0)
                await update_progress(
                    task_id, 
                    current_progress, 
                    f"正在处理第 {i+1}/{frame_count} 帧",
                    "帧提取",
                    i+1,  # 当前帧索引
                    frame_count  # 总帧数
                )
                logger.debug(f"任务 {task_id}: 进度更新 {current_progress}%, 处理第 {i+1}/{frame_count} 帧")
                
                # 复制帧以避免引用问题
                frame_copy = frame.copy()
                
                # 将帧转换为RGB模式（如果需要）
                if frame_copy.mode != 'RGB':
                    frame_copy = frame_copy.convert('RGB')
                
                # 保存帧到临时文件
                frame_filename = f"frame_{i:04d}.png"
                frame_path = os.path.join(frames_dir, frame_filename)
                frame_copy.save(frame_path, format="PNG")
                # 添加相对路径而非绝对路径
                relative_path = os.path.join(os.path.basename(frames_dir), frame_filename)
                frame_paths.append(relative_path)
                
                # 获取帧延迟（毫秒）
                # WebP帧延迟存储在info字典中，如果没有则使用默认值100ms
                delay = frame.info.get('duration', 100)
                delays.append(delay)
                
                logger.debug(f"任务 {task_id}: 处理第 {i+1}/{frame_count} 帧, 延迟: {delay}ms, 保存到: {frame_path}")
                
                # 添加短暂延迟，避免进度更新过于频繁，减少CPU占用
                await asyncio.sleep(PROGRESS_UPDATE_INTERVAL)
            
            # 更新进度：完成处理
            await update_progress(task_id, 37.0, "帧提取完成，准备返回结果", "完成", frame_count, frame_count)
            logger.debug(f"任务 {task_id}: 所有帧提取完成，进度更新 37%")
            
            # 构建结果数据，使用 ProcessWebpResponse 模型
            result_response = ProcessWebpResponse(
                frameCount=frame_count,
                delays=delays,
                frames=frame_paths,
                task_id=task_id
            )

            # 通过SSE发送处理结果
            await send_event(task_id, "webp_result", result_response.model_dump())
            logger.debug(f"任务 {task_id}: WebP处理结果已通过SSE发送")

            # 最终进度更新
            await update_progress(task_id, 38.0, "处理完成，等待客户端获取图片", "完成", frame_count, frame_count, is_done=True)
            logger.debug(f"任务 {task_id}: 最终进度更新 38%，等待客户端获取图片")

            # 注意：不在这里关闭SSE连接，等待Java端处理完webp_result事件后再关闭
            logger.debug(f"任务 {task_id}: WebP处理结果已发送，等待Java端处理完成")
            

            
    except Exception as e:
        error_details = traceback.format_exc()
        logger.error(f"处理WebP时出错: {str(e)}\n{error_details}")
        
        # 收集需要清理的临时文件路径
        temp_paths_to_clean = []
        if temp_path and os.path.exists(temp_path):
            temp_paths_to_clean.append(temp_path)
        if frames_dir and os.path.exists(frames_dir):
            temp_paths_to_clean.append(frames_dir)
        
        # 构建错误结果数据，使用 ErrorResponse 模型
        error_response = ErrorResponse(
            error=str(e),
            task_id=task_id
        )
        
        # 通过SSE发送错误信息，并传递临时文件路径
        await send_event(task_id, "webp_error", error_response.model_dump(), temp_paths_to_clean)
        logger.debug(f"任务 {task_id}: WebP处理错误已通过SSE发送，临时文件路径: {temp_paths_to_clean}")
        
        # 更新进度，但不关闭SSE连接
        await update_progress(task_id, 100.0, f"处理失败：{str(e)}", "错误", is_done=False)
        # 注意：不在这里关闭SSE连接，等待Java端处理完webp_error事件后再关闭
        logger.debug(f"WebP处理错误任务 {task_id} 已发送，等待Java端处理完成")
    finally:
        # 先清理临时文件
        cleanup_temp_files()

@router.post('/create-webp-animation', response_model=AsyncTaskResponse, summary="从帧异步创建WebP动画")
async def create_webp_animation(
    background_tasks: BackgroundTasks,
    frame_paths: str = Form(..., description="帧文件路径列表（JSON格式）"),
    delays: str = Form(..., description="延迟时间列表（JSON格式，毫秒）"),
    frame_format: str = Form(..., description="帧格式列表（JSON格式）"),
    task_id: Optional[str] = Form(None, description="任务ID（可选）")
):
    """从一系列帧图像异步创建WebP动画。

    接收帧路径、延迟和格式信息，并在后台任务中生成WebP动画。
    通过SSE报告进度和结果。

    Args:
        background_tasks (BackgroundTasks): FastAPI后台任务调度器。
        frame_paths (str): 包含各帧文件路径的JSON字符串数组。
        delays (str): 包含各帧延迟时间的JSON字符串数组（毫秒）。
        frame_format (str): 包含各帧格式的JSON字符串数组。
        task_id (Optional[str]): 客户端提供的可选任务ID。

    Returns:
        AsyncTaskResponse: 包含任务ID和初始状态的响应对象。

    Raises:
        HTTPException: 如果输入的JSON无效，则返回400错误。
    """
    try:
        # 解析帧文件路径数组
        try:
            frame_paths_list = json.loads(frame_paths)
            if not frame_paths_list:
                logger.warning("帧文件路径数组为空")
                raise HTTPException(status_code=400, detail="帧文件路径数组为空")
        except json.JSONDecodeError as e:
            logger.warning(f"解析帧文件路径数组失败: {str(e)}")
            raise HTTPException(status_code=400, detail=f"解析帧文件路径数组失败: {str(e)}")
        
        # 解析延迟信息
        try:
            delays_list = json.loads(delays)
        except json.JSONDecodeError as e:
            logger.warning(f"解析延迟信息失败: {str(e)}")
            raise HTTPException(status_code=400, detail=f"解析延迟信息失败: {str(e)}")
        
        # 解析帧格式信息
        try:
            frame_format_list = json.loads(frame_format)
        except json.JSONDecodeError as e:
            logger.warning(f"解析帧格式信息失败: {str(e)}")
            raise HTTPException(status_code=400, detail=f"解析帧格式信息失败: {str(e)}")
        
        # 获取或创建任务ID
        if not task_id:
            progress_response = await create_progress()
            task_id = progress_response.task_id
            logger.debug(f"为WebP动画创建新的任务ID: {task_id}")
        else:
            logger.debug(f"使用请求提供的任务ID: {task_id}")
        
        # 检查帧和延迟的数量是否匹配
        if len(frame_paths_list) != len(delays_list) or len(frame_paths_list) != len(frame_format_list):
            logger.warning(f"帧数量({len(frame_paths_list)})、延迟数量({len(delays_list)})、帧格式数量({len(frame_format_list)})不匹配")
            raise HTTPException(status_code=400, detail="帧数量、延迟数量、帧格式数量不匹配")
        
        # 检查是否有帧
        if len(frame_paths_list) == 0:
            logger.warning("没有提供帧")
            raise HTTPException(status_code=400, detail="没有提供帧")
        
        # 在后台执行处理任务
        background_tasks.add_task(_create_webp_animation_sync, task_id, frame_paths_list, delays_list, frame_format_list)
        
        return AsyncTaskResponse(
            task_id=task_id,
            message="WebP动画创建任务已启动，请通过SSE监听处理结果",
            status="processing"
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"启动WebP动画创建任务时出错: {str(e)}")
        raise HTTPException(status_code=500, detail=f"启动任务失败: {str(e)}")

async def _save_webp_with_progress(frames_pil: List[Image.Image], output_path: str, delays_list: List[int], task_id: str):
    """在后台线程中保存WebP动画，并异步报告进度。

    为了避免在保存大型WebP文件时阻塞事件循环，此函数使用线程池执行保存操作，
    同时主协程可以定期更新进度。

    Args:
        frames_pil (List[Image.Image]): 要保存的Pillow图像帧列表。
        output_path (str): 输出WebP文件的路径。
        delays_list (List[int]): 每帧的延迟时间列表（毫秒）。
        task_id (str): 用于报告进度的任务ID。
    """
    # 保存完成标志
    save_completed = threading.Event()
    save_error = None
    
    def save_webp():
        """在线程中执行WebP保存操作。"""
        nonlocal save_error
        try:
            frames_pil[0].save(
                output_path,
                format='WEBP',
                append_images=frames_pil[1:],
                save_all=True,
                duration=delays_list,  # 每帧的持续时间（毫秒）
                loop=0  # 0表示无限循环
            )
        except Exception as e:
            save_error = e
        finally:
            save_completed.set()
    
    # 在线程池中启动保存任务
    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(save_webp)
        
        # 定期更新进度直到保存完成
        progress = 97.0
        while not save_completed.is_set():
            await asyncio.sleep(0.5)  # 每0.5秒更新一次进度
            progress = min(98.0, progress + 0.01)  # 逐渐增加进度，但不超过98%
            await update_progress(task_id, progress, "正在保存WebP动画文件...", "合成")
        
        # 等待线程完成并检查错误
        future.result()  # 这会重新抛出线程中的异常（如果有的话）
        
        if save_error:
            raise save_error
        
        # 保存完成，更新进度到98%
        await update_progress(task_id, 98.0, "WebP动画保存完成", "合成")

async def _create_webp_animation_sync(
    task_id: str,
    frame_paths_list: List[str],
    delays_list: List[int],
    frame_format_list: List[str]
):
    """在后台同步创建WebP动画。

    这是一个后台任务，负责从Java后端获取所有字符画帧，
    然后将它们合成为一个WebP动图。

    Args:
        task_id (str): 用于跟踪进度的任务ID。
        frame_paths_list (List[str]): 从Java后端获取的各帧的相对路径列表。
        delays_list (List[int]): 每帧的延迟时间列表（毫秒）。
        frame_format_list (List[str]): 每帧的图像格式列表。
    """
    temp_dir = None
    output_path = None
    try:
        # 更新进度：开始处理
        await update_progress(task_id, 90.0, "开始处理WebP动画创建请求", "初始化")

        # 更新进度
        await update_progress(task_id, 91.0, f"准备处理 {len(frame_paths_list)} 帧图像", "准备")
        
        # 创建临时目录来存储图片
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        temp_dir = os.path.join(TEMP_DIR, f"webp_frames_{timestamp}")
        os.makedirs(temp_dir, exist_ok=True)
        
        logger.debug(f"创建临时目录: {temp_dir}")
        await update_progress(task_id, 92.0, "已创建临时目录，开始获取帧", "获取")
        
        # 从Java端获取每一帧并保存
        saved_frame_paths = []
        
        for i, frame_path in enumerate(frame_paths_list):
            try:
                # 更新进度
                current_progress = 93.0 + ((i+1) / float(len(frame_paths_list))) * (96.0-93.0)  # 93-96%用于获取帧
                await update_progress(
                    task_id, 
                    current_progress, 
                    f"正在获取第 {i+1}/{len(frame_paths_list)} 帧",
                    "获取",
                    i+1,  # 当前帧索引
                    len(frame_paths_list)  # 总帧数
                )
                
                # 从Java端获取图片文件
                try:
                    # 检查文件路径是否有效
                    if not frame_path or not isinstance(frame_path, str):
                        logger.warning(f"帧文件的路径无效: {frame_path}")
                        error_data = {
                            "error": f"第 {i+1} 帧文件的路径无效",
                            "task_id": task_id
                        }
                        await send_event(task_id, "webp_error", error_data, [temp_dir] if temp_dir and os.path.exists(temp_dir) else [])
                        await update_progress(task_id, 100.0, f"处理失败：第 {i+1} 帧路径无效", "错误", is_done=True)
                        
                        return
                    
                    # 检查文件类型
                    if not frame_path.lower().endswith(('.png', '.jpg', '.jpeg', '.gif', '.bmp')) or not frame_format_list[i].lower().endswith(('.png', '.jpg', '.jpeg', '.gif', '.bmp')):
                        logger.warning(f"帧文件的格式不是支持的图片格式: {frame_path}")
                        error_data = {
                            "error": f"第 {i+1} 帧的格式不是支持的图片格式",
                            "task_id": task_id
                        }
                        await send_event(task_id, "webp_error", error_data, [temp_dir] if temp_dir and os.path.exists(temp_dir) else [])
                        await update_progress(task_id, 100.0, f"处理失败：第 {i+1} 帧的格式不是支持的图片格式", "错误", is_done=True)
                        
                        return
                    
                    # 保存为临时文件
                    # 获取原始文件的后缀名
                    file_ext = frame_format_list[i].lower() or os.path.splitext(frame_path)[1].lower() or ".png"  # 如果没有后缀名，默认使用.png
                    frame_filename = f"frame_{i:04d}{file_ext}"
                    dest_path = os.path.join(temp_dir, frame_filename)
                    
                    # 从Java后端获取图片数据
                    java_backend_url = JAVA_BACKEND_URL
                    
                    # 拆分路径为临时文件夹名称和文件名
                    # 处理Windows和Unix路径分隔符
                    normalized_path = frame_path.replace('\\', '/')
                    path_parts = normalized_path.split('/')
                    if len(path_parts) != 2:
                        error_msg = f"无效的文件路径格式: {frame_path}，期望格式为'临时文件夹名/文件名' 或 '临时文件夹名\\文件名'"
                        logger.error(error_msg)
                        error_data = {
                            "error": error_msg,
                            "task_id": task_id
                        }
                        await send_event(task_id, "webp_error", error_data, [temp_dir] if temp_dir and os.path.exists(temp_dir) else [])
                        await update_progress(task_id, 100.0, f"处理失败：{error_msg}", "错误", is_done=True)
                        return
                    
                    temp_dir_name, file_name = path_parts
                    
                    # 构建请求URL，对临时文件夹名称和文件名进行URL编码
                    encoded_temp_dir_name = quote(temp_dir_name)
                    encoded_file_name = quote(file_name)
                    request_url = f"{java_backend_url}/api/get-temp-image/{encoded_temp_dir_name}/{encoded_file_name}"
                    
                    logger.debug(f"从Java后端获取图片: {request_url} (临时文件夹: {temp_dir_name}, 文件名: {file_name})")
                    response = requests.get(request_url, timeout=30)
                    
                    if response.status_code != 200:
                        error_msg = f"从Java后端获取图片失败，状态码: {response.status_code}, 响应: {response.text}"
                        logger.error(error_msg)
                        error_data = {
                            "error": error_msg,
                            "task_id": task_id
                        }
                        await send_event(task_id, "webp_error", error_data, [temp_dir] if temp_dir and os.path.exists(temp_dir) else [])
                        await update_progress(task_id, 100.0, f"处理失败：{error_msg}", "错误", is_done=True)
                        return

                    # 将获取到的图片数据保存到临时文件
                    with open(dest_path, 'wb') as f:
                        f.write(response.content)

                    # 添加相对路径而非绝对路径，以便于后续处理
                    relative_path = os.path.join(os.path.basename(temp_dir), frame_filename)
                    saved_frame_paths.append(relative_path)
                    logger.debug(f"保存第 {i+1}/{len(frame_paths_list)} 帧到 {dest_path}，相对路径: {relative_path}")
                    
                except requests.RequestException as e:
                    logger.error(f"获取第 {i+1} 帧时出错: {str(e)}")
                    error_data = {
                        "error": f"获取第 {i+1} 帧时出错: {str(e)}",
                        "task_id": task_id
                    }
                    await send_event(task_id, "webp_error", error_data, [temp_dir] if temp_dir and os.path.exists(temp_dir) else [])
                    await update_progress(task_id, 100.0, f"处理失败：获取第 {i+1} 帧时出错: {str(e)}", "错误", is_done=True)
                    
                    return
                
                # 添加短暂延迟，避免进度更新过于频繁
                await asyncio.sleep(PROGRESS_UPDATE_INTERVAL)
                
            except Exception as e:
                logger.error(f"处理第 {i+1} 帧时出错: {str(e)}")
                error_data = {
                    "error": f"处理第 {i+1} 帧时出错: {str(e)}",
                    "task_id": task_id
                }
                await send_event(task_id, "webp_error", error_data, [temp_dir] if temp_dir and os.path.exists(temp_dir) else [])
                await update_progress(task_id, 100.0, f"处理失败：处理第 {i+1} 帧时出错: {str(e)}", "错误", is_done=True)
                
                return
        
        # 更新进度，通知前端所有帧已获取完成
        await update_progress(task_id, 96.0, "所有帧获取完成，开始创建WebP动画", "合成")

        # 准备创建WebP动画
        output_filename = f"animation_{timestamp}.webp"
        output_path = os.path.join(TEMP_DIR, output_filename)

        try:
            # 使用Pillow库创建WebP动画
            await update_progress(task_id, 96.0, "正在加载图像帧", "合成")
            # 从临时文件加载所有帧图像，需要使用绝对路径
            absolute_frame_paths = [os.path.join(TEMP_DIR, path) for path in saved_frame_paths]
            frames_pil = [Image.open(frame_path) for frame_path in absolute_frame_paths]
            
            # 更新进度
            await update_progress(task_id, 97.0, "正在合成WebP动画", "合成")
            
            # 使用多线程保存WebP动画并定期更新进度
            await _save_webp_with_progress(frames_pil, output_path, delays_list, task_id)
            
            logger.debug(f"WebP动画已创建: {output_path}")
            
            # 构建结果数据，使用 CreateWebpResponse 模型
            result_response = CreateWebpResponse(
                webp=output_filename,  # 使用相对路径
                task_id=task_id
            )
            
            # 通过SSE发送处理结果
            await send_event(task_id, "webp_result", result_response.model_dump())
            logger.debug(f"任务 {task_id}: WebP动画创建结果已通过SSE发送")
            
            # 最终进度更新
            await update_progress(task_id, 98.0, "WebP动画创建完成，等待客户端获取图片", "完成", len(saved_frame_paths), len(saved_frame_paths), is_done=True)
            
            # 注意：不在此处关闭SSE连接，以等待Java端处理完webp_result事件后再由其发起关闭请求
            logger.debug(f"WebP动画创建任务 {task_id} 已完成，等待Java端处理完成")
            
        except Exception as e:
            logger.error(f"创建WebP动画时出错: {str(e)}")
            
            # 收集需要清理的临时文件路径
            temp_paths_to_clean = []
            if temp_dir and os.path.exists(temp_dir):
                temp_paths_to_clean.append(temp_dir)
            if output_path and os.path.exists(output_path):
                temp_paths_to_clean.append(output_path)
            
            # 构建错误结果数据，使用 ErrorResponse 模型
            error_response = ErrorResponse(
                error=f"创建WebP动画时出错: {str(e)}",
                task_id=task_id
            )
            # 通过SSE发送错误信息，并传递临时文件路径
            await send_event(task_id, "webp_error", error_response.model_dump(), temp_paths_to_clean)
            logger.debug(f"任务 {task_id}: WebP动画创建错误已通过SSE发送，临时文件路径: {temp_paths_to_clean}")
            
            await update_progress(task_id, 100.0, f"处理失败：创建WebP动画时出错: {str(e)}", "错误", is_done=False)
            # 注意：不在此处关闭SSE连接，以等待Java端处理完webp_error事件后再由其发起关闭请求
            logger.debug(f"WebP动画创建错误任务 {task_id} 已发送，等待Java端处理完成")
    
    except Exception as e:
        # 捕获所有其他未预料到的异常
        logger.error(f"处理请求时出现意外错误: {str(e)}")
        
        # 收集需要清理的临时文件路径
        temp_paths_to_clean = []
        if temp_dir and os.path.exists(temp_dir):
            temp_paths_to_clean.append(temp_dir)
        if output_path and os.path.exists(output_path):
            temp_paths_to_clean.append(output_path)
        
        # 构建错误结果数据，使用 ErrorResponse 模型
        error_response = ErrorResponse(
            error=f"处理请求时出现出错: {str(e)}",
            task_id=task_id
        )
        
        # 通过SSE发送错误信息，并传递临时文件路径
        await send_event(task_id, "webp_error", error_response.model_dump(), temp_paths_to_clean)
        logger.debug(f"任务 {task_id}: WebP动画创建请求处理的错误已通过SSE发送，临时文件路径: {temp_paths_to_clean}")
        
        # 更新进度，通知前端处理失败，但不关闭SSE连接
        await update_progress(task_id, 100.0, f"处理失败：{str(e)}", "错误", is_done=False)
        # 注意：不在此处关闭SSE连接，以等待Java端处理完webp_error事件后再由其发起关闭请求
        logger.debug(f"WebP动画创建请求处理错误的任务 {task_id} 已发送，等待Java端处理完成")
    finally:
        # 确保在任务结束时（无论成功或失败）都清理临时文件
        cleanup_temp_files()