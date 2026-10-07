@echo off
setlocal

rem ============================================================
rem  打包成一个 jar（前端页面 + 后端服务都在一起）
rem  打包完双击 start-standalone.cmd 就能跑
rem ============================================================

set JAVA_HOME=D:\dev\jdk-21\jdk-21.0.12.1+1
set MVN=D:\dev\apache-maven-3.9.1\bin\mvn.cmd
set ROOT=%~dp0

echo ========================================
echo   打包 ai-table
echo ========================================
echo.

echo [1/3] 构建前端页面...
cd /d "%ROOT%frontend"
rem 显式把 node_modules\.bin 加进 PATH（否则可能报「'vue-tsc' 不是内部或外部命令」）
set "PATH=%CD%\node_modules\.bin;%PATH%"

rem 临时目录指到项目内：
rem 有些机器上 esbuild 删不掉系统临时目录里的文件，会报 Access is denied，
rem 那通常是杀毒软件的实时防护锁住了刚创建的文件，换个目录就能绕开
set "TEMP=%ROOT%.build-tmp"
set "TMP=%ROOT%.build-tmp"
if not exist "%TEMP%" mkdir "%TEMP%"

rem 版本副本里不带依赖（不然副本会很大），所以第一次运行会自动装一次
if not exist "node_modules\vite" (
  echo.
  echo   还没装前端依赖，正在安装（第一次要几分钟，请耐心等）...
  echo.
  call npm install
  if errorlevel 1 (
    echo.
    echo *** 前端依赖安装失败 ***
    pause
    exit /b 1
  )
)

call npm run build
if errorlevel 1 (
  echo.
  echo   构建失败。如果错误是 Access is denied（杀毒软件临时锁了文件），
  echo   正在自动重试一次...
  echo.
  call npm run build
)
if errorlevel 1 (
  echo.
  echo *** 前端构建失败 ***
  echo.
  echo   如果错误是「Access is denied」，可以试试：
  echo     1. 把项目的 .build-tmp 目录加进杀毒软件的白名单
  echo     2. 或者临时关掉杀毒软件的「实时防护」再跑一次
  pause
  exit /b 1
)

echo.
echo [2/3] 把前端产物放进后端的静态目录...
if exist "%ROOT%backend\src\main\resources\static" rd /s /q "%ROOT%backend\src\main\resources\static"
xcopy /E /I /Y "%ROOT%frontend\dist" "%ROOT%backend\src\main\resources\static" >nul
if errorlevel 1 (
  echo.
  echo *** 复制前端产物失败 ***
  pause
  exit /b 1
)

echo.
echo [3/3] 打包后端...
cd /d "%ROOT%backend"
call "%MVN%" -B clean package -DskipTests
if errorlevel 1 (
  echo.
  echo *** 后端打包失败 ***
  pause
  exit /b 1
)

echo.
echo ========================================
echo   打包完成
echo.
echo   产物：backend\target\ai-table-backend-0.0.1-SNAPSHOT.jar
echo   启动：双击 start-standalone.cmd
echo ========================================
pause