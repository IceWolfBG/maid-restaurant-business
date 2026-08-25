@echo off
chcp 936 >nul
setlocal EnableDelayedExpansion

REM ==========================================
REM 修复系统路径
REM ==========================================
set "PATH=%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SystemRoot%\System32\WindowsPowerShell\v1.0\"

title Minecraft 客户端启动中...
echo.

REM ==========================================
REM 读取外部 ASCII LOGO
REM ==========================================
if exist "%~dp0runtime\MINECRAFT.txt" (
    powershell -NoProfile -Command "Get-Content -Encoding UTF8 '%~dp0runtime\MINECRAFT.txt'"
)
echo.

echo ==========================================
echo [1/2] 正在准备游戏环境...
echo ==========================================

REM 自动定位内置 JDK
for /d %%i in ("%~dp0runtime\*") do (
    set "JAVA_HOME=%%i"
    goto :found_java
)
:found_java
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM ==========================================
REM 修复 pSize 报错 (首次运行自动清理缓存)
REM ==========================================
if not exist "%~dp0.first_run_lock" (
    echo [提示] 检测到首次运行，正在清理旧缓存...
    if exist "%~dp0build" rmdir /s /q "%~dp0build"
    type nul > "%~dp0.first_run_lock"
)

echo [2/2] 正在启动 Minecraft...
echo ------------------------------------------
echo 提示：启动过程可能需要 1-2 分钟。
echo 游戏窗口出现前，请【不要关闭】此黑色窗口。
echo ------------------------------------------
echo.

REM ==========================================
REM 启动命令
REM ==========================================
call "%~dp0gradlew.bat" runClient --no-daemon -g "%~dp0.gradle_repo"

REM ==========================================
REM 退出后状态判定
REM ==========================================
echo.
echo ==========================================
echo           游戏进程已结束
echo ==========================================
echo.

REM 使用 ERRORLEVEL 判定跳转
if %ERRORLEVEL% NEQ 0 goto :client_error

:client_success
echo [成功] 游戏已正常关闭。
echo 期待您的下一次创作！
goto :end_script

:client_error
REM 即使是 errorlevel 1，也可能是用户强制关闭窗口导致的，不一定是错
echo [情况 A] 如果您是手动关闭的游戏：
echo    恭喜！模组测试完毕，环境一切正常。
echo    您可以直接关闭此窗口。
echo.
echo [情况 B] 如果游戏【没启动】或【闪退】：
echo    请向上滚动查看红色报错信息，可能的原因：
echo    1. 代码中存在语法错误 (Build Failed)

:end_script
echo.
echo ==========================================
pause