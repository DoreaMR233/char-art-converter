@echo off
echo ��������WebP�������...

:: ���Python�Ƿ�װ.
python --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ����: δ�ҵ�Python���밲װPython 3.6+
    pause
    exit /b 1
)

:: ����Ƿ��Ѱ�װ����.
if not exist "%~dp0venv" (
    echo ���ڴ������⻷��...
    python -m venv "%~dp0venv"
    echo ���ڰ�װ����...
    call "%~dp0venv\Scripts\activate.bat"
    pip install -r "%~dp0requirements.txt"
) else (
    call "%~dp0venv\Scripts\activate.bat"
)

:: ��������.
echo ������������...
python "%~dp0app.py"

pause