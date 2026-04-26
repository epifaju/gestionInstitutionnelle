@echo off
setlocal enabledelayedexpansion

REM Resolve JAVA_HOME from current `java` on PATH (java.home)
for /f "tokens=1,* delims==" %%A in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /i "java.home"') do (
  set "RAW=%%B"
)

if not defined RAW (
  echo [ERROR] Unable to resolve java.home from `java`. Please install JDK 21 and set JAVA_HOME.
  exit /b 1
)

REM Trim leading spaces
for /f "tokens=* delims= " %%A in ("%RAW%") do set "JAVA_HOME=%%A"

echo Using JAVA_HOME=%JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

if "%~1"=="" (
  mvn test
) else (
  mvn %*
)

