@echo off
REM Data Masking Loader GUI起動スクリプト (Windows)

echo ===== Data Masking Loader GUI =====
echo.

if "%1"=="build" goto build
if "%1"=="run" goto run
if "%1"=="build-and-run" goto build-and-run
goto usage

:build
echo プロジェクトをビルド中...
call mvn clean package
if %ERRORLEVEL% NEQ 0 (
    echo ビルド失敗
    exit /b 1
)
echo ビルド成功
goto end

:run
echo GUIアプリケーションを起動中...
call mvn javafx:run
goto end

:build-and-run
call :build
if %ERRORLEVEL% EQU 0 (
    call :run
)
goto end

:usage
echo 使い方:
echo   %0 build           # ビルドのみ
echo   %0 run             # GUIを起動
echo   %0 build-and-run   # ビルドしてGUI起動
echo.
echo 例:
echo   %0 build-and-run
exit /b 1

:end
