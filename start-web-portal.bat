@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
cd /d "%PROJECT_ROOT%web-portal"

if not exist "package.json" (
  echo [ERROR] web-portal/package.json not found.
  exit /b 1
)

if not exist "node_modules" (
  echo Installing web portal dependencies...
  npm install
  if errorlevel 1 (
    echo [ERROR] npm install failed.
    exit /b 1
  )
)

echo Starting web portal on Vite dev server...
npm start

endlocal
