@echo off
setlocal enabledelayedexpansion

REM ×Ö·û»­×ª»»Æ÷ºó¶ËDocker¿ìËÙÆô¶¯½Å±¾ (Windows°æ)

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
echo %GREEN%=== ×Ö·û»­×ª»»Æ÷ºó¶ËDocker¿ìËÙÆô¶¯½Å±¾ ===%NC%
echo %YELLOW%´Ë½Å±¾½«¹¹½¨²¢Æô¶¯×Ö·û»­×ª»»Æ÷ºó¶Ë·þÎñ%NC%
echo.

REM ÉèÖÃ±äÁ¿
set IMAGE_NAME=char-art-backend
set CONTAINER_NAME=char-art-backend
set HOST_PORT=8080
set CONTAINER_PORT=8080

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

    REM RedisÅäÖÃ
    set /p REDIS_HOST=Redis·þÎñÆ÷µØÖ· (Ä¬ÈÏ: localhost): 
    if ""%REDIS_HOST%""==""" set REDIS_HOST=localhost

    set /p REDIS_PORT=Redis·þÎñÆ÷¶Ë¿Ú (Ä¬ÈÏ: 6379): 
    if ""%REDIS_PORT%""==""" set REDIS_PORT=6379

    set /p REDIS_DATABASE=RedisÊý¾Ý¿âË÷Òý (Ä¬ÈÏ: 0): 
    if ""%REDIS_DATABASE%""==""" set REDIS_DATABASE=0

    set /p REDIS_TIMEOUT=RedisÁ¬½Ó³¬Ê±Ê±¼ä (Ä¬ÈÏ: 60000): 
    if ""%REDIS_TIMEOUT%""==""" set REDIS_TIMEOUT=60000

    REM ×Ö·û»­»º´æÅäÖÃ
    set /p CHAR_ART_CACHE_TTL=»º´æ¹ýÆÚÊ±¼ä£¬µ¥Î»Ãë (Ä¬ÈÏ: 3600): 
    if ""%CHAR_ART_CACHE_TTL%""==""" set CHAR_ART_CACHE_TTL=3600

    set /p CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=»º´æ¼üÇ°×º (Ä¬ÈÏ: char-art:text:): 
    if ""%CHAR_ART_CACHE_DEFAULT_KEY_PREFIX%""==""" set CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=char-art:text:

    REM WebP´¦Àí·þÎñÅäÖÃ
    set /p WEBP_PROCESSOR_URL=WebP´¦Àí·þÎñURL (Ä¬ÈÏ: http://localhost:8081): 
    if ""%WEBP_PROCESSOR_URL%""==""" set WEBP_PROCESSOR_URL=http://localhost:8081

    set /p WEBP_PROCESSOR_ENABLED=ÊÇ·ñÆôÓÃWebP´¦Àí·þÎñ (Ä¬ÈÏ: true): 
    if ""%WEBP_PROCESSOR_ENABLED%""==""" set WEBP_PROCESSOR_ENABLED=true

    set /p WEBP_PROCESSOR_CONNECTION_TIMEOUT=Á¬½Ó³¬Ê±Ê±¼ä (Ä¬ÈÏ: 600000): 
    if ""%WEBP_PROCESSOR_CONNECTION_TIMEOUT%""==""" set WEBP_PROCESSOR_CONNECTION_TIMEOUT=600000

    set /p WEBP_PROCESSOR_MAX_RETRIES=×î´óÖØÊÔ´ÎÊý (Ä¬ÈÏ: 2): 
    if ""%WEBP_PROCESSOR_MAX_RETRIES%""==""" set WEBP_PROCESSOR_MAX_RETRIES=2

    REM ·þÎñÆ÷ÅäÖÃ
    set /p SERVER_PORT=·þÎñÆ÷¶Ë¿Ú (Ä¬ÈÏ: 8080): 
    if ""%SERVER_PORT%""==""" set SERVER_PORT=8080
    set HOST_PORT=%SERVER_PORT%

    REM ÉÏ´«ÎÄ¼þÅäÖÃ
    set /p MAX_FILE_SIZE=×î´óÎÄ¼þ´óÐ¡ (Ä¬ÈÏ: 10MB): 
    if ""%MAX_FILE_SIZE%""==""" set MAX_FILE_SIZE=10MB

    set /p MAX_REQUEST_SIZE=×î´óÇëÇó´óÐ¡ (Ä¬ÈÏ: 10MB): 
    if ""%MAX_REQUEST_SIZE%""==""" set MAX_REQUEST_SIZE=10MB

    REM ÈÕÖ¾ÅäÖÃ
    set /p LOG_LEVEL=ÈÕÖ¾¼¶±ð (Ä¬ÈÏ: INFO): 
    if ""%LOG_LEVEL%""==""" set LOG_LEVEL=INFO

    set /p LOG_FILE_MAX_SIZE=ÈÕÖ¾ÎÄ¼þ×î´ó´óÐ¡ (Ä¬ÈÏ: 10MB): 
    if ""%LOG_FILE_MAX_SIZE%""==""" set LOG_FILE_MAX_SIZE=10MB

    set /p LOG_FILE_MAX_HISTORY=ÈÕÖ¾ÎÄ¼þ±£ÁôÀúÊ·ÊýÁ¿ (Ä¬ÈÏ: 30): 
    if ""%LOG_FILE_MAX_HISTORY%""==""" set LOG_FILE_MAX_HISTORY=30

    REM ×Ö·û»­Ä¬ÈÏÅäÖÃ
    set /p DEFAULT_DENSITY=Ä¬ÈÏ×Ö·ûÃÜ¶È (Ä¬ÈÏ: medium): 
    if ""%DEFAULT_DENSITY%""==""" set DEFAULT_DENSITY=medium

    set /p DEFAULT_COLOR_MODE=Ä¬ÈÏÑÕÉ«Ä£Ê½ (Ä¬ÈÏ: grayscale): 
    if ""%DEFAULT_COLOR_MODE%""==""" set DEFAULT_COLOR_MODE=grayscale

    REM Æô¶¯ÈÝÆ÷
    echo %GREEN%[5/5] Æô¶¯×Ö·û»­×ª»»Æ÷ºó¶ËÈÝÆ÷...%NC%
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
echo %GREEN%[4/4] µÈ´ý·þÎñÆô¶¯...%NC%
timeout /t 5 /nobreak > nul

REM ¼ì²é·þÎñ½¡¿µ×´Ì¬
echo %YELLOW%¼ì²é·þÎñ½¡¿µ×´Ì¬...%NC%
set MAX_RETRIES=10
set RETRIES=0

if "%USE_DOCKER_RUN%"=="true" (
    set HEALTH_CHECK_URL=http://localhost:%HOST_PORT%/api/health
    
    :HEALTH_CHECK_LOOP_SINGLE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_SINGLE
    
    REM Ê¹ÓÃPowerShellÖ´ÐÐ½¡¿µ¼ì²é
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%·þÎñÒÑ³É¹¦Æô¶¯!%NC%
        echo %GREEN%APIµØÖ·: http://localhost:%HOST_PORT%/api%NC%
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
    set HEALTH_CHECK_URL=http://localhost:8080/api/health
    
    :HEALTH_CHECK_LOOP_COMPOSE
    if %RETRIES% geq %MAX_RETRIES% goto :HEALTH_CHECK_FAILED_COMPOSE
    
    REM Ê¹ÓÃPowerShellÖ´ÐÐ½¡¿µ¼ì²é
    powershell -Command "try { $response = Invoke-WebRequest -Uri '%HEALTH_CHECK_URL%' -UseBasicParsing -ErrorAction Stop; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" > nul 2>&1
    
    if %ERRORLEVEL% equ 0 (
        echo %GREEN%·þÎñÒÑ³É¹¦Æô¶¯!%NC%
        echo %GREEN%APIµØÖ·: http://localhost:8080/api%NC%
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