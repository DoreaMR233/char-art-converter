@echo off
setlocal enabledelayedexpansion

REM 字符画转换器前端Docker快速启动脚本 (Windows版)

REM 颜色定义
set GREEN=[92m
set YELLOW=[93m
set RED=[91m
set NC=[0m

REM 检查Docker是否安装
docker --version > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %RED%错误: Docker未安装。请先安装Docker: https://docs.docker.com/get-docker/%NC%
	pause
    exit /b 1
)

REM 显示欢迎信息
echo %GREEN%=== 字符画转换器前端Docker快速启动脚本 ===%NC%
echo %YELLOW%此脚本将构建并启动字符画转换器前端服务%NC%
echo.

REM 设置变量
set IMAGE_NAME=char-art-frontend
set CONTAINER_NAME=char-art-frontend
set HOST_PORT=8080
set CONTAINER_PORT=80
set BASE_PATH=
set API_URL=http://localhost:8080

REM 显示选择菜单
echo %GREEN%请选择启动方式:%NC%
echo %YELLOW%1. 使用Docker Run（单容器模式）%NC%
echo %YELLOW%2. 使用Docker Compose（多容器模式）%NC%
echo.

set /p CHOICE=请输入选择（1或2）: 

if "%CHOICE%"=="1" (
    goto :USE_DOCKER_RUN
) else if "%CHOICE%"=="2" (
    goto :USE_DOCKER_COMPOSE
) else (
    echo %RED%错误: 无效的选择，请输入1或2%NC%
	pause
    exit /b 1
)

:USE_DOCKER_RUN
REM 构建Docker镜像
echo %GREEN%[1/5] 构建Docker镜像...%NC%
docker build -t %IMAGE_NAME%:latest .

if %ERRORLEVEL% neq 0 (
    echo %RED%错误: 构建Docker镜像失败%NC%
	pause
    exit /b 1
)

echo %GREEN%[2/5] 检查并停止已存在的容器...%NC%
REM 检查容器是否已存在，如果存在则停止并删除
docker ps -a | findstr "%CONTAINER_NAME%" > nul
if %ERRORLEVEL% equ 0 (
    echo %YELLOW%发现已存在的%CONTAINER_NAME%容器，正在停止并删除...%NC%
    docker stop %CONTAINER_NAME% > nul 2>&1
    docker rm %CONTAINER_NAME% > nul 2>&1
)

REM 检查网络是否存在，如果不存在则创建
echo %GREEN%[3/5] 检查Docker网络...%NC%
docker network ls | findstr "char-art-network" > nul
if %ERRORLEVEL% neq 0 (
    echo %YELLOW%创建Docker网络: char-art-network%NC%
    docker network create char-art-network
    
    if %ERRORLEVEL% neq 0 (
        echo %RED%错误: 创建网络失败%NC%
        pause
        exit /b 1
    )
)

REM 设置环境变量
echo %GREEN%[4/5] 配置环境变量...%NC%
echo %YELLOW%请为每个环境变量输入值，或直接按回车使用默认值%NC%
echo.

REM 资源路径前缀配置
echo %YELLOW%资源路径前缀配置%NC%
echo 用于在非根路径部署时设置资源路径，例如部署在 http://example.com/char-art/
set /p BASE_PATH=资源路径前缀 (默认: 空): 
if "%BASE_PATH%"=="" set BASE_PATH=

REM 项目后端地址配置
echo %YELLOW%项目后端地址配置%NC%
echo 用于与后端服务进行通信
set /p API_URL=项目后端地址 (默认: http://localhost:8080): 
if "%API_URL%"=="" set API_URL=http://localhost:8080

REM 启动容器
echo %GREEN%[5/5] 启动字符画转换器前端容器...%NC%
docker run -d --name %CONTAINER_NAME% ^
    -p %HOST_PORT%:%CONTAINER_PORT% ^
    --network char-art-network ^
    -e BASE_PATH=%BASE_PATH% ^
	-e API_URL=%API_URL% ^
    %IMAGE_NAME%:latest

if %ERRORLEVEL% neq 0 (
    echo %RED%错误: 容器启动失败%NC%
	pause
    exit /b 1
)

goto :WAIT_FOR_SERVICES

:USE_DOCKER_COMPOSE
REM 检查Docker Compose是否安装
docker-compose --version > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %RED%错误: Docker Compose未安装。请先安装Docker Compose: https://docs.docker.com/compose/install/%NC%
	pause
    exit /b 1
)

echo %GREEN%[1/2] 使用Docker Compose启动服务...%NC%
docker-compose up -d

if %ERRORLEVEL% neq 0 (
    echo %RED%错误: 启动服务失败%NC%
	pause
    exit /b 1
)

:WAIT_FOR_SERVICES

REM 等待服务启动
echo %GREEN%[*] 等待服务启动...%NC%
timeout /t 5 /nobreak > nul

REM 检查服务健康状态
echo %YELLOW%检查服务健康状态...%NC%
set MAX_RETRIES=10
set RETRIES=0
set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%

:HEALTH_CHECK_LOOP
if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED

REM 使用PowerShell执行健康检查
powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1

if %ERRORLEVEL% equ 0 (
    echo %GREEN%服务已成功启动!%NC%
    echo %GREEN%前端地址: http://localhost:%HOST_PORT%%NC%
    echo %GREEN%应用路径: http://localhost:%HOST_PORT%%BASE_PATH%%NC%
    echo.
    echo %YELLOW%常用命令:%NC%
    echo   %GREEN%查看日志: docker logs %CONTAINER_NAME%%NC%
    echo   %GREEN%停止服务: docker stop %CONTAINER_NAME%%NC%
    echo   %GREEN%启动服务: docker start %CONTAINER_NAME%%NC%
    echo   %GREEN%删除容器: docker rm %CONTAINER_NAME%%NC%
    echo.
    echo %YELLOW%更多配置选项请参考Docker.md文档%NC%
	pause
    exit /b 0
)

set /a RETRIES+=1

echo %YELLOW%服务正在启动中，请稍候... (!RETRIES!/%MAX_RETRIES%)%NC%

timeout /t 2 /nobreak > nul
goto :HEALTH_CHECK_LOOP

:HEALTH_CHECK_FAILED
echo %RED%警告: 服务可能未正常启动，请检查日志:%NC%
echo %GREEN%docker logs %CONTAINER_NAME%%NC%
pause