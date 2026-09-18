@echo off
setlocal
if exist "lib\sqlite-jdbc.jar" (
    echo SQLite JDBC driver already exists in lib\sqlite-jdbc.jar
    exit /b 0
)
if not exist "lib" mkdir "lib"
echo Downloading SQLite JDBC driver...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar' -OutFile 'lib\sqlite-jdbc.jar'"
if errorlevel 1 (
    echo Download failed. Check your internet connection and try again.
    exit /b 1
)
echo Driver downloaded successfully.
