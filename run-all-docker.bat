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
echo %YELLOW%1. ʹ��һ�廯Dockerfile���Ƽ���������ģʽ��%NC%
echo %YELLOW%2. ʹ��Docker Compose��������ģʽ��%NC%
echo.

set /p CHOICE=������ѡ��1��2��: 

if "%CHOICE%"=="1" (
    goto :USE_DOCKERFILE
) else if "%CHOICE%"=="2" (
    goto :USE_DOCKER_COMPOSE
) else (
    echo %RED%����: ��Ч��ѡ��������1��2%NC%
	pause
    exit /b 1
)

:USE_DOCKERFILE
echo %GREEN%[1/3] ʹ��һ�廯Dockerfile��������...%NC%

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
docker ps -a --format "{{.Names}}" | findstr /i "char-art-app" > nul
if %ERRORLEVEL% equ 0 (
    echo %YELLOW%�����Ѵ��ڣ�����ֹͣ���Ƴ�...%NC%
    docker stop char-art-app > nul 2>&1
    docker rm char-art-app > nul 2>&1
)

REM ��������
docker run -d --name char-art-app -p 80:80 char-art-converter:latest

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

echo %GREEN%[1/3] ʹ��Docker Compose��������...%NC%
docker-compose up -d

if %ERRORLEVEL% neq 0 (
    echo %RED%����: ��������ʧ��%NC%
	pause
    exit /b 1
)

:WAIT_FOR_SERVICES
REM �ȴ���������
echo %GREEN%[2/3] �ȴ���������...%NC%
timeout /t 5 /nobreak > nul

REM �����񽡿�״̬
echo %GREEN%[3/3] �����񽡿�״̬...%NC%

if "%CHOICE%"=="1" (
    REM һ�廯Dockerfileģʽ�µĽ������
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