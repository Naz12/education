@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem Wipes operational data for the default organization. Keeps:
rem   - User "superadmin" (and their SUPER_ADMIN role link)
rem   - Organizations, system roles, cities/subcities/weredas/clusters
rem   - grade_groups rows and grade_codes (grades preserved)
rem   - checklist_item_type_defaults
rem Requires PostgreSQL client (psql). Uses PATH, or common Windows install paths.
rem Set PSQL to the full path to psql.exe to override (e.g. set PSQL=C:\...\psql.exe).
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

set "PSQL_CMD="
if defined PSQL (
  if exist "!PSQL!" set "PSQL_CMD=!PSQL!"
)
if not defined PSQL_CMD (
  where psql >nul 2>&1
  if not errorlevel 1 set "PSQL_CMD=psql"
)
if not defined PSQL_CMD (
  for /f "delims=" %%V in ('dir /b /ad /o-n "%ProgramFiles%\PostgreSQL" 2^>nul') do (
    if exist "%ProgramFiles%\PostgreSQL\%%V\bin\psql.exe" (
      set "PSQL_CMD=%ProgramFiles%\PostgreSQL\%%V\bin\psql.exe"
      goto :found_psql
    )
  )
)
:found_psql
if not defined PSQL_CMD (
  if exist "%ProgramFiles(x86)%\PostgreSQL\16\bin\psql.exe" set "PSQL_CMD=%ProgramFiles(x86)%\PostgreSQL\16\bin\psql.exe"
)
if not defined PSQL_CMD (
  if exist "%ProgramFiles%\PostgreSQL\18\bin\psql.exe" set "PSQL_CMD=%ProgramFiles%\PostgreSQL\18\bin\psql.exe"
)
if not defined PSQL_CMD (
  if exist "%ProgramFiles%\PostgreSQL\17\bin\psql.exe" set "PSQL_CMD=%ProgramFiles%\PostgreSQL\17\bin\psql.exe"
)
if not defined PSQL_CMD (
  if exist "%ProgramFiles%\PostgreSQL\16\bin\psql.exe" set "PSQL_CMD=%ProgramFiles%\PostgreSQL\16\bin\psql.exe"
)
if not defined PSQL_CMD (
  if exist "%ProgramFiles%\PostgreSQL\15\bin\psql.exe" set "PSQL_CMD=%ProgramFiles%\PostgreSQL\15\bin\psql.exe"
)
if not defined PSQL_CMD (
  if exist "%ProgramFiles%\PostgreSQL\14\bin\psql.exe" set "PSQL_CMD=%ProgramFiles%\PostgreSQL\14\bin\psql.exe"
)

if not defined PSQL_CMD (
  echo ERROR: psql.exe not found.
  echo.
  echo Install PostgreSQL from https://www.postgresql.org/download/windows/ ^(includes psql in bin^),
  echo or add the PostgreSQL "bin" folder to your PATH, or set PSQL to the full path to psql.exe, e.g.:
  echo   set PSQL=C:\Program Files\PostgreSQL\16\bin\psql.exe
  echo.
  exit /b 1
)
echo Using psql: !PSQL_CMD!

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

"!PSQL_CMD!" -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "%DB_NAME%" -v ON_ERROR_STOP=1 -f "%SQLFILE%"
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
