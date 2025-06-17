@echo off
setlocal enabledelayedexpansion

REM ×Ö·û»­×ª»»Æ÷ ÍêÕûÏµÍ³ Docker ¿ìËÙÆô¶¯½Å±¾

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
echo %GREEN%=== ×Ö·û»­×ª»»Æ÷ ÍêÕûÏµÍ³ Docker ¿ìËÙÆô¶¯½Å±¾ ===%NC%
echo %YELLOW%´Ë½Å±¾½«Æô¶¯×Ö·û»­×ª»»Æ÷µÄËùÓÐ·þÎñ£¬°üÀ¨ºó¶Ë·þÎñºÍWebP´¦Àí·þÎñ%NC%
echo.

REM ÏÔÊ¾Ñ¡Ôñ²Ëµ¥
echo %GREEN%ÇëÑ¡ÔñÆô¶¯·½Ê½:%NC%
echo %YELLOW%1. Ê¹ÓÃÒ»Ìå»¯Dockerfile£¨ÍÆ¼ö£¬µ¥ÈÝÆ÷Ä£Ê½£©%NC%
echo %YELLOW%2. Ê¹ÓÃDocker Compose£¨¶àÈÝÆ÷Ä£Ê½£©%NC%
echo.

set /p CHOICE=ÇëÊäÈëÑ¡Ôñ£¨1»ò2£©: 

if "%CHOICE%"=="1" (
    goto :USE_DOCKERFILE
) else if "%CHOICE%"=="2" (
    goto :USE_DOCKER_COMPOSE
) else (
    echo %RED%´íÎó: ÎÞÐ§µÄÑ¡Ôñ£¬ÇëÊäÈë1»ò2%NC%
	pause
    exit /b 1
)

:USE_DOCKERFILE
echo %GREEN%[1/3] Ê¹ÓÃÒ»Ìå»¯DockerfileÆô¶¯·þÎñ...%NC%

REM ¼ì²é¾µÏñÊÇ·ñ´æÔÚ
docker images char-art-converter:latest --format "{{.Repository}}" | findstr /i "char-art-converter" > nul
if %ERRORLEVEL% neq 0 (
    echo %YELLOW%¾µÏñ²»´æÔÚ£¬ÕýÔÚ¹¹½¨...%NC%
    docker build -t char-art-converter:latest .
    
    if %ERRORLEVEL% neq 0 (
        echo %RED%´íÎó: ¹¹½¨¾µÏñÊ§°Ü%NC%
		pause
        exit /b 1
    )
)

REM ¼ì²éÈÝÆ÷ÊÇ·ñÒÑ´æÔÚ
docker ps -a --format "{{.Names}}" | findstr /i "char-art-app" > nul
if %ERRORLEVEL% equ 0 (
    echo %YELLOW%ÈÝÆ÷ÒÑ´æÔÚ£¬ÕýÔÚÍ£Ö¹²¢ÒÆ³ý...%NC%
    docker stop char-art-app > nul 2>&1
    docker rm char-art-app > nul 2>&1
)

REM Æô¶¯ÈÝÆ÷
docker run -d --name char-art-app -p 80:80 char-art-converter:latest

if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: Æô¶¯ÈÝÆ÷Ê§°Ü%NC%
	pause
    exit /b 1
)

goto :WAIT_FOR_SERVICES

:USE_DOCKER_COMPOSE
REM ¼ì²éDocker ComposeÊÇ·ñ°²×°
docker-compose --version > nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: Docker ComposeÎ´°²×°¡£ÇëÏÈ°²×°Docker Compose: https://docs.docker.com/compose/install/%NC%
	pause
    exit /b 1
)

echo %GREEN%[1/3] Ê¹ÓÃDocker ComposeÆô¶¯·þÎñ...%NC%
docker-compose up -d

if %ERRORLEVEL% neq 0 (
    echo %RED%´íÎó: Æô¶¯·þÎñÊ§°Ü%NC%
	pause
    exit /b 1
)

:WAIT_FOR_SERVICES
REM µÈ´ý·þÎñÆô¶¯
echo %GREEN%[2/3] µÈ´ý·þÎñÆô¶¯...%NC%
timeout /t 5 /nobreak > nul

REM ¼ì²é·þÎñ½¡¿µ×´Ì¬
echo %GREEN%[3/3] ¼ì²é·þÎñ½¡¿µ×´Ì¬...%NC%

