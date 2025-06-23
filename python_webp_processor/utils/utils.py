import logging
import os
import time
from config import TEMP_DIR, TEMP_FILE_TTL

logger: logging.Logger = logging.getLogger(__name__)

def cleanup_temp_files():
    """清理过期的临时文件。
    
    扫描临时目录，删除超过保留时间的文件和空目录。
    保留时间由配置项TEMP_FILE_TTL定义（以秒为单位）。
    
    Raises:
        Exception: 当清理过程中发生错误时记录日志但不抛出异常
    """
    try:
        if not os.path.exists(TEMP_DIR):
            return
        
        current_time = time.time()
        cutoff_time = current_time - TEMP_FILE_TTL
        
        for filename in os.listdir(TEMP_DIR):
            file_path = os.path.join(TEMP_DIR, filename)
            
            # 跳过目录
            if os.path.isdir(file_path):
                # 检查目录是否为空，如果为空则删除
                try:
                    if not os.listdir(file_path):
                        os.rmdir(file_path)
                        logger.info(f"删除空目录: {file_path}")
                except OSError:
                    pass
                continue
            
            # 检查文件修改时间
            try:
                file_mtime = os.path.getmtime(file_path)
                if file_mtime < cutoff_time:
                    os.remove(file_path)
                    logger.info(f"删除过期文件: {file_path}")
            except OSError as e:
                logger.warning(f"删除文件失败 {file_path}: {e}")
                
    except Exception as e:
        logger.error(f"清理临时文件时出错: {e}")