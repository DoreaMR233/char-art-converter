@echo off
setlocal enabledelayedexpansion

REM �ַ���ת����ǰ��Docker���������ű� (Windows��)

REM ��ɫ����
set GREEN=[92m
set YELLOW=[93m
set RED=[91m
set NC=[0m

REM ���Docker�Ƿ�װ
docker --version > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %RED%����: Dockerδ��װ�����Ȱ�װDocker: https://docs.docker.com/get-docker/%NC%
	pause
    exit /b 1
)

REM ��ʾ��ӭ��Ϣ
echo %GREEN%=== �ַ���ת����ǰ��Docker���������ű� ===%NC%
echo %YELLOW%�˽ű��������������ַ���ת����ǰ�˷���%NC%
echo.

REM ���ñ���
set IMAGE_NAME=char-art-frontend
set CONTAINER_NAME=char-art-frontend
set HOST_PORT=8080
set CONTAINER_PORT=80
set BASE_PATH=
set API_URL=http://localhost:8080

REM ��ʾѡ��˵�
echo %GREEN%��ѡ��������ʽ:%NC%
echo %YELLOW%1. ʹ��Docker Run��������ģʽ��%NC%
echo %YELLOW%2. ʹ��Docker Compose��������ģʽ��%NC%
echo.

set /p CHOICE=������ѡ��1��2��: 

if "%CHOICE%"=="1" (
    goto :USE_DOCKER_RUN
) else if "%CHOICE%"=="2" (
    goto :USE_DOCKER_COMPOSE
) else (
    echo %RED%����: ��Ч��ѡ��������1��2%NC%
	pause
    exit /b 1
)

:USE_DOCKER_RUN
REM ����Docker����
echo %GREEN%[1/5] ����Docker����...%NC%
docker build -t %IMAGE_NAME%:latest .

if %ERRORLEVEL% neq 0 (
    echo %RED%����: ����Docker����ʧ��%NC%
	pause
    exit /b 1
)

echo %GREEN%[2/5] ��鲢ֹͣ�Ѵ��ڵ�����...%NC%
REM ��������Ƿ��Ѵ��ڣ����������ֹͣ��ɾ��
docker ps -a | findstr "%CONTAINER_NAME%" > nul
if %ERRORLEVEL% equ 0 (
    echo %YELLOW%�����Ѵ��ڵ�%CONTAINER_NAME%����������ֹͣ��ɾ��...%NC%
    docker stop %CONTAINER_NAME% > nul 2>&1
    docker rm %CONTAINER_NAME% > nul 2>&1
)

REM ��������Ƿ���ڣ�����������򴴽�
echo %GREEN%[3/5] ���Docker����...%NC%
docker network ls | findstr "char-art-network" > nul
if %ERRORLEVEL% neq 0 (
    echo %YELLOW%����Docker����: char-art-network%NC%
    docker network create char-art-network
    
    if %ERRORLEVEL% neq 0 (
        echo %RED%����: ��������ʧ��%NC%
        pause
        exit /b 1
    )
)

REM ���û�������
echo %GREEN%[4/5] ���û�������...%NC%
echo %YELLOW%��Ϊÿ��������������ֵ����ֱ�Ӱ��س�ʹ��Ĭ��ֵ%NC%
echo.

REM ��Դ·��ǰ׺����
echo %YELLOW%��Դ·��ǰ׺����%NC%
echo �����ڷǸ�·������ʱ������Դ·�������粿���� http://example.com/char-art/
set /p BASE_PATH=��Դ·��ǰ׺ (Ĭ��: ��): 
if "%BASE_PATH%"=="" set BASE_PATH=

REM ��Ŀ��˵�ַ����
echo %YELLOW%��Ŀ��˵�ַ����%NC%
echo �������˷������ͨ��
set /p API_URL=��Ŀ��˵�ַ (Ĭ��: http://localhost:8080): 
if "%API_URL%"=="" set API_URL=http://localhost:8080

REM ��������
echo %GREEN%[5/5] �����ַ���ת����ǰ������...%NC%
docker run -d --name %CONTAINER_NAME% ^
    -p %HOST_PORT%:%CONTAINER_PORT% ^
    --network char-art-network ^
    -e BASE_PATH=%BASE_PATH% ^
	-e API_URL=%API_URL% ^
    %IMAGE_NAME%:latest

if %ERRORLEVEL% neq 0 (
    echo %RED%����: ��������ʧ��%NC%
	pause
    exit /b 1
)

goto :WAIT_FOR_SERVICES

:USE_DOCKER_COMPOSE
REM ���Docker Compose�Ƿ�װ
docker-compose --version > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %RED%����: Docker Composeδ��װ�����Ȱ�װDocker Compose: https://docs.docker.com/compose/install/%NC%
	pause
    exit /b 1
)

echo %GREEN%[1/2] ʹ��Docker Compose��������...%NC%
docker-compose up -d

if %ERRORLEVEL% neq 0 (
    echo %RED%����: ��������ʧ��%NC%
	pause
    exit /b 1
)

:WAIT_FOR_SERVICES

REM �ȴ���������
echo %GREEN%[*] �ȴ���������...%NC%
timeout /t 5 /nobreak > nul

REM �����񽡿�״̬
echo %YELLOW%�����񽡿�״̬...%NC%
set MAX_RETRIES=10
set RETRIES=0
set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%

:HEALTH_CHECK_LOOP
if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED

REM ʹ��PowerShellִ�н������
powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1

if %ERRORLEVEL% equ 0 (
    echo %GREEN%�����ѳɹ�����!%NC%
    echo %GREEN%ǰ�˵�ַ: http://localhost:%HOST_PORT%%NC%
    echo %GREEN%Ӧ��·��: http://localhost:%HOST_PORT%%BASE_PATH%%NC%
    echo.
    echo %YELLOW%��������:%NC%
    echo   %GREEN%�鿴��־: docker logs %CONTAINER_NAME%%NC%
    echo   %GREEN%ֹͣ����: docker stop %CONTAINER_NAME%%NC%
    echo   %GREEN%��������: docker start %CONTAINER_NAME%%NC%
    echo   %GREEN%ɾ������: docker rm %CONTAINER_NAME%%NC%
    echo.
    echo %YELLOW%��������ѡ����ο�Docker.md�ĵ�%NC%
	pause
    exit /b 0
)

set /a RETRIES+=1

echo %YELLOW%�������������У����Ժ�... (!RETRIES!/%MAX_RETRIES%)%NC%

timeout /t 2 /nobreak > nul
goto :HEALTH_CHECK_LOOP

:HEALTH_CHECK_FAILED
echo %RED%����: �������δ����������������־:%NC%
echo %GREEN%docker logs %CONTAINER_NAME%%NC%
pause