if "%CHOICE%"=="1" (
    REM Ò»Ìå»¯DockerfileÄ£Ê½ÏÂµÄ½¡¿µ¼ì²é
    set BACKEND_URL=http://localhost/api/health
    
    echo %YELLOW%¼ì²é·þÎñ½¡¿µ×´Ì¬...%NC%
    set MAX_RETRIES=10
    set RETRIES=0
    
    :HEALTH_CHECK_LOOP
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED
    
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%BACKEND_URL%' -UseBasicParsing; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%·þÎñÒÑ³É¹¦Æô¶¯!%NC%
        goto :SINGLE_CONTAINER_STARTED
    )
    
    set /a RETRIES+=1
    echo %YELLOW%·þÎñÕýÔÚÆô¶¯ÖÐ£¬ÇëÉÔºò... (!RETRIES!/%MAX_RETRIES%)%NC%
    timeout /t 2 /nobreak > nul
    goto :HEALTH_CHECK_LOOP
    
    :HEALTH_CHECK_FAILED
    echo %RED%¾¯¸æ: ·þÎñ¿ÉÄÜÎ´Õý³£Æô¶¯£¬Çë¼ì²éÈÕÖ¾:%NC%
    echo %GREEN%docker logs char-art-app%NC%
    
    :SINGLE_CONTAINER_STARTED
    echo.
    echo %GREEN%·þÎñµØÖ·:%NC%
    echo %GREEN%Ó¦ÓÃÇ°¶Ë: http://localhost%NC%
    echo.
    echo %YELLOW%³£ÓÃÃüÁî:%NC%
    echo   ²é¿´ÈÝÆ÷×´Ì¬: %GREEN%docker ps%NC%
    echo   ²é¿´Ó¦ÓÃÈÕÖ¾: %GREEN%docker logs char-art-app%NC%
    echo   Í£Ö¹Ó¦ÓÃ: %GREEN%docker stop char-art-app%NC%
    echo   ÖØÆôÓ¦ÓÃ: %GREEN%docker restart char-art-app%NC%
    echo.
    echo %YELLOW%¸ü¶àÅäÖÃÑ¡ÏîÇë²Î¿¼Docker.mdÎÄµµ%NC%
) else (
    REM Docker ComposeÄ£Ê½ÏÂµÄ½¡¿µ¼ì²é
    REM ¼ì²éºó¶Ë·þÎñ
    echo %YELLOW%¼ì²éºó¶Ë·þÎñ...%NC%
    set MAX_RETRIES=10
    set RETRIES=0
    set BACKEND_URL=http://localhost:8080/api/health
    
    :BACKEND_CHECK_LOOP
    if %RETRIES% geq %MAX_RETRIES% goto :BACKEND_CHECK_FAILED
    
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%BACKEND_URL%' -UseBasicParsing; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%ºó¶Ë·þÎñÒÑ³É¹¦Æô¶¯!%NC%
        goto :CHECK_WEBP_PROCESSOR
    )
    
    set /a RETRIES+=1
    echo %YELLOW%ºó¶Ë·þÎñÕýÔÚÆô¶¯ÖÐ£¬ÇëÉÔºò... (!RETRIES!/%MAX_RETRIES%)%NC%
    timeout /t 2 /nobreak > nul
    goto :BACKEND_CHECK_LOOP
    
    :BACKEND_CHECK_FAILED
    echo %RED%¾¯¸æ: ºó¶Ë·þÎñ¿ÉÄÜÎ´Õý³£Æô¶¯£¬Çë¼ì²éÈÕÖ¾:%NC%
    echo %GREEN%docker logs char-art-backend%NC%
    goto :CHECK_WEBP_PROCESSOR
    
    :CHECK_WEBP_PROCESSOR
    REM ¼ì²éWebP´¦Àí·þÎñ
    echo %YELLOW%¼ì²éWebP´¦Àí·þÎñ...%NC%
    set MAX_RETRIES=10
    set RETRIES=0
    set WEBP_URL=http://localhost:8081/api/health
    
    :WEBP_CHECK_LOOP
    if %RETRIES% geq %MAX_RETRIES% goto :WEBP_CHECK_FAILED
    
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%WEBP_URL%' -UseBasicParsing; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%WebP´¦Àí·þÎñÒÑ³É¹¦Æô¶¯!%NC%
        goto :ALL_SERVICES_STARTED
    )
    
    set /a RETRIES+=1
    echo %YELLOW%WebP´¦Àí·þÎñÕýÔÚÆô¶¯ÖÐ£¬ÇëÉÔºò... (!RETRIES!/%MAX_RETRIES%)%NC%
    timeout /t 2 /nobreak > nul
    goto :WEBP_CHECK_LOOP
    
    :WEBP_CHECK_FAILED
    echo %RED%¾¯¸æ: WebP´¦Àí·þÎñ¿ÉÄÜÎ´Õý³£Æô¶¯£¬Çë¼ì²éÈÕÖ¾:%NC%
    echo %GREEN%docker logs webp-processor%NC%
    
    :ALL_SERVICES_STARTED
    echo.
    echo %GREEN%·þÎñµØÖ·:%NC%
    echo %GREEN%ºó¶Ë·þÎñ: http://localhost:8080%NC%
    echo %GREEN%WebP´¦Àí·þÎñ: http://localhost:8081%NC%
    echo.
    echo %YELLOW%³£ÓÃÃüÁî:%NC%
    echo   ²é¿´ËùÓÐÈÝÆ÷: %GREEN%docker ps%NC%
    echo   ²é¿´ºó¶ËÈÕÖ¾: %GREEN%docker logs char-art-backend%NC%
    echo   ²é¿´WebP´¦Àí·þÎñÈÕÖ¾: %GREEN%docker logs webp-processor%NC%
    echo   Í£Ö¹ËùÓÐ·þÎñ: %GREEN%docker-compose down%NC%
    echo   ÖØÆôËùÓÐ·þÎñ: %GREEN%docker-compose restart%NC%
    echo.
    echo %YELLOW%¸ü¶àÅäÖÃÑ¡ÏîÇë²Î¿¼Docker.mdÎÄµµ%NC%
)