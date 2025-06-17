import os
import logging
from datetime import datetime
from config import TEMP_DIR, TEMP_FILE_TTL

logger: logging.Logger = logging.getLogger(__name__)

def cleanup_temp_files() -> None:
    """
    清理过期的临时文件
    
    清理TEMP_DIR目录中超过TEMP_FILE_TTL秒未修改的临时文件
    
    Returns:
        None
    """
    try:
        # 检查临时目录是否存在
        if not os.path.exists(TEMP_DIR):
            logger.warning(f"临时目录不存在: {TEMP_DIR}")
            return
            
        now = datetime.now()
        count = 0
        
        for filename in os.listdir(TEMP_DIR):
            file_path = os.path.join(TEMP_DIR, filename)
            
            # 检查文件是否为常规文件
            if not os.path.isfile(file_path):
                continue
                
            # 获取文件修改时间
            file_mod_time = datetime.fromtimestamp(os.path.getmtime(file_path))
            age_seconds = (now - file_mod_time).total_seconds()
            
            # 如果文件超过保留时间，则删除
            if age_seconds > TEMP_FILE_TTL:
                try:
                    os.remove(file_path)
                    count += 1
                    logger.debug(f"已删除过期临时文件: {file_path}, 存在时间: {age_seconds:.1f}秒")
                except Exception as e:
                    logger.error(f"删除临时文件失败: {file_path}, 错误: {str(e)}")
        
        if count > 0:
            logger.info(f"已清理 {count} 个过期临时文件")
    
    except Exception as e:
        logger.error(f"清理临时文件时出错: {str(e)}")