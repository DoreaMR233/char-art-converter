@echo off
echo 正在启动WebP处理服务...

:: 检查Python是否安装.
python --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo 错误: 未找到Python，请安装Python 3.6+
    pause
    exit /b 1
)

:: 检查是否已安装依赖.
if not exist "%~dp0venv" (
    echo 正在创建虚拟环境...
    python -m venv "%~dp0venv"
    echo 正在安装依赖...
    call "%~dp0venv\Scripts\activate.bat"
    pip install -r "%~dp0requirements.txt"
) else (
    call "%~dp0venv\Scripts\activate.bat"
)

:: 启动服务.
echo 正在启动服务...
python "%~dp0app.py"

pause