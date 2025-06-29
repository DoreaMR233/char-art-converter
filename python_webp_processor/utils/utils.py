import logging
import os
import time
from config import TEMP_DIR, TEMP_FILE_TTL

logger: logging.Logger = logging.getLogger(__name__)

def cleanup_temp_files(ignore_ttl=False, target_path=None):
    """清理临时文件。
    
    扫描临时目录或指定路径，删除文件和空目录。
    
    Args:
        ignore_ttl (bool): 是否忽略保留时间。
                          True: 删除所有文件，不考虑保留时间
                          False: 只删除超过保留时间的文件（默认行为）
        target_path (str, optional): 指定要清理的文件或目录路径。
                                    如果为None，则清理默认临时目录
    
    Raises:
        Exception: 当清理过程中发生错误时记录日志但不抛出异常
    """
    try:
        # 如果指定了target_path，则清理指定路径；否则清理默认临时目录
        if target_path:
            if not os.path.exists(target_path):
                logger.debug(f"指定路径不存在，跳过清理: {target_path}")
                return
            
            # 如果是文件，直接删除
            if os.path.isfile(target_path):
                try:
                    os.remove(target_path)
                    logger.debug(f"删除指定文件: {target_path}")
                except OSError as e:
                    logger.warning(f"删除指定文件失败 {target_path}: {e}")
                return
            
            # 如果是目录，递归删除目录及其内容
            if os.path.isdir(target_path):
                try:
                    import shutil
                    shutil.rmtree(target_path)
                    logger.debug(f"删除指定目录: {target_path}")
                except OSError as e:
                    logger.warning(f"删除指定目录失败 {target_path}: {e}")
                return
        
        # 默认行为：清理临时目录
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
                        logger.debug(f"删除空目录: {file_path}")
                except OSError:
                    pass
                continue
            
            # 根据ignore_ttl参数决定是否检查文件修改时间
            try:
                should_delete = ignore_ttl
                if not ignore_ttl:
                    file_mtime = os.path.getmtime(file_path)
                    should_delete = file_mtime < cutoff_time
                
                if should_delete:
                    os.remove(file_path)
                    if ignore_ttl:
                        logger.debug(f"强制删除文件: {file_path}")
                    else:
                        logger.info(f"删除过期文件: {file_path}")
            except OSError as e:
                logger.warning(f"删除文件失败 {file_path}: {e}")
                
    except Exception as e:
        logger.error(f"清理临时文件时出错: {e}")