@echo off
setlocal

set MAVEN_VERSION=3.9.9
set WRAPPER_DIR=%~dp0.mvn\wrapper
set MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
set MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd

if not exist "%MAVEN_BIN%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "$version='%MAVEN_VERSION%';" ^
    "$dir='%WRAPPER_DIR%';" ^
    "$zip=Join-Path $dir ('apache-maven-' + $version + '-bin.zip');" ^
    "$url='https://archive.apache.org/dist/maven/maven-3/' + $version + '/binaries/apache-maven-' + $version + '-bin.zip';" ^
    "if (!(Test-Path $zip)) { Invoke-WebRequest -Uri $url -OutFile $zip };" ^
    "Expand-Archive -Path $zip -DestinationPath $dir -Force"
)

call "%MAVEN_BIN%" %*
set MAVEN_EXIT_CODE=%ERRORLEVEL%
endlocal & exit /b %MAVEN_EXIT_CODE%
