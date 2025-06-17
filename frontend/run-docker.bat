@echo off
setlocal enabledelayedexpansion

REM ×Ö·û»­×ª»»Æ÷Ç°¶ËDocker¿ìËÙÆô¶¯½Å±¾ (Windows°æ)

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
echo %GREEN%=== ×Ö·û»­×ª»»Æ÷Ç°¶ËDocker¿ìËÙÆô¶¯½Å±¾ ===%NC%
echo %YELLOW%´Ë½Å±¾½«¹¹½¨²¢Æô¶¯×Ö·û»­×ª»»Æ÷Ç°¶Ë·þÎñ%NC%
echo.

REM ÉèÖÃ±äÁ¿
set IMAGE_NAME=char-art-frontend
set CONTAINER_NAME=char-art-frontend
set HOST_PORT=8080
set CONTAINER_PORT=80
set BASE_PATH=/char-art/

REM ¹¹½¨Docker¾µÏñ
echo %GREEN%[1/4] ¹¹½¨Docker¾µÏñ...%NC%
docker build -t %IMAGE_NAME%:latest .

if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: ¹¹½¨Docker¾µÏñÊ§°Ü%NC%
	pause
    exit /b 1
)

echo %GREEN%[2/4] ¼ì²é²¢Í£Ö¹ÒÑ´æÔÚµÄÈÝÆ÷...%NC%
REM ¼ì²éÈÝÆ÷ÊÇ·ñÒÑ´æÔÚ£¬Èç¹û´æÔÚÔòÍ£Ö¹²¢É¾³ý
docker ps -a | findstr "%CONTAINER_NAME%" > nul
if %ERRORLEVEL% equ 0 (
    echo %YELLOW%·¢ÏÖÒÑ´æÔÚµÄ%CONTAINER_NAME%ÈÝÆ÷£¬ÕýÔÚÍ£Ö¹²¢É¾³ý...%NC%
    docker stop %CONTAINER_NAME% > nul 2>&1
    docker rm %CONTAINER_NAME% > nul 2>&1
)

REM Æô¶¯ÈÝÆ÷
echo %GREEN%[3/4] Æô¶¯×Ö·û»­×ª»»Æ÷Ç°¶ËÈÝÆ÷...%NC%
docker run -d --name %CONTAINER_NAME% ^
    -p %HOST_PORT%:%CONTAINER_PORT% ^
    -e BASE_PATH=%BASE_PATH% ^
    --network char-art-network ^
    %IMAGE_NAME%:latest

if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: ÈÝÆ÷Æô¶¯Ê§°Ü%NC%
	pause
    exit /b 1
)

REM µÈ´ý·þÎñÆô¶¯
echo %GREEN%[4/4] µÈ´ý·þÎñÆô¶¯...%NC%
timeout /t 5 /nobreak > nul

REM ¼ì²é·þÎñ½¡¿µ×´Ì¬
echo %YELLOW%¼ì²é·þÎñ½¡¿µ×´Ì¬...%NC%
set MAX_RETRIES=10
set RETRIES=0
set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%

:HEALTH_CHECK_LOOP
if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED

REM Ê¹ÓÃPowerShellÖ´ÐÐ½¡¿µ¼ì²é
powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1

if %ERRORLEVEL% equ 0 (
    echo %GREEN%·þÎñÒÑ³É¹¦Æô¶¯!%NC%
    echo %GREEN%Ç°¶ËµØÖ·: http://localhost:%HOST_PORT%%NC%
    echo %GREEN%Ó¦ÓÃÂ·¾¶: http://localhost:%HOST_PORT%%BASE_PATH%%NC%
    echo.
    echo %YELLOW%³£ÓÃÃüÁî:%NC%
    echo   %GREEN%²é¿´ÈÕÖ¾: docker logs %CONTAINER_NAME%%NC%
    echo   %GREEN%Í£Ö¹·þÎñ: docker stop %CONTAINER_NAME%%NC%
    echo   %GREEN%Æô¶¯·þÎñ: docker start %CONTAINER_NAME%%NC%
    echo   %GREEN%É¾³ýÈÝÆ÷: docker rm %CONTAINER_NAME%%NC%
    echo.
    echo %YELLOW%¸ü¶àÅäÖÃÑ¡ÏîÇë²Î¿¼Docker.mdÎÄµµ%NC%
    exit /b 0
)

set /a RETRIES+=1

echo %YELLOW%·þÎñÕýÔÚÆô¶¯ÖÐ£¬ÇëÉÔºò... (!RETRIES!/%MAX_RETRIES%)%NC%

timeout /t 2 /nobreak > nul
goto :HEALTH_CHECK_LOOP

:HEALTH_CHECK_FAILED
echo %RED%¾¯¸æ: ·þÎñ¿ÉÄÜÎ´Õý³£Æô¶¯£¬Çë¼ì²éÈÕÖ¾:%NC%
echo %GREEN%docker logs %CONTAINER_NAME%%NC%
pause