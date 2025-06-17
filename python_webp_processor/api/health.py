from flask import jsonify
from datetime import datetime
from typing import Dict, Tuple, Any
from api import api_bp

@api_bp.route('/health', methods=['GET'])
def health_check() -> Tuple[Dict[str, Any], int]:
    """
    健康检查接口
    
    Returns:
        tuple[dict, int]: 包含服务状态信息的JSON响应和HTTP状态码
    """
    return jsonify({
        "status": "ok",
        "timestamp": datetime.now().isoformat(),
        "service": "WebP Processor API",
        "version": "1.0.0"
    })

@api_bp.route('/version', methods=['GET'])
def version() -> Tuple[Dict[str, Any], int]:
    """
    返回API版本信息
    
    Returns:
        tuple[dict, int]: 包含API版本和端点信息的JSON响应和HTTP状态码
    """
    return jsonify({
        'name': 'WebP Processor API',
        'version': '1.1.0',
        'description': 'WebP动图处理服务',
        'endpoints': [
            {'path': '/api/health', 'method': 'GET', 'description': '健康检查'},
            {'path': '/api/process-webp', 'method': 'POST', 'description': '处理WebP动图'},
            {'path': '/api/create-webp-animation', 'method': 'POST', 'description': '创建WebP动画'},
            {'path': '/api/version', 'method': 'GET', 'description': '版本信息'}
        ]
    })