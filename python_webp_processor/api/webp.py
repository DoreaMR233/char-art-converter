import asyncio
import json
import logging
import os
import traceback
from datetime import datetime
from typing import Optional, List
from urllib.parse import quote

import requests
from PIL import Image, ImageSequence
from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Path
from fastapi.responses import FileResponse
from pydantic import BaseModel

from api.progress import update_progress, close_progress_connection, create_progress
from config import TEMP_DIR, MAX_CONTENT_LENGTH, PROGRESS_UPDATE_INTERVAL, JAVA_BACKEND_URL
from utils.utils import cleanup_temp_files

logger: logging.Logger = logging.getLogger(__name__)

router = APIRouter()

# 响应模型
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

@router.get('/get-image/{file_path:path}')
async def get_image(file_path: str = Path(..., description="图片文件的路径（相对于TEMP_DIR的路径）")):
    """获取图片接口。
    
    根据文件路径返回对应的图片文件，支持多种图片格式。
    
    Args:
        file_path (str): 图片文件的相对路径
        
    Returns:
        FileResponse: 图片文件响应
        
    Raises:
        HTTPException: 当文件不存在、路径非法或其他错误时抛出
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
                    logger.info(f"文件已成功传输并删除: {full_path}")
                    
                    # 检查目录是否为空，如果为空则删除
                    if os.path.exists(dir_path) and len(os.listdir(dir_path)) == 0:
                        os.rmdir(dir_path)
                        logger.info(f"空文件夹已删除: {dir_path}")
            except Exception as e:
                logger.error(f"删除文件或文件夹时出错: {str(e)}")
        
        # 注册后台任务
        response.background = cleanup_file
        
        logger.info(f"返回文件: {full_path}, 媒体类型: {media_type}")
        return response
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"获取图片时出错: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取图片时出错: {str(e)}")

@router.post('/process-webp', response_model=ProcessWebpResponse)
async def process_webp(
    image: UploadFile = File(..., description="WebP动图文件"),
    task_id: Optional[str] = Form(None, description="任务ID（可选）")
):
    """处理WebP动图，提取帧并返回帧信息。
    
    接收WebP动图文件，提取所有帧并保存为PNG格式，返回帧信息和延迟时间。
    
    Args:
        image (UploadFile): 上传的WebP动图文件
        task_id (Optional[str]): 可选的任务ID，用于进度跟踪
        
    Returns:
        ProcessWebpResponse: 包含帧数、延迟时间、帧文件路径和任务ID的响应对象
        
    Raises:
        HTTPException: 当文件格式不正确、文件过大或处理失败时抛出
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
            # 如果请求中没有提供任务ID，则创建一个新的
            progress_response = await create_progress()
            task_id = progress_response.task_id
            logger.info(f"为WebP处理创建新的任务ID: {task_id}")
        else:
            logger.info(f"使用请求提供的任务ID: {task_id}")
        
        # 更新进度：开始处理
        logger.info(f"任务 {task_id}: 开始处理WebP文件，大小: {file_size} 字节")
        update_progress(task_id, 31, "WebP文件已接收，开始处理", "初始化")
        logger.debug(f"任务 {task_id}: 进度更新 31%")
        
        # 保存上传的文件到临时目录
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        temp_path = os.path.join(TEMP_DIR, f"{timestamp}_{image.filename}")
        
        with open(temp_path, 'wb') as f:
            f.write(file_content)
        
        logger.info(f"任务 {task_id}: WebP文件已保存到临时路径: {temp_path}")
        update_progress(task_id, 32, "WebP文件已保存，准备解析", "文件保存")
        logger.debug(f"任务 {task_id}: 进度更新 32%")
        
        # 使用Pillow打开WebP文件
        with Image.open(temp_path) as img:
            # 检查是否为动图
            is_animated = getattr(img, "is_animated", False)
            logger.info(f"任务 {task_id}: WebP是否为动图: {is_animated}")
            if not is_animated:
                logger.warning(f"任务 {task_id}: 上传的WebP不是动图")
                update_progress(task_id, 100, "处理失败：上传的WebP不是动图", "错误", is_done=True)
                logger.info(f"任务 {task_id}: 关闭进度连接")
                close_progress_connection(task_id)
                raise HTTPException(status_code=400, detail="上传的WebP不是动图")
            
            # 获取帧数
            frame_count = getattr(img, "n_frames", 0)
            if frame_count <= 0:
                update_progress(task_id, 100, "处理失败：无法获取WebP帧数", "错误", is_done=True)
                close_progress_connection(task_id)
                raise HTTPException(status_code=400, detail="无法获取WebP帧数")
            
            logger.info(f"任务 {task_id}: WebP动图帧数: {frame_count}")
            update_progress(task_id, 33, f"WebP动图帧数: {frame_count}，开始提取帧", "帧提取")
            logger.debug(f"任务 {task_id}: 进度更新 33%")
            
            # 提取每一帧和延迟信息
            delays = []
            frame_paths = []
            
            # 创建临时目录来存储帧
            timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
            frames_dir = os.path.join(TEMP_DIR, f"webp_frames_{timestamp}")
            os.makedirs(frames_dir, exist_ok=True)
            logger.info(f"任务 {task_id}: 创建临时目录存储帧: {frames_dir}")
            
            for i, frame in enumerate(ImageSequence.Iterator(img)):
                # 计算当前进度
                current_progress = 33 + ((i+1) / float(frame_count)) * (37 - 33)
                update_progress(
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
                
                logger.info(f"任务 {task_id}: 处理第 {i+1}/{frame_count} 帧, 延迟: {delay}ms, 保存到: {frame_path}")
                
                # 添加短暂延迟，避免进度更新过于频繁
                await asyncio.sleep(PROGRESS_UPDATE_INTERVAL)
            
            # 更新进度：完成处理
            update_progress(task_id, 37, "帧提取完成，准备返回结果", "完成", frame_count, frame_count)
            logger.info(f"任务 {task_id}: 所有帧提取完成，进度更新 37%")
            
            # 返回结果，使用帧路径
            result = ProcessWebpResponse(
                frameCount=frame_count,
                delays=delays,
                frames=frame_paths,
                task_id=task_id
            )
            
            # 最终进度更新
            update_progress(task_id, 38, "处理完成，等待客户端获取图片", "完成", frame_count, frame_count, is_done=False)
            logger.info(f"任务 {task_id}: 最终进度更新 38%，等待客户端获取图片")
            
            # 确保SSE连接已关闭
            logger.info(f"任务 {task_id}: WebP处理已完成，准备关闭SSE连接")
            close_progress_connection(task_id)
            logger.info(f"任务 {task_id}: SSE连接已关闭")
            

            
    except HTTPException:
        raise
    except Exception as e:
        error_details = traceback.format_exc()
        logger.error(f"处理WebP时出错: {str(e)}\n{error_details}")
        # 如果有task_id，关闭SSE连接
        if task_id:
            update_progress(task_id, 100, f"处理失败：{str(e)}", "错误", is_done=True)
            close_progress_connection(task_id)
        raise HTTPException(status_code=500, detail=f"处理失败: {str(e)}")
    finally:
        # 先清理临时文件
        cleanup_temp_files()
    return result

@router.post('/create-webp-animation', response_model=CreateWebpResponse)
async def create_webp_animation(
    frame_paths: str = Form(..., description="帧文件路径列表（JSON格式）"),
    delays: str = Form(..., description="延迟时间列表（JSON格式，毫秒）"),
    frame_format: str = Form(..., description="帧格式列表（JSON格式）"),
    task_id: Optional[str] = Form(None, description="任务ID（可选）")
):
    """创建WebP动画。
    
    根据提供的帧文件路径、延迟时间和格式信息，从Java后端获取帧图片并合成WebP动画。
    
    Args:
        frame_paths (str): 帧文件路径列表的JSON字符串
        delays (str): 每帧延迟时间列表的JSON字符串（毫秒）
        frame_format (str): 帧格式列表的JSON字符串
        task_id (Optional[str]): 可选的任务ID，用于进度跟踪
        
    Returns:
        CreateWebpResponse: 包含生成的WebP动画文件路径和任务ID的响应对象
        
    Raises:
        HTTPException: 当参数解析失败、帧数量不匹配或处理失败时抛出
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
            # 如果请求中没有提供任务ID，则创建一个新的
            progress_response = await create_progress()
            task_id = progress_response.task_id
        
        # 更新进度：开始处理
        update_progress(task_id, 90, "开始处理WebP动画创建请求", "初始化")
        
        # 检查帧和延迟的数量是否匹配
        if len(frame_paths_list) != len(delays_list) or len(frame_paths_list) != len(frame_format_list):
            logger.warning(f"帧数量({len(frame_paths_list)})、延迟数量({len(delays_list)})、帧格式数量({len(frame_format_list)})不匹配")
            update_progress(task_id, 100, "处理失败：帧数量、延迟数量、帧格式数量不匹配", "错误", is_done=True)
            close_progress_connection(task_id)
            raise HTTPException(status_code=400, detail="帧数量、延迟数量、帧格式数量不匹配")
        
        # 检查是否有帧
        if len(frame_paths_list) == 0:
            logger.warning("没有提供帧")
            update_progress(task_id, 100, "处理失败：没有提供帧", "错误", is_done=True)
            close_progress_connection(task_id)
            raise HTTPException(status_code=400, detail="没有提供帧")
        
        # 更新进度
        update_progress(task_id, 91, f"准备处理 {len(frame_paths_list)} 帧图像", "准备")
        
        # 创建临时目录来存储图片
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        temp_dir = os.path.join(TEMP_DIR, f"webp_frames_{timestamp}")
        os.makedirs(temp_dir, exist_ok=True)
        
        logger.info(f"创建临时目录: {temp_dir}")
        update_progress(task_id, 92, "已创建临时目录，开始获取帧", "获取")
        
        # 从Java端获取每一帧并保存
        saved_frame_paths = []
        
        for i, frame_path in enumerate(frame_paths_list):
            try:
                # 更新进度
                current_progress = 93 + ((i+1) / float(len(frame_paths_list))) * (96-93)  # 15-60%用于获取帧
                update_progress(
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
                        logger.warning(f"帧文件路径无效: {frame_path}")
                        update_progress(task_id, 100, f"处理失败：第 {i+1} 帧路径无效", "错误", is_done=True)
                        close_progress_connection(task_id)
                        raise HTTPException(status_code=400, detail=f"第 {i+1} 帧路径无效")
                    
                    # 检查文件类型
                    if not frame_path.lower().endswith(('.png', '.jpg', '.jpeg', '.gif', '.bmp')) or not frame_format_list[i].lower().endswith(('.png', '.jpg', '.jpeg', '.gif', '.bmp')):
                        logger.warning(f"帧文件不是支持的图片格式: {frame_path}")
                        update_progress(task_id, 100, f"处理失败：第 {i+1} 帧不是支持的图片格式", "错误", is_done=True)
                        close_progress_connection(task_id)
                        raise HTTPException(status_code=400, detail=f"第 {i+1} 帧不是支持的图片格式")
                    
                    # 保存为临时文件
                    # 获取原始文件的后缀名
                    file_ext = frame_format_list[i].lower() or os.path.splitext(frame_path)[1].lower() or ".png"  # 如果没有后缀名，默认使用.png
                    frame_filename = f"frame_{i:04d}{file_ext}"
                    dest_path = os.path.join(temp_dir, frame_filename)
                    
                    # 从Java后端获取图片数据
                    java_backend_url = JAVA_BACKEND_URL
                    
                    # 构建请求URL，对文件路径进行URL编码
                    encoded_path = quote(frame_path)
                    request_url = f"{java_backend_url}/api/get-temp-image/{encoded_path}"
                    
                    logger.info(f"从Java后端获取图片: {request_url}")
                    response = requests.get(request_url, timeout=30)
                    
                    if response.status_code != 200:
                        error_msg = f"从Java后端获取图片失败，状态码: {response.status_code}, 响应: {response.text}"
                        logger.error(error_msg)
                        update_progress(task_id, 100, f"处理失败：{error_msg}", "错误", is_done=True)
                        close_progress_connection(task_id)
                        raise HTTPException(status_code=400, detail=error_msg)
                    
                    # 将获取到的图片数据保存到临时文件
                    with open(dest_path, 'wb') as f:
                        f.write(response.content)
                    
                    # 添加相对路径而非绝对路径
                    relative_path = os.path.join(os.path.basename(temp_dir), frame_filename)
                    saved_frame_paths.append(relative_path)
                    logger.info(f"保存第 {i+1}/{len(frame_paths_list)} 帧到 {dest_path}，相对路径: {relative_path}")
                    
                except requests.RequestException as e:
                    logger.error(f"获取第 {i+1} 帧时出错: {str(e)}")
                    update_progress(task_id, 100, f"处理失败：获取第 {i+1} 帧时出错: {str(e)}", "错误", is_done=True)
                    close_progress_connection(task_id)
                    raise HTTPException(status_code=400, detail=f"获取第 {i+1} 帧时出错: {str(e)}")
                
                # 添加短暂延迟，避免进度更新过于频繁
                await asyncio.sleep(PROGRESS_UPDATE_INTERVAL)
                
            except HTTPException:
                raise
            except Exception as e:
                logger.error(f"处理第 {i+1} 帧时出错: {str(e)}")
                update_progress(task_id, 100, f"处理失败：处理第 {i+1} 帧时出错: {str(e)}", "错误", is_done=True)
                close_progress_connection(task_id)
                raise HTTPException(status_code=400, detail=f"处理第 {i+1} 帧时出错: {str(e)}")
        
        # 更新进度
        update_progress(task_id, 96, "所有帧获取完成，开始创建WebP动画", "合成")
        
        # 创建WebP动画
        output_filename = f"animation_{timestamp}.webp"
        output_path = os.path.join(TEMP_DIR, output_filename)
        
        try:
            # 使用PIL创建WebP动画
            update_progress(task_id, 96, "正在加载图像帧", "合成")
            # 需要使用绝对路径打开图像文件
            absolute_frame_paths = [os.path.join(TEMP_DIR, path) for path in saved_frame_paths]
            frames_pil = [Image.open(frame_path) for frame_path in absolute_frame_paths]
            
            # 更新进度
            update_progress(task_id, 97, "正在合成WebP动画", "合成")
            
            # 保存为WebP动画
            frames_pil[0].save(
                output_path,
                format='WEBP',
                append_images=frames_pil[1:],
                save_all=True,
                duration=delays_list,  # 每帧的持续时间（毫秒）
                loop=0  # 0表示无限循环
            )
            
            logger.info(f"WebP动画已创建: {output_path}")
            
            # 返回WebP文件路径（相对路径）
            result = CreateWebpResponse(
                webp=output_filename,  # 使用相对路径
                task_id=task_id
            )
            
            # 最终进度更新
            update_progress(task_id, 98, "WebP动画创建完成，等待客户端获取图片", "完成", len(saved_frame_paths), len(saved_frame_paths), is_done=False)
            
            # 确保SSE连接已关闭
            logger.info(f"WebP动画创建任务 {task_id} 已完成，确保SSE连接已关闭")
            close_progress_connection(task_id)
            

            
        except Exception as e:
            logger.error(f"创建WebP动画时出错: {str(e)}")
            update_progress(task_id, 100, f"处理失败：创建WebP动画时出错: {str(e)}", "错误", is_done=True)
            close_progress_connection(task_id)
            raise HTTPException(status_code=500, detail=f"创建WebP动画时出错: {str(e)}")
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"处理请求时出错: {str(e)}")
        # 如果有task_id，更新进度并关闭SSE连接
        if task_id:
            update_progress(task_id, 100, f"处理失败：{str(e)}", "错误")
            close_progress_connection(task_id)
        raise HTTPException(status_code=500, detail=f"处理请求时出错: {str(e)}")
    finally:
        # 先清理临时文件
        cleanup_temp_files()
    return result