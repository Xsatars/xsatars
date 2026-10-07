@echo off
setlocal

rem ============================================================
rem  启动内网穿透：把本机的 8080 暴露到公网
rem  这样不在同一个网络的朋友也能玩
rem
rem  必须先启动 start-standalone.cmd（把游戏服务跑起来）
rem ============================================================

rem cloudflared 可能放在几个地方，挨个找一遍（脚本在 ai-table\ 或 versions\v0.1\ 里都能用）
set "CF=%~dp0tools\cloudflared.exe"
if not exist "%CF%" set "CF=%~dp0..\tools\cloudflared.exe"
if not exist "%CF%" set "CF=%~dp0..\..\tools\cloudflared.exe"
if not exist "%CF%" set "CF=%LOCALAPPDATA%\cloudflared\cloudflared.exe"

if not exist "%CF%" (
  echo.
  echo   找不到 cloudflared.exe（内网穿透工具）。
  echo.
  echo   找过这几个位置：
  echo     %~dp0tools\cloudflared.exe
  echo     %~dp0..\tools\cloudflared.exe
  echo     %~dp0..\..\tools\cloudflared.exe
  echo     %LOCALAPPDATA%\cloudflared\cloudflared.exe
  echo.
  echo   可以去这里下载（下载后放进上面任意一个位置，文件名保持 cloudflared.exe）：
  echo     https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
  echo.
  pause
  exit /b 1
)

echo ========================================
echo   启动内网穿透（Cloudflare Tunnel）
echo ========================================
echo.
echo   使用的工具：%CF%
echo.
echo   请稍等十几秒，下面会出现一行网址，长这样：
echo.
echo       https://xxxx-xxxx-xxxx.trycloudflare.com
echo.
echo   把那个 https 开头的网址发给别人，他在任何地方都能玩。
echo.
echo   提醒：
echo   1. 要先启动 start-standalone.cmd，否则对方打开是空的
echo   2. 关掉这个窗口，网址立刻失效
echo   3. 每次启动网址都不一样，要发最新那个
echo ========================================
echo.

"%CF%" tunnel --url http://localhost:8080 --no-autoupdate
pause