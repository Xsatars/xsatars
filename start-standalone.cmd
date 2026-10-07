@echo off
setlocal

rem ============================================================
rem  启动 ai-table（前端 + 后端都在这个进程里）
rem  别人用浏览器访问下面显示的地址即可，不需要任何文件
rem ============================================================

rem 优先用系统里的 JAVA_HOME；没有就用下面这个默认值（改成你自己的 JDK 路径）
if not defined JAVA_HOME set "JAVA_HOME=D:\dev\jdk-21\jdk-21.0.12.1+1"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo   找不到 JDK，请设置 JAVA_HOME 环境变量，或改这个文件里的默认路径。
  pause
  exit /b 1
)
set JAR=%~dp0backend\target\ai-table-backend-0.0.1-SNAPSHOT.jar

if not exist "%JAR%" (
  echo.
  echo   还没打包过，请先双击 build-release.cmd
  echo.
  pause
  exit /b 1
)

echo ========================================
echo   启动 ai-table
echo ========================================
echo.
echo   本机访问：   http://localhost:8080
echo.
echo   别人要玩，把下面这个"局域网地址"发给他就行：
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
  for /f "tokens=1" %%b in ("%%a") do echo                http://%%b:8080
)
echo.
echo   注意：
echo   1. 别人的电脑要和你连在同一个网络（同一个 WiFi / 同一个路由器）
echo   2. 第一次启动时 Windows 可能弹防火墙提示，要点"允许访问"
echo   3. 如果对方在异地，需要用内网穿透工具（见 README 的说明）
echo.
echo   关闭这个窗口 = 停止服务
echo ========================================
echo.

"%JAVA_HOME%\bin\java.exe" -jar "%JAR%"
pause
