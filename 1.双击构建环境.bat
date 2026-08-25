@echo off
chcp 936 >nul
setlocal EnableDelayedExpansion

REM 修复系统环境变量
set "PATH=%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SystemRoot%\System32\WindowsPowerShell\v1.0\"

title Minecraft模组开发环境初始化
echo.

REM 读取 Creeper.txt
if exist "%~dp0runtime\Creeper.txt" (
    powershell -NoProfile -Command "Get-Content -Encoding UTF8 '%~dp0runtime\Creeper.txt'"
)
echo.

echo ==================================================
echo         欢迎来到 Minecraft 模组开发世界！
echo ==================================================
echo.
echo [1/3] 正在召唤 Java 核心环境...
echo --------------------------------------------------

REM 尝试运行标准化脚本
if exist "%~dp0runtime\normalize-bats.ps1" powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0runtime\normalize-bats.ps1" >nul 2>nul

REM 定位内置 JDK
for /d %%i in ("%~dp0runtime\*") do (
    set "JAVA_HOME=%%i"
    goto :found_java
)
echo.
echo [严重错误] 找不到 Java 核心！
echo 请检查您下载的压缩包是否完整，或者 runtime 文件夹是否丢失。
pause
exit /b 1

:found_java
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM 验证 Java 版本
echo 核心装载完毕，当前版本:
java -version
echo.

echo [2/3] 正在验证构建工具...
echo --------------------------------------------------
echo 正在为您搭建“工作台” (Gradle)，这需要一点时间...
echo.

echo [3/3] 开始构建模组地基 (关键步骤)
echo ==================================================
echo  !!! 高能预警 (必读) !!!
echo ==================================================
echo  1. 首次构建可能需要 3-5 分钟，取决于您的网速。
echo  2. 屏幕如果“不动了”是在下载资源，千万不要关闭窗口！
echo  3. 请确保没有开启 360、火绒 等杀毒软件，否则可能会误删文件。
echo ==================================================
echo.
echo 正在执行构建指令... 请稍候...
echo.

call "%~dp0gradlew.bat" build --no-daemon -g "%~dp0.gradle_repo"

echo.
echo ==================================================
REM 使用 if %ERRORLEVEL% 判断，若不为 0 则跳转到错误处理
if %ERRORLEVEL% NEQ 0 goto :build_failed

REM --- 成功逻辑 ---
if exist "%~dp0runtime\Loader.txt" (
    powershell -NoProfile -Command "Get-Content -Encoding UTF8 '%~dp0runtime\Loader.txt'"
)
echo.
echo [成就达成] 模组开发环境已部署完毕！
echo ==================================================
echo.
echo 现在，您可以关闭此窗口。
echo 接下来请打开 IDEA，开始发挥您的创造力吧！
echo.
goto :end_script

:build_failed
REM --- 失败逻辑 ---
echo [Boom!] 就像苦力怕爆炸了一样，构建失败了...
echo ==================================================
echo.
echo 可能的原因：
echo  - 网络连接不稳定 (建议检查网线或WiFi)
echo  - 杀毒软件拦截了 Java (请暂时关闭后重试)
echo.
echo 请截图此窗口的错误信息，联系作者寻求帮助。

:end_script
echo ==================================================
pause