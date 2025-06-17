@echo off
setlocal enabledelayedexpansion

REM �ַ���ת���� ����ϵͳ Docker ���������ű�

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
echo %GREEN%=== �ַ���ת���� ����ϵͳ Docker ���������ű� ===%NC%
echo %YELLOW%�˽ű��������ַ���ת���������з��񣬰�����˷����WebP�������%NC%
echo.

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
    echo %GREEN%[1/5] ʹ��Docker Run��������...%NC%

    REM ��龵���Ƿ����
    docker images char-art-converter:latest --format "{{.Repository}}" | findstr /i "char-art-converter" > nul
    if %ERRORLEVEL% neq 0 (
        echo %YELLOW%���񲻴��ڣ����ڹ���...%NC%
        docker build -t char-art-converter:latest .
        
        if %ERRORLEVEL% neq 0 (
            echo %RED%����: ��������ʧ��%NC%
		    pause
            exit /b 1
        )
    )

    REM ��������Ƿ��Ѵ���
    echo %GREEN%[2/5] ��鲢ֹͣ�Ѵ��ڵ�����...%NC%
    docker ps -a --format "{{.Names}}" | findstr /i "char-art-app" > nul
    if %ERRORLEVEL% equ 0 (
        echo %YELLOW%�����Ѵ��ڣ�����ֹͣ���Ƴ�...%NC%
        docker stop char-art-app > nul 2>&1
        docker rm char-art-app > nul 2>&1
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

    REM ͨ������
    set /p LOG_LEVEL=��־���� (Ĭ��: INFO): 
    if ""%LOG_LEVEL%""==""" set LOG_LEVEL=INFO

    REM ��˷�������
    set /p MAX_FILE_SIZE=����ļ���С (Ĭ��: 10MB): 
    if ""%MAX_FILE_SIZE%""==""" set MAX_FILE_SIZE=10MB

    set /p MAX_REQUEST_SIZE=��������С (Ĭ��: 10MB): 
    if ""%MAX_REQUEST_SIZE%""==""" set MAX_REQUEST_SIZE=10MB

    REM �����־����
    set /p LOG_FILE_MAX_SIZE=��־�ļ�����С (Ĭ��: 10MB): 
    if ""%LOG_FILE_MAX_SIZE%""==""" set LOG_FILE_MAX_SIZE=10MB

    set /p LOG_FILE_MAX_HISTORY=��־�ļ�������ʷ���� (Ĭ��: 30): 
    if ""%LOG_FILE_MAX_HISTORY%""==""" set LOG_FILE_MAX_HISTORY=30

    REM Redis����
    set /p REDIS_DATABASE=Redis���ݿ����� (Ĭ��: 0): 
    if ""%REDIS_DATABASE%""==""" set REDIS_DATABASE=0

    set /p REDIS_TIMEOUT=Redis��ʱʱ�䣬��λ���� (Ĭ��: 60000): 
    if ""%REDIS_TIMEOUT%""==""" set REDIS_TIMEOUT=60000

    REM �ַ�����������
    set /p CHAR_ART_CACHE_TTL=�������ʱ�䣬��λ�� (Ĭ��: 3600): 
    if ""%CHAR_ART_CACHE_TTL%""==""" set CHAR_ART_CACHE_TTL=3600

    set /p CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=�����ǰ׺ (Ĭ��: char-art:text:): 
    if ""%CHAR_ART_CACHE_DEFAULT_KEY_PREFIX%""==""" set CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=char-art:text:

    REM WebP����������
    set /p WEBP_PROCESSOR_CONNECTION_TIMEOUT=WebP���������ӳ�ʱʱ�䣬��λ���� (Ĭ��: 5000): 
    if ""%WEBP_PROCESSOR_CONNECTION_TIMEOUT%""==""" set WEBP_PROCESSOR_CONNECTION_TIMEOUT=5000

    set /p WEBP_PROCESSOR_MAX_RETRIES=WebP������������Դ��� (Ĭ��: 2): 
    if ""%WEBP_PROCESSOR_MAX_RETRIES%""==""" set WEBP_PROCESSOR_MAX_RETRIES=2

    REM Python WebP����������
    set /p DEBUG=�Ƿ�������ģʽ (Ĭ��: False): 
    if ""%DEBUG%""==""" set DEBUG=False

    set /p MAX_CONTENT_LENGTH=������ݳ��ȣ���λ�ֽ� (Ĭ��: 16777216): 
    if ""%MAX_CONTENT_LENGTH%""==""" set MAX_CONTENT_LENGTH=16777216

    set /p TEMP_FILE_TTL=��ʱ�ļ����ʱ�䣬��λ�� (Ĭ��: 3600): 
    if ""%TEMP_FILE_TTL%""==""" set TEMP_FILE_TTL=3600

    REM �ַ���Ĭ������
    set /p DEFAULT_DENSITY=Ĭ���ַ��ܶ� (Ĭ��: medium): 
    if ""%DEFAULT_DENSITY%""==""" set DEFAULT_DENSITY=medium

    set /p DEFAULT_COLOR_MODE=Ĭ����ɫģʽ (Ĭ��: grayscale): 
    if ""%DEFAULT_COLOR_MODE%""==""" set DEFAULT_COLOR_MODE=grayscale

    REM ǰ������
    set /p BASE_PATH=ǰ����Դ·��ǰ׺ (Ĭ��Ϊ��): 
    if ""%BASE_PATH%""==""" set BASE_PATH=

    REM ��������
    echo %GREEN%[5/5] �����ַ���ת��������...%NC%
    docker run -d --name char-art-app ^  
        -p 80:80 ^  
        -v char-art-data:/app/backend/data ^  
        -v char-art-logs:/app/backend/logs ^  
        -v redis-data:/app/redis/data ^  
        -v webp-processor-data:/app/webp-processor/data ^  
        -v webp-processor-logs:/app/webp-processor/logs ^  
        --network char-art-network ^  
        -e LOG_LEVEL=%LOG_LEVEL% ^  
        -e MAX_FILE_SIZE=%MAX_FILE_SIZE% ^  
        -e MAX_REQUEST_SIZE=%MAX_REQUEST_SIZE% ^  
        -e LOG_FILE_MAX_SIZE=%LOG_FILE_MAX_SIZE% ^  
        -e LOG_FILE_MAX_HISTORY=%LOG_FILE_MAX_HISTORY% ^  
        -e REDIS_DATABASE=%REDIS_DATABASE% ^  
        -e REDIS_TIMEOUT=%REDIS_TIMEOUT% ^  
        -e CHAR_ART_CACHE_TTL=%CHAR_ART_CACHE_TTL% ^  
        -e CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=%CHAR_ART_CACHE_DEFAULT_KEY_PREFIX% ^  
        -e WEBP_PROCESSOR_CONNECTION_TIMEOUT=%WEBP_PROCESSOR_CONNECTION_TIMEOUT% ^  
        -e WEBP_PROCESSOR_MAX_RETRIES=%WEBP_PROCESSOR_MAX_RETRIES% ^  
        -e DEBUG=%DEBUG% ^  
        -e MAX_CONTENT_LENGTH=%MAX_CONTENT_LENGTH% ^  
        -e TEMP_FILE_TTL=%TEMP_FILE_TTL% ^  
        -e DEFAULT_DENSITY=%DEFAULT_DENSITY% ^  
        -e DEFAULT_COLOR_MODE=%DEFAULT_COLOR_MODE% ^  
        -e BASE_PATH=%BASE_PATH% ^  
        char-art-converter:latest

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

    echo %GREEN%[1/3] ʹ��Docker Compose��������...%NC%
    docker-compose up -d

    if %ERRORLEVEL% neq 0 (
        echo %RED%����: ��������ʧ��%NC%
	    pause
        exit /b 1
    )
)

REM �ȴ���������
echo %GREEN%[2/3] �ȴ���������...%NC%
timeout /t 5 /nobreak > nul

REM �����񽡿�״̬
echo %GREEN%[3/3] �����񽡿�״̬...%NC%

if "%USE_DOCKER_RUN%"=="true" (
    REM Docker Runģʽ�µĽ������
    set BACKEND_URL=http://localhost/api/health
    
    echo %YELLOW%�����񽡿�״̬...%NC%
    set MAX_RETRIES=10
    set RETRIES=0
    
    :HEALTH_CHECK_LOOP
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED
    
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%BACKEND_URL%' -UseBasicParsing; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%�����ѳɹ�����!%NC%
        goto :SINGLE_CONTAINER_STARTED
    )
    
    set /a RETRIES+=1
    echo %YELLOW%�������������У����Ժ�... (!RETRIES!/%MAX_RETRIES%)%NC%
    timeout /t 2 /nobreak > nul
    goto :HEALTH_CHECK_LOOP
    
    :HEALTH_CHECK_FAILED
    echo %RED%����: �������δ����������������־:%NC%
    echo %GREEN%docker logs char-art-app%NC%
    
    :SINGLE_CONTAINER_STARTED
    echo.
    echo %GREEN%�����ַ:%NC%
    echo %GREEN%Ӧ��ǰ��: http://localhost%NC%
    echo.
    echo %YELLOW%��������:%NC%
    echo   �鿴����״̬: %GREEN%docker ps%NC%
    echo   �鿴Ӧ����־: %GREEN%docker logs char-art-app%NC%
    echo   ֹͣӦ��: %GREEN%docker stop char-art-app%NC%
    echo   ����Ӧ��: %GREEN%docker restart char-art-app%NC%
    echo.
    echo %YELLOW%��������ѡ����ο�Docker.md�ĵ�%NC%
) else (
    REM Docker Composeģʽ�µĽ������
    REM ����˷���
    echo %YELLOW%����˷���...%NC%
    set MAX_RETRIES=10
    set RETRIES=0
    set BACKEND_URL=http://localhost:8080/api/health
    
    :BACKEND_CHECK_LOOP
    if %RETRIES% geq %MAX_RETRIES% goto :BACKEND_CHECK_FAILED
    
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%BACKEND_URL%' -UseBasicParsing; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%��˷����ѳɹ�����!%NC%
        goto :CHECK_WEBP_PROCESSOR
    )
    
    set /a RETRIES+=1
    echo %YELLOW%��˷������������У����Ժ�... (!RETRIES!/%MAX_RETRIES%)%NC%
    timeout /t 2 /nobreak > nul
    goto :BACKEND_CHECK_LOOP
    
    :BACKEND_CHECK_FAILED
    echo %RED%����: ��˷������δ����������������־:%NC%
    echo %GREEN%docker logs char-art-backend%NC%
    goto :CHECK_WEBP_PROCESSOR
    
    :CHECK_WEBP_PROCESSOR
    REM ���WebP�������
    echo %YELLOW%���WebP�������...%NC%
    set MAX_RETRIES=10
    set RETRIES=0
    set WEBP_URL=http://localhost:8081/api/health
    
    :WEBP_CHECK_LOOP
    if %RETRIES% geq %MAX_RETRIES% goto :WEBP_CHECK_FAILED
    
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%WEBP_URL%' -UseBasicParsing; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%WebP��������ѳɹ�����!%NC%
        goto :ALL_SERVICES_STARTED
    )
    
    set /a RETRIES+=1
    echo %YELLOW%WebP����������������У����Ժ�... (!RETRIES!/%MAX_RETRIES%)%NC%
    timeout /t 2 /nobreak > nul
    goto :WEBP_CHECK_LOOP
    
    :WEBP_CHECK_FAILED
    echo %RED%����: WebP����������δ����������������־:%NC%
    echo %GREEN%docker logs webp-processor%NC%
    
    :ALL_SERVICES_STARTED
    echo.
    echo %GREEN%�����ַ:%NC%
    echo %GREEN%��˷���: http://localhost:8080%NC%
    echo %GREEN%WebP�������: http://localhost:8081%NC%
    echo.
    echo %YELLOW%��������:%NC%
    echo   �鿴��������: %GREEN%docker ps%NC%
    echo   �鿴�����־: %GREEN%docker logs char-art-backend%NC%
    echo   �鿴WebP���������־: %GREEN%docker logs webp-processor%NC%
    echo   ֹͣ���з���: %GREEN%docker-compose down%NC%
    echo   �������з���: %GREEN%docker-compose restart%NC%
    echo.
    echo %YELLOW%��������ѡ����ο�Docker.md�ĵ�%NC%
)