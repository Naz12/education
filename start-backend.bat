@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "JAVA_HOME=C:\Program Files\OpenJDK\jdk-25"
set "PATH=C:\Program Files\OpenJDK\jdk-25\bin;%PATH%"

echo Starting backend with JAVA_HOME=%JAVA_HOME%
cd /d "%PROJECT_ROOT%backend-spring"

if not exist "pom.xml" (
  echo [ERROR] backend-spring/pom.xml not found.
  exit /b 1
)

mvn spring-boot:run

endlocal
