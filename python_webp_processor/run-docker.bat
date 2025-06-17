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

REM ����Docker����
echo %GREEN%[1/4] ����Docker����...%NC%
docker build -t %IMAGE_NAME% .

if %ERRORLEVEL% neq 0 (
    echo %RED%����: ����Docker����ʧ��%NC%
	pause
    exit /b 1
)

echo %GREEN%[2/4] ��鲢ֹͣ�Ѵ��ڵ�����...%NC%
REM ��������Ƿ��Ѵ��ڣ����������ֹͣ��ɾ��
docker ps -a | findstr %CONTAINER_NAME% > nul
if %ERRORLEVEL% equ 0 (
    echo %YELLOW%�����Ѵ��ڵ�����������ֹͣ��ɾ��...%NC%
    docker stop %CONTAINER_NAME% > nul 2>&1
    docker rm %CONTAINER_NAME% > nul 2>&1
)

REM ��������

echo %GREEN%[3/4] ����WebP�����������...%NC%

docker run -d ^
    --name %CONTAINER_NAME% ^
    -p %HOST_PORT%:%CONTAINER_PORT% ^
    -v webp-processor-data:/app/data ^
    -v webp-processor-logs:/app/logs ^
    %IMAGE_NAME%

if %ERRORLEVEL% neq 0 (
    echo %RED%����: ��������ʧ��%NC%
	pause
    exit /b 1
)

REM �ȴ���������
echo %GREEN%[4/4] �ȴ���������...%NC%
timeout /t 3 /nobreak > nul

REM �����񽡿�״̬
echo %YELLOW%�����񽡿�״̬...%NC%
set MAX_RETRIES=10
set RETRIES=0
set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%/api/health

:HEALTH_CHECK_LOOP
if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED

REM ʹ��PowerShell����HTTP����
powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1

if %ERRORLEVEL% equ 0 (
    
    echo %GREEN%�����ѳɹ�����!... %NC%
    echo %GREEN%WebP��������ַ: http://localhost:%HOST_PORT% %NC%
    echo.
    echo %YELLOW%��������:%NC%
    echo   %GREEN%�鿴��־: docker logs %CONTAINER_NAME% %NC%
    echo   %GREEN%ֹͣ����: docker stop %CONTAINER_NAME% %NC%
    echo   %GREEN%��������: docker start %CONTAINER_NAME% %NC%
    echo   %GREEN%ɾ������: docker rm %CONTAINER_NAME% %NC%
    echo.
    echo %YELLOW% ��������ѡ����ο� README.Docker.md �ĵ� %NC%
    pause
	exit /b 0
)

set /a RETRIES+=1

echo %YELLOW%�������������У����Ժ�... (!RETRIES!/%MAX_RETRIES%)%NC%

timeout /t 2 /nobreak > nul
goto :HEALTH_CHECK_LOOP

:HEALTH_CHECK_FAILED
echo %RED% ����: �������δ����������������־:%NC%
echo %GREEN% docker logs %CONTAINER_NAME% %NC%
pause