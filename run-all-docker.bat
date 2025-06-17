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
    echo %GREEN%[1/5] Ê¹ÓÃDocker RunÆô¶¯·þÎñ...%NC%

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
    echo %GREEN%[2/5] ¼ì²é²¢Í£Ö¹ÒÑ´æÔÚµÄÈÝÆ÷...%NC%
    docker ps -a --format "{{.Names}}" | findstr /i "char-art-app" > nul
    if %ERRORLEVEL% equ 0 (
        echo %YELLOW%ÈÝÆ÷ÒÑ´æÔÚ£¬ÕýÔÚÍ£Ö¹²¢ÒÆ³ý...%NC%
        docker stop char-art-app > nul 2>&1
        docker rm char-art-app > nul 2>&1
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

    REM Í¨ÓÃÅäÖÃ
    set /p LOG_LEVEL=ÈÕÖ¾¼¶±ð (Ä¬ÈÏ: INFO): 
    if ""%LOG_LEVEL%""==""" set LOG_LEVEL=INFO

    REM ºó¶Ë·þÎñÅäÖÃ
    set /p MAX_FILE_SIZE=×î´óÎÄ¼þ´óÐ¡ (Ä¬ÈÏ: 10MB): 
    if ""%MAX_FILE_SIZE%""==""" set MAX_FILE_SIZE=10MB

    set /p MAX_REQUEST_SIZE=×î´óÇëÇó´óÐ¡ (Ä¬ÈÏ: 10MB): 
    if ""%MAX_REQUEST_SIZE%""==""" set MAX_REQUEST_SIZE=10MB

    REM ºó¶ËÈÕÖ¾ÅäÖÃ
    set /p LOG_FILE_MAX_SIZE=ÈÕÖ¾ÎÄ¼þ×î´ó´óÐ¡ (Ä¬ÈÏ: 10MB): 
    if ""%LOG_FILE_MAX_SIZE%""==""" set LOG_FILE_MAX_SIZE=10MB

    set /p LOG_FILE_MAX_HISTORY=ÈÕÖ¾ÎÄ¼þ±£ÁôÀúÊ·ÊýÁ¿ (Ä¬ÈÏ: 30): 
    if ""%LOG_FILE_MAX_HISTORY%""==""" set LOG_FILE_MAX_HISTORY=30

    REM RedisÅäÖÃ
    set /p REDIS_DATABASE=RedisÊý¾Ý¿âË÷Òý (Ä¬ÈÏ: 0): 
    if ""%REDIS_DATABASE%""==""" set REDIS_DATABASE=0

    set /p REDIS_TIMEOUT=Redis³¬Ê±Ê±¼ä£¬µ¥Î»ºÁÃë (Ä¬ÈÏ: 60000): 
    if ""%REDIS_TIMEOUT%""==""" set REDIS_TIMEOUT=60000

    REM ×Ö·û»­»º´æÅäÖÃ
    set /p CHAR_ART_CACHE_TTL=»º´æ¹ýÆÚÊ±¼ä£¬µ¥Î»Ãë (Ä¬ÈÏ: 3600): 
    if ""%CHAR_ART_CACHE_TTL%""==""" set CHAR_ART_CACHE_TTL=3600

    set /p CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=»º´æ¼üÇ°×º (Ä¬ÈÏ: char-art:text:): 
    if ""%CHAR_ART_CACHE_DEFAULT_KEY_PREFIX%""==""" set CHAR_ART_CACHE_DEFAULT_KEY_PREFIX=char-art:text:

    REM WebP´¦ÀíÆ÷ÅäÖÃ
    set /p WEBP_PROCESSOR_CONNECTION_TIMEOUT=WebP´¦ÀíÆ÷Á¬½Ó³¬Ê±Ê±¼ä£¬µ¥Î»ºÁÃë (Ä¬ÈÏ: 5000): 
    if ""%WEBP_PROCESSOR_CONNECTION_TIMEOUT%""==""" set WEBP_PROCESSOR_CONNECTION_TIMEOUT=5000

    set /p WEBP_PROCESSOR_MAX_RETRIES=WebP´¦ÀíÆ÷×î´óÖØÊÔ´ÎÊý (Ä¬ÈÏ: 2): 
    if ""%WEBP_PROCESSOR_MAX_RETRIES%""==""" set WEBP_PROCESSOR_MAX_RETRIES=2

    REM Python WebP´¦ÀíÆ÷ÅäÖÃ
    set /p DEBUG=ÊÇ·ñ¿ªÆôµ÷ÊÔÄ£Ê½ (Ä¬ÈÏ: False): 
    if ""%DEBUG%""==""" set DEBUG=False

    set /p MAX_CONTENT_LENGTH=×î´óÄÚÈÝ³¤¶È£¬µ¥Î»×Ö½Ú (Ä¬ÈÏ: 16777216): 
    if ""%MAX_CONTENT_LENGTH%""==""" set MAX_CONTENT_LENGTH=16777216

    set /p TEMP_FILE_TTL=ÁÙÊ±ÎÄ¼þ´æ»îÊ±¼ä£¬µ¥Î»Ãë (Ä¬ÈÏ: 3600): 
    if ""%TEMP_FILE_TTL%""==""" set TEMP_FILE_TTL=3600

    REM ×Ö·û»­Ä¬ÈÏÅäÖÃ
    set /p DEFAULT_DENSITY=Ä¬ÈÏ×Ö·ûÃÜ¶È (Ä¬ÈÏ: medium): 
    if ""%DEFAULT_DENSITY%""==""" set DEFAULT_DENSITY=medium

    set /p DEFAULT_COLOR_MODE=Ä¬ÈÏÑÕÉ«Ä£Ê½ (Ä¬ÈÏ: grayscale): 
    if ""%DEFAULT_COLOR_MODE%""==""" set DEFAULT_COLOR_MODE=grayscale

    REM Ç°¶ËÅäÖÃ
    set /p BASE_PATH=Ç°¶Ë×ÊÔ´Â·¾¶Ç°×º (Ä¬ÈÏÎª¿Õ): 
    if ""%BASE_PATH%""==""" set BASE_PATH=

    REM Æô¶¯ÈÝÆ÷
    echo %GREEN%[5/5] Æô¶¯×Ö·û»­×ª»»Æ÷ÈÝÆ÷...%NC%
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

    echo %GREEN%[1/3] Ê¹ÓÃDocker ComposeÆô¶¯·þÎñ...%NC%
    docker-compose up -d

    if %ERRORLEVEL% neq 0 (
        echo %RED%´íÎó: Æô¶¯·þÎñÊ§°Ü%NC%
	    pause
        exit /b 1
    )
)

REM µÈ´ý·þÎñÆô¶¯
echo %GREEN%[2/3] µÈ´ý·þÎñÆô¶¯...%NC%
timeout /t 5 /nobreak > nul

REM ¼ì²é·þÎñ½¡¿µ×´Ì¬
echo %GREEN%[3/3] ¼ì²é·þÎñ½¡¿µ×´Ì¬...%NC%

if "%USE_DOCKER_RUN%"=="true" (
    REM Docker RunÄ£Ê½ÏÂµÄ½¡¿µ¼ì²é
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