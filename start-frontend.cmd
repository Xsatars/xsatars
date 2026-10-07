@echo off
rem ===== AI Table 前端启动脚本 =====
echo [AI Table] 正在启动前端（端口 5173）...
echo [AI Table] 启动完成后访问 http://localhost:5173
echo.

cd /d "%~dp0frontend"
rem 显式把 node_modules\.bin 加进 PATH：
rem 有的环境下 npm 不会自动加，会报「'vite' 不是内部或外部命令」
set "PATH=%CD%\node_modules\.bin;%PATH%"

rem 临时目录指到项目内，避免 esbuild 报「Access is denied」
set "TEMP=%~dp0.build-tmp"
set "TMP=%~dp0.build-tmp"
if not exist "%TEMP%" mkdir "%TEMP%"

rem 版本副本里不带依赖（不然副本会很大），所以第一次运行会自动装一次
if not exist "node_modules\vite" (
  echo.
  echo   还没装前端依赖，正在安装（第一次要几分钟，请耐心等）...
  echo.
  call npm install
)

call npm run dev

echo.
echo [AI Table] 前端已停止
pause