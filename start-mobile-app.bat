@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
cd /d "%PROJECT_ROOT%mobile-flutter"

if not exist "pubspec.yaml" (
  echo [ERROR] mobile-flutter/pubspec.yaml not found.
  exit /b 1
)

echo Getting Flutter dependencies...
call flutter pub get
if errorlevel 1 (
  echo [ERROR] flutter pub get failed.
  exit /b 1
)

echo Running mobile app on connected emulator/device...
call flutter run

endlocal
