@echo off
setlocal

:: Configuration
set JAVAC=javac
set JAVA=java
set SRC_DIR=src
set BIN_DIR=bin

:: Commands
if "%1"=="compile" goto compile
if "%1"=="run-server" goto runserver
if "%1"=="run-client" goto runclient
if "%1"=="clean" goto clean

:default
echo Usage: build.bat [compile|run-server|run-client|clean]
goto end

:compile
if not exist %BIN_DIR% mkdir %BIN_DIR%
for %%f in (%SRC_DIR%\\*.java) do (
    %JAVAC% -d %BIN_DIR% %%f
)
goto end

:runserver
call %0 compile >nul
%JAVA% -cp %BIN_DIR% MathServer
goto end

:runclient
call %0 compile >nul
%JAVA% -cp %BIN_DIR% MathClient
goto end

:clean
if exist %BIN_DIR% (
    rmdir /s /q %BIN_DIR%
)
goto end

:end
endlocal
