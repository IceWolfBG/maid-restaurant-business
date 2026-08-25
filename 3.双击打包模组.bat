@echo off
chcp 936 >nul
setlocal EnableDelayedExpansion

REM ==========================================
REM 1. 必加修复：修复系统路径
REM 确保 PowerShell 能运行，否则 JAR.txt 无法显示
REM ==========================================
set "PATH=%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SystemRoot%\System32\WindowsPowerShell\v1.0\"

title Minecraft 模组打包中...
echo.

echo ==========================================
echo [1/2] 正在准备打包环境...
echo ==========================================

REM 定位内置 JDK
for /d %%i in ("%~dp0runtime\*") do (
    set "JAVA_HOME=%%i"
    goto :found_java
)
echo [错误] 未在 runtime 文件夹中找到 JDK !
pause
exit /b 1

:found_java
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo [2/2] 正在执行打包命令...
echo ------------------------------------------
echo 提示：正在跳过单元测试，强制生成模组文件...
echo 请耐心等待，不要关闭窗口。
echo.

REM ==========================================
REM 2. 核心打包命令
REM -x test: 跳过代码检查，提高小白打包成功率
REM --no-daemon: 禁用后台进程，防止文件占用
REM -g: 强制指定便携缓存目录
REM ==========================================
call "%~dp0gradlew.bat" build -x test --no-daemon -g "%~dp0.gradle_repo"

echo.
REM 使用 goto 判定失败，规避括号解析风险
if %ERRORLEVEL% NEQ 0 goto :build_failed

REM ==========================================
REM 3. 成功展示：读取 JAR.txt (这里严格保留了！)
REM ==========================================
if exist "%~dp0runtime\JAR.txt" (
    powershell -NoProfile -Command "Get-Content -Encoding UTF8 '%~dp0runtime\JAR.txt'"
)

echo.
echo [成功] 打包完成!
echo.

set "OUTDIR=%~dp0build\libs"

REM 剥离 if/else 代码块，使用 goto 防止 (.jar) 的括号引发语法崩溃
if not exist "%OUTDIR%" goto :no_outdir

echo 正在自动打开输出目录...
echo 请查收您的模组文件 (.jar)
start "" explorer "%OUTDIR%"
goto :end_script

:no_outdir
echo [提示] 未找到输出目录：%OUTDIR%
goto :end_script

:build_failed
echo ==========================================
echo [失败] 打包失败！
echo ==========================================
echo 请检查代码是否有红色波浪线报错。
echo 建议截图错误信息联系作者。
pause
exit /b 1

:end_script
echo.
echo 按任意键退出...
pause >nul
endlocal