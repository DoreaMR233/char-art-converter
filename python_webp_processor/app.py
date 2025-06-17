from flask import Flask
from config import MAX_CONTENT_LENGTH, PORT, DEBUG, TEMP_DIR, SHOULD_CLEANUP_TEMP_DIR
from utils.utils import cleanup_temp_files
import logging
import atexit
import shutil

logger: logging.Logger = logging.getLogger(__name__)

def create_app() -> Flask:
    """
    创建并配置Flask应用
    
    Returns:
        Flask: 配置好的Flask应用实例
    """
    app = Flask(__name__)
    
    # 配置最大上传文件大小
    app.config['MAX_CONTENT_LENGTH'] = MAX_CONTENT_LENGTH
    
    # 注册蓝图
    from api import api_bp
    app.register_blueprint(api_bp)
    
    return app

# 注册退出时的清理函数
def cleanup_on_exit():
    """程序退出时执行的清理函数"""
    # 先清理临时文件
    cleanup_temp_files()
    
    # 如果需要，删除整个临时目录
    if SHOULD_CLEANUP_TEMP_DIR:
        try:
            shutil.rmtree(TEMP_DIR)
            logger.info(f"已删除临时目录: {TEMP_DIR}")
        except Exception as e:
            logger.error(f"删除临时目录时出错: {str(e)}")

# 注册退出处理函数
atexit.register(cleanup_on_exit)

if __name__ == '__main__':
    # 清理过期的临时文件
    cleanup_temp_files()
    
    # 创建应用
    app = create_app()
    
    # 启动应用
    logger.info(f"启动WebP处理服务，监听端口: {PORT}, 调试模式: {DEBUG}")
    app.run(host='0.0.0.0', port=PORT, debug=DEBUG)