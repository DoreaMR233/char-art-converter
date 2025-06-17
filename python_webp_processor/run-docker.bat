@echo off
setlocal enabledelayedexpansion

REM ×Ö·û»­×ª»»Æ÷ WebP´¦Àí·þÎñ Docker ¿ìËÙÆô¶¯½Å±¾

REM ÑÕÉ«¶¨Òå
set GREEN=[92m
set YELLOW=[93m
set RED=[91m
set NC=[0m

REM ¼ì²éDockerÊÇ·ñ°²×°
docker --version > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: DockerÎ´°²×°¡£ÇëÏÈ°²×°Docker: https://docs.docker.com/get-docker/%NC%
	pause
    exit /b 1
)

REM ÏÔÊ¾»¶Ó­ÐÅÏ¢
echo %GREEN%=== ×Ö·û»­×ª»»Æ÷ WebP´¦Àí·þÎñ Docker ¿ìËÙÆô¶¯½Å±¾ ===%NC%
echo %YELLOW%´Ë½Å±¾½«¹¹½¨²¢Æô¶¯WebP´¦Àí·þÎñDockerÈÝÆ÷%NC%
echo.

REM ÉèÖÃ±äÁ¿
set IMAGE_NAME=webp-processor
set CONTAINER_NAME=webp-processor
set HOST_PORT=8081
set CONTAINER_PORT=5000

REM ¹¹½¨Docker¾µÏñ
echo %GREEN%[1/4] ¹¹½¨Docker¾µÏñ...%NC%
docker build -t %IMAGE_NAME% .

if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: ¹¹½¨Docker¾µÏñÊ§°Ü%NC%
	pause
    exit /b 1
)

echo %GREEN%[2/4] ¼ì²é²¢Í£Ö¹ÒÑ´æÔÚµÄÈÝÆ÷...%NC%
REM ¼ì²éÈÝÆ÷ÊÇ·ñÒÑ´æÔÚ£¬Èç¹û´æÔÚÔòÍ£Ö¹²¢É¾³ý
docker ps -a | findstr %CONTAINER_NAME% > nul
if %ERRORLEVEL% equ 0 (
    echo %YELLOW%·¢ÏÖÒÑ´æÔÚµÄÈÝÆ÷£¬ÕýÔÚÍ£Ö¹²¢É¾³ý...%NC%
    docker stop %CONTAINER_NAME% > nul 2>&1
    docker rm %CONTAINER_NAME% > nul 2>&1
)

REM Æô¶¯ÈÝÆ÷

echo %GREEN%[3/4] Æô¶¯WebP´¦Àí·þÎñÈÝÆ÷...%NC%

docker run -d ^
    --name %CONTAINER_NAME% ^
    -p %HOST_PORT%:%CONTAINER_PORT% ^
    -v webp-processor-data:/app/data ^
    -v webp-processor-logs:/app/logs ^
    %IMAGE_NAME%

if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: Æô¶¯ÈÝÆ÷Ê§°Ü%NC%
	pause
    exit /b 1
)

REM µÈ´ý·þÎñÆô¶¯
echo %GREEN%[4/4] µÈ´ý·þÎñÆô¶¯...%NC%
timeout /t 3 /nobreak > nul

REM ¼ì²é·þÎñ½¡¿µ×´Ì¬
echo %YELLOW%¼ì²é·þÎñ½¡¿µ×´Ì¬...%NC%
set MAX_RETRIES=10
set RETRIES=0
set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%/api/health

:HEALTH_CHECK_LOOP
if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED

REM Ê¹ÓÃPowerShell½øÐÐHTTPÇëÇó
powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1

if %ERRORLEVEL% equ 0 (
    
    echo %GREEN%·þÎñÒÑ³É¹¦Æô¶¯!... %NC%
    echo %GREEN%WebP´¦Àí·þÎñµØÖ·: http://localhost:%HOST_PORT% %NC%
    echo.
    echo %YELLOW%³£ÓÃÃüÁî:%NC%
    echo   %GREEN%²é¿´ÈÕÖ¾: docker logs %CONTAINER_NAME% %NC%
    echo   %GREEN%Í£Ö¹·þÎñ: docker stop %CONTAINER_NAME% %NC%
    echo   %GREEN%Æô¶¯·þÎñ: docker start %CONTAINER_NAME% %NC%
    echo   %GREEN%É¾³ýÈÝÆ÷: docker rm %CONTAINER_NAME% %NC%
    echo.
    echo %YELLOW% ¸ü¶àÅäÖÃÑ¡ÏîÇë²Î¿¼ README.Docker.md ÎÄµµ %NC%
    pause
	exit /b 0
)

set /a RETRIES+=1

echo %YELLOW%·þÎñÕýÔÚÆô¶¯ÖÐ£¬ÇëÉÔºò... (!RETRIES!/%MAX_RETRIES%)%NC%

timeout /t 2 /nobreak > nul
goto :HEALTH_CHECK_LOOP

:HEALTH_CHECK_FAILED
echo %RED% ¾¯¸æ: ·þÎñ¿ÉÄÜÎ´Õý³£Æô¶¯£¬Çë¼ì²éÈÕÖ¾:%NC%
echo %GREEN% docker logs %CONTAINER_NAME% %NC%
pause