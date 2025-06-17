@echo off
setlocal enabledelayedexpansion

REM �ַ���ת���� WebP������� Docker ���������ű�

REM ��ɫ����
set GREEN=[92m
set YELLOW=[93m
set RED=[91m
set NC=[0m

REM ���Docker�Ƿ�װ
docker --version > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %RED%����: Dockerδ��װ�����Ȱ�װDocker: https://docs.docker.com/get-docker/%NC%
	pause
    exit /b 1
)

REM ��ʾ��ӭ��Ϣ
echo %GREEN%=== �ַ���ת���� WebP������� Docker ���������ű� ===%NC%
echo %YELLOW%�˽ű�������������WebP�������Docker����%NC%
echo.

REM ���ñ���
set IMAGE_NAME=webp-processor
set CONTAINER_NAME=webp-processor
set HOST_PORT=8081
set CONTAINER_PORT=5000

REM ��ʾѡ��˵�
echo %GREEN%��ѡ��������ʽ:%NC%
echo %YELLOW%1. ʹ��Docker Run��������ģʽ��%NC%
echo %YELLOW%2. ʹ��Docker Compose��������ģʽ��%NC%
echo.

set /p CHOICE=������ѡ��1��2��: 

if "%CHOICE%"=="1" (
    set USE_DOCKER_RUN=true
) else if "%CHOICE%"=="2" (
    set USE_DOCKER_RUN=false
) else (
    echo %RED%����: ��Ч��ѡ��������1��2%NC%
	pause
    exit /b 1
)

if "%USE_DOCKER_RUN%"=="true" (
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

    set /p PORT=��������˿� (Ĭ��: 5000): 
    if ""%PORT%""==""" set PORT=5000

    set /p LOG_LEVEL=��־���� (DEBUG, INFO, WARNING, ERROR, CRITICAL) (Ĭ��: INFO): 
    if ""%LOG_LEVEL%""==""" set LOG_LEVEL=INFO

    set /p DEBUG=����ģʽ (Ĭ��: False): 
    if ""%DEBUG%""==""" set DEBUG=False

    set /p MAX_CONTENT_LENGTH=����ϴ��ļ���С���ֽڣ� (Ĭ��: 16777216): 
    if ""%MAX_CONTENT_LENGTH%""==""" set MAX_CONTENT_LENGTH=16777216

    set /p TEMP_FILE_TTL=��ʱ�ļ�����ʱ�䣨�룩 (Ĭ��: 3600): 
    if ""%TEMP_FILE_TTL%""==""" set TEMP_FILE_TTL=3600

    REM ��������
    echo %GREEN%[5/5] ����WebP�����������...%NC%
    docker run -d ^
        --name %CONTAINER_NAME% ^
        -p %HOST_PORT%:%CONTAINER_PORT% ^
        -v webp-processor-data:/app/data ^
        -v webp-processor-logs:/app/logs ^
        --network char-art-network ^
        -e PORT=%PORT% ^
        -e LOG_LEVEL=%LOG_LEVEL% ^
        -e DEBUG=%DEBUG% ^
        -e MAX_CONTENT_LENGTH=%MAX_CONTENT_LENGTH% ^
        -e TEMP_FILE_TTL=%TEMP_FILE_TTL% ^
        %IMAGE_NAME%:latest

    if %ERRORLEVEL% neq 0 (
        echo %RED%����: ��������ʧ��%NC%
        pause
        exit /b 1
    )
) else (
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
)

REM �ȴ���������
echo %GREEN%[*] �ȴ���������...%NC%
timeout /t 3 /nobreak > nul

REM �����񽡿�״̬
echo %YELLOW%�����񽡿�״̬...%NC%
set MAX_RETRIES=10
set RETRIES=0

if "%USE_DOCKER_RUN%"=="true" (
    set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%/api/health
    
    :HEALTH_CHECK_LOOP_SINGLE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_SINGLE
    
    REM ʹ��PowerShellִ�н������
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%�����ѳɹ�����!%NC%
        echo %GREEN%WebP��������ַ: http://localhost:%HOST_PORT%%NC%
        echo %GREEN%�������: %HEALTH_CHECK_URL%%NC%
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
    goto :HEALTH_CHECK_LOOP_SINGLE
    
    :HEALTH_CHECK_FAILED_SINGLE
    echo %RED%����: �������δ����������������־:%NC%
    echo %GREEN%docker logs %CONTAINER_NAME%%NC%
    pause
    exit /b 1
) else (
    set HEALTH_CHECK_URL=http://localhost:8081/api/health
    
    :HEALTH_CHECK_LOOP_COMPOSE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_COMPOSE
    
    REM ʹ��PowerShellִ�н������
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%�����ѳɹ�����!%NC%
        echo %GREEN%WebP��������ַ: http://localhost:8081%NC%
        echo %GREEN%�������: %HEALTH_CHECK_URL%%NC%
        echo.
        echo %YELLOW%��������:%NC%
        echo   %GREEN%�鿴��־: docker-compose logs%NC%
        echo   %GREEN%ֹͣ����: docker-compose down%NC%
        echo   %GREEN%��������: docker-compose up -d%NC%
        echo.
        echo %YELLOW%��������ѡ����ο�Docker.md�ĵ�%NC%
        pause
        exit /b 0
    )
    
    set /a RETRIES+=1
    
    echo %YELLOW%�������������У����Ժ�... (!RETRIES!/%MAX_RETRIES%)%NC%
    
    timeout /t 2 /nobreak > nul
    goto :HEALTH_CHECK_LOOP_COMPOSE
    
    :HEALTH_CHECK_FAILED_COMPOSE
    echo %RED%����: �������δ����������������־:%NC%
    echo %GREEN%docker-compose logs%NC%
    pause
    exit /b 1
)