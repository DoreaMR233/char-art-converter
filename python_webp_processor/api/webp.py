from flask import request, jsonify
import os
import io
import base64
import traceback
import logging
from datetime import datetime
from typing import Dict, Tuple, Any
from PIL import Image, ImageSequence
from api import api_bp
from config import TEMP_DIR, MAX_CONTENT_LENGTH

logger: logging.Logger = logging.getLogger(__name__)

@api_bp.route('/process-webp', methods=['POST'])
def process_webp() -> Tuple[Dict[str, Any], int]:
    """
    处理上传的WebP动图，提取帧和延迟信息
    
    Returns:
        tuple[dict, int]: 包含以下信息的JSON响应和HTTP状态码
            - 成功时返回:
                {
                    "frameCount": 帧数,
                    "delays": [帧1延迟, 帧2延迟, ...],
                    "frames": [帧1的base64编码, 帧2的base64编码, ...]
                }
            - 失败时返回:
                {"error": 错误信息}
    """
    try:
        # 检查请求方法和内容类型
        if request.method != 'POST':
            return jsonify({"error": "只支持POST请求"}), 405
            
        # 检查是否有文件上传
        if 'image' not in request.files:
            logger.warning("请求中没有上传文件")
            return jsonify({"error": "没有上传文件"}), 400
        
        file = request.files['image']
        
        # 检查文件名
        if file.filename == '':
            logger.warning("上传的文件没有文件名")
            return jsonify({"error": "未选择文件"}), 400
        
        # 检查文件类型
        if not file.filename.lower().endswith('.webp'):
            logger.warning(f"上传的文件不是WebP格式: {file.filename}")
            return jsonify({"error": "只支持WebP格式"}), 400
            
        # 检查文件大小
        file.seek(0, os.SEEK_END)
        file_size = file.tell()
        file.seek(0)
        
        if file_size > MAX_CONTENT_LENGTH:
            logger.warning(f"文件过大: {file_size} 字节, 超过最大限制 {MAX_CONTENT_LENGTH} 字节")
            return jsonify({"error": f"文件过大，最大允许 {MAX_CONTENT_LENGTH/1024/1024:.1f}MB"}), 413
        
        # 保存上传的文件到临时目录
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        temp_path = os.path.join(TEMP_DIR, f"{timestamp}_{file.filename}")
        file.save(temp_path)
        
        logger.info(f"WebP文件已保存到临时路径: {temp_path}")
        
        # 使用Pillow打开WebP文件
        with Image.open(temp_path) as img:
            # 检查是否为动图
            is_animated = getattr(img, "is_animated", False)
            if not is_animated:
                return jsonify({"error": "上传的WebP不是动图"}), 400
            
            # 获取帧数
            frame_count = getattr(img, "n_frames", 0)
            if frame_count <= 0:
                return jsonify({"error": "无法获取WebP帧数"}), 400
            
            logger.info(f"WebP动图帧数: {frame_count}")
            
            # 提取每一帧和延迟信息
            frames = []
            delays = []
            
            for i, frame in enumerate(ImageSequence.Iterator(img)):
                # 复制帧以避免引用问题
                frame_copy = frame.copy()
                
                # 将帧转换为RGB模式（如果需要）
                if frame_copy.mode != 'RGB':
                    frame_copy = frame_copy.convert('RGB')
                
                # 将帧转换为字节流
                buffer = io.BytesIO()
                frame_copy.save(buffer, format="PNG")
                frame_bytes = buffer.getvalue()
                
                # 将帧添加到列表
                frames.append(base64.b64encode(frame_bytes).decode('utf-8'))
                
                # 获取帧延迟（毫秒）
                # WebP帧延迟存储在info字典中，如果没有则使用默认值100ms
                delay = frame.info.get('duration', 100)
                delays.append(delay)
                
                logger.info(f"处理第 {i+1}/{frame_count} 帧, 延迟: {delay}ms")
            
            # 返回结果
            result = {
                "frameCount": frame_count,
                "delays": delays,
                "frames": frames
            }
            
            return jsonify(result)
            
    except Exception as e:
        error_details = traceback.format_exc()
        logger.error(f"处理WebP时出错: {str(e)}\n{error_details}")
        return jsonify({"error": f"处理失败: {str(e)}"}), 500
    finally:
        # 清理临时文件
        try:
            if 'temp_path' in locals() and os.path.exists(temp_path):
                os.remove(temp_path)
                logger.info(f"已删除临时文件: {temp_path}")
        except Exception as e:
            logger.warning(f"清理临时文件失败: {str(e)}")

@api_bp.route('/create-webp-animation', methods=['POST'])
def create_webp_animation() -> Tuple[Dict[str, Any], int]:
    """
    接收字符画图片临时文件路径列表，将它们组合成一个新的WebP动画
    
    请求格式：
    {
        "framePaths": ["路径1", "路径2", ...],
        "delays": [延迟1, 延迟2, ...]
    }
    
    Returns:
        tuple[dict, int]: 包含以下信息的JSON响应和HTTP状态码
            - 成功时返回:
                {"webpPath": "WebP文件临时路径"}
            - 失败时返回:
                {"error": 错误信息}
    """
    try:
        # 检查请求方法
        if request.method != 'POST':
            return jsonify({"error": "只支持POST请求"}), 405
            
        # 检查请求内容
        if not request.is_json:
            logger.warning("请求不是JSON格式")
            return jsonify({"error": "请求必须是JSON格式"}), 400
        
        data = request.get_json()
        
        # 检查必要的字段
        if 'framePaths' not in data or 'delays' not in data:
            logger.warning("请求缺少必要的字段")
            return jsonify({"error": "请求必须包含framePaths和delays字段"}), 400
        
        frame_paths = data['framePaths']
        delays = data['delays']
        
        # 检查路径列表和延迟列表长度是否一致
        if len(frame_paths) != len(delays):
            logger.warning("帧路径列表和延迟列表长度不一致")
            return jsonify({"error": "帧路径列表和延迟列表长度必须一致"}), 400
        
        # 检查是否有帧
        if len(frame_paths) == 0:
            logger.warning("没有提供帧")
            return jsonify({"error": "至少需要一个帧"}), 400
        
        # 检查所有路径是否存在
        for path in frame_paths:
            if not os.path.exists(path):
                logger.warning(f"帧路径不存在: {path}")
                return jsonify({"error": f"帧路径不存在: {path}"}), 400
        
        # 读取所有图片
        frames = []
        for path in frame_paths:
            try:
                img = Image.open(path)
                frames.append(img.copy())
            except Exception as e:
                logger.error(f"读取图片失败: {path}, 错误: {str(e)}")
                return jsonify({"error": f"读取图片失败: {path}"}), 500
        
        # 创建临时文件用于保存WebP动画
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        output_path = os.path.join(TEMP_DIR, f"{timestamp}_animation.webp")
        
        # 保存为WebP动画
        frames[0].save(
            output_path,
            format="WEBP",
            append_images=frames[1:],
            save_all=True,
            duration=delays,  # 延迟时间列表（毫秒）
            loop=0  # 0表示无限循环
        )
        
        logger.info(f"已创建WebP动画: {output_path}, 共{len(frames)}帧")
        
        # 返回WebP文件路径
        return jsonify({
            "webpPath": output_path
        })
        
    except Exception as e:
        error_details = traceback.format_exc()
        logger.error(f"创建WebP动画时出错: {str(e)}\n{error_details}")
        return jsonify({"error": f"处理失败: {str(e)}"}), 500