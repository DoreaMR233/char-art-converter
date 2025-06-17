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

REM ÏÔÊ¾Ñ¡Ôñ²Ëµ¥
echo %GREEN%ÇëÑ¡ÔñÆô¶¯·½Ê½:%NC%
echo %YELLOW%1. Ê¹ÓÃDocker Run£¨µ¥ÈÝÆ÷Ä£Ê½£©%NC%
echo %YELLOW%2. Ê¹ÓÃDocker Compose£¨¶àÈÝÆ÷Ä£Ê½£©%NC%
echo.

set /p CHOICE=ÇëÊäÈëÑ¡Ôñ£¨1»ò2£©: 

if "%CHOICE%"=="1" (
    set USE_DOCKER_RUN=true
) else if "%CHOICE%"=="2" (
    set USE_DOCKER_RUN=false
) else (
    echo %RED%´íÎó: ÎÞÐ§µÄÑ¡Ôñ£¬ÇëÊäÈë1»ò2%NC%
	pause
    exit /b 1
)

if "%USE_DOCKER_RUN%"=="true" (
    REM ¹¹½¨Docker¾µÏñ
    echo %GREEN%[1/5] ¹¹½¨Docker¾µÏñ...%NC%
    docker build -t %IMAGE_NAME%:latest .

    if %ERRORLEVEL% neq 0 (
        echo %RED%´íÎó: ¹¹½¨Docker¾µÏñÊ§°Ü%NC%
        pause
        exit /b 1
    )

    echo %GREEN%[2/5] ¼ì²é²¢Í£Ö¹ÒÑ´æÔÚµÄÈÝÆ÷...%NC%
    REM ¼ì²éÈÝÆ÷ÊÇ·ñÒÑ´æÔÚ£¬Èç¹û´æÔÚÔòÍ£Ö¹²¢É¾³ý
    docker ps -a | findstr "%CONTAINER_NAME%" > nul
    if %ERRORLEVEL% equ 0 (
        echo %YELLOW%·¢ÏÖÒÑ´æÔÚµÄ%CONTAINER_NAME%ÈÝÆ÷£¬ÕýÔÚÍ£Ö¹²¢É¾³ý...%NC%
        docker stop %CONTAINER_NAME% > nul 2>&1
        docker rm %CONTAINER_NAME% > nul 2>&1
    )

    REM ¼ì²éÍøÂçÊÇ·ñ´æÔÚ£¬Èç¹û²»´æÔÚÔò´´½¨
    echo %GREEN%[3/5] ¼ì²éDockerÍøÂç...%NC%
    docker network ls | findstr "char-art-network" > nul
    if %ERRORLEVEL% neq 0 (
        echo %YELLOW%´´½¨DockerÍøÂç: char-art-network%NC%
        docker network create char-art-network
        
        if %ERRORLEVEL% neq 0 (
            echo %RED%´íÎó: ´´½¨ÍøÂçÊ§°Ü%NC%
            pause
            exit /b 1
        )
    )

    REM ÉèÖÃ»·¾³±äÁ¿
    echo %GREEN%[4/5] ÅäÖÃ»·¾³±äÁ¿...%NC%
    echo %YELLOW%ÇëÎªÃ¿¸ö»·¾³±äÁ¿ÊäÈëÖµ£¬»òÖ±½Ó°´»Ø³µÊ¹ÓÃÄ¬ÈÏÖµ%NC%
    echo.

    set /p PORT=·þÎñ¼àÌý¶Ë¿Ú (Ä¬ÈÏ: 5000): 
    if ""%PORT%""==""" set PORT=5000

    set /p LOG_LEVEL=ÈÕÖ¾¼¶±ð (DEBUG, INFO, WARNING, ERROR, CRITICAL) (Ä¬ÈÏ: INFO): 
    if ""%LOG_LEVEL%""==""" set LOG_LEVEL=INFO

    set /p DEBUG=µ÷ÊÔÄ£Ê½ (Ä¬ÈÏ: False): 
    if ""%DEBUG%""==""" set DEBUG=False

    set /p MAX_CONTENT_LENGTH=×î´óÉÏ´«ÎÄ¼þ´óÐ¡£¨×Ö½Ú£© (Ä¬ÈÏ: 16777216): 
    if ""%MAX_CONTENT_LENGTH%""==""" set MAX_CONTENT_LENGTH=16777216

    set /p TEMP_FILE_TTL=ÁÙÊ±ÎÄ¼þ±£ÁôÊ±¼ä£¨Ãë£© (Ä¬ÈÏ: 3600): 
    if ""%TEMP_FILE_TTL%""==""" set TEMP_FILE_TTL=3600

    REM Æô¶¯ÈÝÆ÷
    echo %GREEN%[5/5] Æô¶¯WebP´¦Àí·þÎñÈÝÆ÷...%NC%
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
        echo %RED%´íÎó: Æô¶¯ÈÝÆ÷Ê§°Ü%NC%
        pause
        exit /b 1
    )
) else (
    REM ¼ì²éDocker ComposeÊÇ·ñ°²×°
    docker-compose --version > nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo %RED%´íÎó: Docker ComposeÎ´°²×°¡£ÇëÏÈ°²×°Docker Compose: https://docs.docker.com/compose/install/%NC%
        pause
        exit /b 1
    )

    echo %GREEN%[1/2] Ê¹ÓÃDocker ComposeÆô¶¯·þÎñ...%NC%
    docker-compose up -d

    if %ERRORLEVEL% neq 0 (
        echo %RED%´íÎó: Æô¶¯·þÎñÊ§°Ü%NC%
        pause
        exit /b 1
    )
)

REM µÈ´ý·þÎñÆô¶¯
echo %GREEN%[*] µÈ´ý·þÎñÆô¶¯...%NC%
timeout /t 3 /nobreak > nul

REM ¼ì²é·þÎñ½¡¿µ×´Ì¬
echo %YELLOW%¼ì²é·þÎñ½¡¿µ×´Ì¬...%NC%
set MAX_RETRIES=10
set RETRIES=0

if "%USE_DOCKER_RUN%"=="true" (
    set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%/api/health
    
    :HEALTH_CHECK_LOOP_SINGLE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_SINGLE
    
    REM Ê¹ÓÃPowerShellÖ´ÐÐ½¡¿µ¼ì²é
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%·þÎñÒÑ³É¹¦Æô¶¯!%NC%
        echo %GREEN%WebP´¦Àí·þÎñµØÖ·: http://localhost:%HOST_PORT%%NC%
        echo %GREEN%½¡¿µ¼ì²é: %HEALTH_CHECK_URL%%NC%
        echo.
        echo %YELLOW%³£ÓÃÃüÁî:%NC%
        echo   %GREEN%²é¿´ÈÕÖ¾: docker logs %CONTAINER_NAME%%NC%
        echo   %GREEN%Í£Ö¹·þÎñ: docker stop %CONTAINER_NAME%%NC%
        echo   %GREEN%Æô¶¯·þÎñ: docker start %CONTAINER_NAME%%NC%
        echo   %GREEN%É¾³ýÈÝÆ÷: docker rm %CONTAINER_NAME%%NC%
        echo.
        echo %YELLOW%¸ü¶àÅäÖÃÑ¡ÏîÇë²Î¿¼Docker.mdÎÄµµ%NC%
        pause
        exit /b 0
    )
    
    set /a RETRIES+=1
    
    echo %YELLOW%·þÎñÕýÔÚÆô¶¯ÖÐ£¬ÇëÉÔºò... (!RETRIES!/%MAX_RETRIES%)%NC%
    
    timeout /t 2 /nobreak > nul
    goto :HEALTH_CHECK_LOOP_SINGLE
    
    :HEALTH_CHECK_FAILED_SINGLE
    echo %RED%¾¯¸æ: ·þÎñ¿ÉÄÜÎ´Õý³£Æô¶¯£¬Çë¼ì²éÈÕÖ¾:%NC%
    echo %GREEN%docker logs %CONTAINER_NAME%%NC%
    pause
    exit /b 1
) else (
    set HEALTH_CHECK_URL=http://localhost:8081/api/health
    
    :HEALTH_CHECK_LOOP_COMPOSE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_COMPOSE
    
    REM Ê¹ÓÃPowerShellÖ´ÐÐ½¡¿µ¼ì²é
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.Content -match 'ok') { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%·þÎñÒÑ³É¹¦Æô¶¯!%NC%
        echo %GREEN%WebP´¦Àí·þÎñµØÖ·: http://localhost:8081%NC%
        echo %GREEN%½¡¿µ¼ì²é: %HEALTH_CHECK_URL%%NC%
        echo.
        echo %YELLOW%³£ÓÃÃüÁî:%NC%
        echo   %GREEN%²é¿´ÈÕÖ¾: docker-compose logs%NC%
        echo   %GREEN%Í£Ö¹·þÎñ: docker-compose down%NC%
        echo   %GREEN%Æô¶¯·þÎñ: docker-compose up -d%NC%
        echo.
        echo %YELLOW%¸ü¶àÅäÖÃÑ¡ÏîÇë²Î¿¼Docker.mdÎÄµµ%NC%
        pause
        exit /b 0
    )
    
    set /a RETRIES+=1
    
    echo %YELLOW%·þÎñÕýÔÚÆô¶¯ÖÐ£¬ÇëÉÔºò... (!RETRIES!/%MAX_RETRIES%)%NC%
    
    timeout /t 2 /nobreak > nul
    goto :HEALTH_CHECK_LOOP_COMPOSE
    
    :HEALTH_CHECK_FAILED_COMPOSE
    echo %RED%¾¯¸æ: ·þÎñ¿ÉÄÜÎ´Õý³£Æô¶¯£¬Çë¼ì²éÈÕÖ¾:%NC%
    echo %GREEN%docker-compose logs%NC%
    pause
    exit /b 1
)