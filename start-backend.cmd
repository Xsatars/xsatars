@echo off
rem ============================================================
rem  启动后端（Windows 本地开发用）
rem
rem  如果你只是想玩这个游戏，用 Docker 更省事：
rem      docker compose up -d
rem  这个脚本是给"改代码、调试"用的。
rem
rem  首次使用前请确认：电脑上装了 JDK 21，并且下面能找到它。
rem  找不到的话，要么设置系统环境变量 JAVA_HOME，要么直接改这个文件。
rem ============================================================
setlocal

rem ---- JDK 21 ----
if not defined JAVA_HOME set "JAVA_HOME=D:\dev\jdk-21\jdk-21.0.12.1+1"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo.
  echo   找不到 JDK。请做下面任意一件事：
  echo     1. 设置系统环境变量 JAVA_HOME，指向你的 JDK 21 目录
  echo     2. 用记事本打开这个文件，把 JAVA_HOME= 那一行改成你的路径
  echo.
  echo   提醒：只玩不开发的话，用 Docker 最省事：docker compose up -d
  echo.
  pause
  exit /b 1
)

rem ---- Maven：先在 PATH 里找，找不到再试常见安装位置 ----
for %%i in (mvn.cmd) do set "MAVEN=%%~$PATH:i"
if not defined MAVEN (
  if exist "D:\dev\apache-maven-3.9.1\bin\mvn.cmd" (
    set "MAVEN=D:\dev\apache-maven-3.9.1\bin\mvn.cmd"
  )
)
if not defined MAVEN (
  echo.
  echo   找不到 Maven。请把 mvn 加进 PATH，或者改这个文件里 MAVEN 那一段的默认路径。
  echo.
  pause
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

rem 8080 被占用就明确提示（打包版、IDEA 里的服务都会占它）
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo.
  echo   *** 端口 8080 已经被占用了，起不来 ***
  echo.
  echo   常见原因：
  echo     1. 之前双击过 start-standalone.cmd（打包版），那个黑窗口还开着
  echo     2. IDEA 里已经启动了一份后端
  echo.
  echo   处理办法：把占用的关掉，再运行这个脚本。
  echo.
  echo   当前占用 8080 的：
  netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"
  echo.
  pause
  exit /b 1
)

echo [AI Table] JDK   : %JAVA_HOME%
echo [AI Table] Maven : %MAVEN%
echo [AI Table] 正在启动后端（端口 8080）...
echo [AI Table] 启动完成后可访问 http://localhost:8080/api/health
echo.

cd /d "%~dp0backend"
call "%MAVEN%" spring-boot:run

echo.
echo [AI Table] 后端已停止
pause