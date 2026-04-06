@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem Wipes operational data for the default organization. Keeps:
rem   - User "superadmin" (and their SUPER_ADMIN role link)
rem   - Organizations, system roles, cities/subcities/weredas/clusters
rem   - grade_groups rows and grade_codes (grades preserved)
rem   - checklist_item_type_defaults
rem Requires PostgreSQL client (psql) on PATH.
rem Connection defaults match backend-spring/src/main/resources/application.yml
rem unless you override with environment variables below.
rem ---------------------------------------------------------------------------

if "%DB_HOST%"=="" set "DB_HOST=localhost"
if "%DB_PORT%"=="" set "DB_PORT=5432"
if "%DB_NAME%"=="" set "DB_NAME=supervision"
if "%DB_USER%"=="" set "DB_USER=postgres"

if not defined PGPASSWORD (
  set "PGPASSWORD=root1234"
  echo Using default PGPASSWORD from application.yml. Set PGPASSWORD in the environment to override.
)

set "SQLFILE=%~dp0scripts\postgres\clean_default_org_keep_superadmin.sql"

where psql >nul 2>&1
if errorlevel 1 (
  echo ERROR: psql not found. Install PostgreSQL client tools and add them to PATH.
  exit /b 1
)

if not exist "%SQLFILE%" (
  echo ERROR: Missing SQL script: "%SQLFILE%"
  exit /b 1
)

echo.
echo This will DELETE most data for org 11111111-1111-1111-1111-111111111111
echo except superadmin, system roles, geography, grade groups, and type defaults.
echo Database: %DB_USER%@%DB_HOST%:%DB_PORT%/%DB_NAME%
echo.
pause

psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "%DB_NAME%" -v ON_ERROR_STOP=1 -f "%SQLFILE%"
set "ERR=%ERRORLEVEL%"
if not "%ERR%"=="0" (
  echo.
  echo FAILED with exit code %ERR%.
  exit /b %ERR%
)

echo.
echo Done. Start the backend with app.demo-data.enabled=false ^(default^) so demo data is not re-seeded.
endlocal
exit /b 0
