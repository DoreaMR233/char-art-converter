#!/bin/bash

echo "正在启动WebP处理服务..."

# 检查Python是否安装
if ! command -v python3 &> /dev/null; then
    echo "错误: 未找到Python，请安装Python 3.6+"
    exit 1
fi

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# 检查是否已安装依赖
if [ ! -d "$SCRIPT_DIR/venv" ]; then
    echo "正在创建虚拟环境..."
    python3 -m venv "$SCRIPT_DIR/venv"
    echo "正在安装依赖..."
    source "$SCRIPT_DIR/venv/bin/activate"
    pip install -r "$SCRIPT_DIR/requirements.txt"
else
    source "$SCRIPT_DIR/venv/bin/activate"
fi

# 启动服务
echo "正在启动服务..."
python3 "$SCRIPT_DIR/app.py"