from flask import Blueprint

# 创建API蓝图
api_bp: Blueprint = Blueprint('api', __name__, url_prefix='/api')

# 导入各个模块的路由
from api import webp, health