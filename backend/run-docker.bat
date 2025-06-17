@echo off
setlocal enabledelayedexpansion

REM �ַ���ת�������Docker���������ű� (Windows��)

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
echo %GREEN%=== �ַ���ת�������Docker���������ű� ===%NC%
echo %YELLOW%�˽ű��������������ַ���ת������˷���%NC%
echo.

REM ���ñ���
set IMAGE_NAME=char-art-backend
set CONTAINER_NAME=char-art-backend
set HOST_PORT=8080
set CONTAINER_PORT=8080

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

    REM Redis����
    set /p REDIS_HOST=Redis��������ַ (Ĭ��: localhost): 
    if ""%REDIS_HOST%""==""" set REDIS_HOST=localhost

    set /p REDIS_PORT=Redis�������˿� (Ĭ��: 6379): 
    if ""%REDIS_PORT%""==""" set REDIS_PORT=6379

    set /p REDIS_DATABASE=Redis���ݿ����� (Ĭ��: 0): 
    if ""%REDIS_DATABASE%""==""" set REDIS_DATABASE=0

    set /p REDIS_TIMEOUT=Redis���ӳ�ʱʱ�� (Ĭ��: 60000): 
    if ""%REDIS_TIMEOUT%""==""" set REDIS_TIMEOUT=60000

    REM �ַ�����������
    set /p CHAR_ART_CACHE_TTL=�������ʱ�䣬��λ�� (Ĭ��: 3600): 
    if ""%CHAR_ART_CACHE_TTL%""==""" set CHAR_ART_CACHE_TTL=3600

    set /p CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=�����ǰ׺ (Ĭ��: char-art:text:): 
    if ""%CHAR_ART_CACHE_DEFAULT_KEY_PREFIX%""==""" set CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=char-art:text:

    REM WebP�����������
    set /p WEBP_PROCESSOR_URL=WebP�������URL (Ĭ��: http://localhost:8081): 
    if ""%WEBP_PROCESSOR_URL%""==""" set WEBP_PROCESSOR_URL=http://localhost:8081

    set /p WEBP_PROCESSOR_ENABLED=�Ƿ�����WebP������� (Ĭ��: true): 
    if ""%WEBP_PROCESSOR_ENABLED%""==""" set WEBP_PROCESSOR_ENABLED=true

    set /p WEBP_PROCESSOR_CONNECTION_TIMEOUT=���ӳ�ʱʱ�� (Ĭ��: 600000): 
    if ""%WEBP_PROCESSOR_CONNECTION_TIMEOUT%""==""" set WEBP_PROCESSOR_CONNECTION_TIMEOUT=600000

    set /p WEBP_PROCESSOR_MAX_RETRIES=������Դ��� (Ĭ��: 2): 
    if ""%WEBP_PROCESSOR_MAX_RETRIES%""==""" set WEBP_PROCESSOR_MAX_RETRIES=2

    REM ����������
    set /p SERVER_PORT=�������˿� (Ĭ��: 8080): 
    if ""%SERVER_PORT%""==""" set SERVER_PORT=8080
    set HOST_PORT=%SERVER_PORT%

    REM �ϴ��ļ�����
    set /p MAX_FILE_SIZE=����ļ���С (Ĭ��: 10MB): 
    if ""%MAX_FILE_SIZE%""==""" set MAX_FILE_SIZE=10MB

    set /p MAX_REQUEST_SIZE=��������С (Ĭ��: 10MB): 
    if ""%MAX_REQUEST_SIZE%""==""" set MAX_REQUEST_SIZE=10MB

    REM ��־����
    set /p LOG_LEVEL=��־���� (Ĭ��: INFO): 
    if ""%LOG_LEVEL%""==""" set LOG_LEVEL=INFO

    set /p LOG_FILE_MAX_SIZE=��־�ļ�����С (Ĭ��: 10MB): 
    if ""%LOG_FILE_MAX_SIZE%""==""" set LOG_FILE_MAX_SIZE=10MB

    set /p LOG_FILE_MAX_HISTORY=��־�ļ�������ʷ���� (Ĭ��: 30): 
    if ""%LOG_FILE_MAX_HISTORY%""==""" set LOG_FILE_MAX_HISTORY=30

    REM �ַ���Ĭ������
    set /p DEFAULT_DENSITY=Ĭ���ַ��ܶ� (Ĭ��: medium): 
    if ""%DEFAULT_DENSITY%""==""" set DEFAULT_DENSITY=medium

    set /p DEFAULT_COLOR_MODE=Ĭ����ɫģʽ (Ĭ��: grayscale): 
    if ""%DEFAULT_COLOR_MODE%""==""" set DEFAULT_COLOR_MODE=grayscale

    REM ��������
    echo %GREEN%[5/5] �����ַ���ת�����������...%NC%
    docker run -d --name %CONTAINER_NAME% ^
        -p %HOST_PORT%:%CONTAINER_PORT% ^
        -v char-art-data:/app/data ^
        -v char-art-logs:/app/logs ^
        --network char-art-network ^
        -e REDIS_HOST=%REDIS_HOST% ^
        -e REDIS_PORT=%REDIS_PORT% ^
        -e REDIS_DATABASE=%REDIS_DATABASE% ^
        -e REDIS_TIMEOUT=%REDIS_TIMEOUT% ^
        -e CHAR_ART_CACHE_TTL=%CHAR_ART_CACHE_TTL% ^
        -e CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=%CHAR_ART_CACHE_DEFAULT_KEY_PREFIX% ^
        -e WEBP_PROCESSOR_URL=%WEBP_PROCESSOR_URL% ^
        -e WEBP_PROCESSOR_ENABLED=%WEBP_PROCESSOR_ENABLED% ^
        -e WEBP_PROCESSOR_CONNECTION_TIMEOUT=%WEBP_PROCESSOR_CONNECTION_TIMEOUT% ^
        -e WEBP_PROCESSOR_MAX_RETRIES=%WEBP_PROCESSOR_MAX_RETRIES% ^
        -e SERVER_PORT=%SERVER_PORT% ^
        -e MAX_FILE_SIZE=%MAX_FILE_SIZE% ^
        -e MAX_REQUEST_SIZE=%MAX_REQUEST_SIZE% ^
        -e LOG_LEVEL=%LOG_LEVEL% ^
        -e LOG_FILE_MAX_SIZE=%LOG_FILE_MAX_SIZE% ^
        -e LOG_FILE_MAX_HISTORY=%LOG_FILE_MAX_HISTORY% ^
        -e DEFAULT_DENSITY=%DEFAULT_DENSITY% ^
        -e DEFAULT_COLOR_MODE=%DEFAULT_COLOR_MODE% ^
        %IMAGE_NAME%:latest
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
echo %GREEN%[4/4] �ȴ���������...%NC%
timeout /t 5 /nobreak > nul

REM �����񽡿�״̬
echo %YELLOW%�����񽡿�״̬...%NC%
set MAX_RETRIES=10
set RETRIES=0

if "%USE_DOCKER_RUN%"=="true" (
    set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%/api/health
    
    :HEALTH_CHECK_LOOP_SINGLE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_SINGLE
    
    REM ʹ��PowerShellִ�н������
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%�����ѳɹ�����!%NC%
        echo %GREEN%API��ַ: http://localhost:%HOST_PORT%/api%NC%
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
    set HEALTH_CHECK_URL=http://localhost:8080/api/health
    
    :HEALTH_CHECK_LOOP_COMPOSE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_COMPOSE
    
    REM ʹ��PowerShellִ�н������
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%�����ѳɹ�����!%NC%
        echo %GREEN%API��ַ: http://localhost:8080/api%NC%
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