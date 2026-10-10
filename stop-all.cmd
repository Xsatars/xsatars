@echo off
title 关闭 AI Table 的前后端服务
setlocal enabledelayedexpansion

echo ============================================
echo    关闭 AI Table 的服务
echo ============================================
echo.

set FOUND=0

rem 找出占用 8080（后端/打包版）和 5173（前端开发服务器）的进程，逐个关掉
for %%P in (8080 5173) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr ":%%P " ^| findstr "LISTENING"') do (
    if not "%%A"=="0" (
      set FOUND=1
      echo   端口 %%P 被 PID %%A 占用，正在关闭...
      taskkill /PID %%A /F >nul 2>&1
      if errorlevel 1 (
        echo     失败：权限不够，请右键这个文件选「以管理员身份运行」
      ) else (
        echo     已关闭
      )
    )
  )
)

rem Redis 也顺便关掉（它是启动脚本拉起来的）
tasklist /FI "IMAGENAME eq redis-server.exe" 2>nul | findstr /I "redis-server" >nul
if not errorlevel 1 (
  set FOUND=1
  echo   关闭 Redis...
  taskkill /IM redis-server.exe /F >nul 2>&1
)

echo.
if "!FOUND!"=="0" (
  echo   没有发现正在运行的服务（可能已经关干净了）。
) else (
  echo   处理完毕。下面确认一下端口状态：
  echo.
  netstat -ano | findstr ":8080 " | findstr "LISTENING"
  netstat -ano | findstr ":5173 " | findstr "LISTENING"
  echo   （上面没有输出就说明端口已经释放了）
)

echo.
echo   注意：MySQL 是系统服务，这个脚本不动它。
echo         要停 MySQL 的话按下面做（需要管理员）：
echo           net stop MYSQL80
echo.
pause