@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo   HARDWARE SHOP BILLING SYSTEM - TEST
echo ========================================
echo.

where java >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java was not found in PATH.
    echo Please install Java and try again.
    pause
    exit /b 1
)

where javac >nul 2>&1
if errorlevel 1 (
    echo ERROR: javac was not found in PATH.
    echo Please install the JDK and try again.
    pause
    exit /b 1
)

if not exist "lib\sqlite-jdbc.jar" (
    echo ERROR: lib\sqlite-jdbc.jar is missing.
    echo Run setup_driver.bat first.
    pause
    exit /b 1
)

if not exist "bin" mkdir bin

echo [1/3] Compiling source files...
javac -cp "lib\sqlite-jdbc.jar" -d bin src\*.java
if errorlevel 1 (
    echo.
    echo TEST STOPPED: Compilation failed.
    pause
    exit /b 1
)

echo [2/3] Running validation tests...
java -cp "bin;lib\sqlite-jdbc.jar" ValidationTest
if errorlevel 1 (
    echo.
    echo TEST FAILED: ValidationTest returned an error.
    pause
    exit /b 1
)

echo [3/3] Running end-to-end database tests...
java -cp "bin;lib\sqlite-jdbc.jar" SystemTest
if errorlevel 1 (
    echo.
    echo TEST FAILED: SystemTest returned an error.
    pause
    exit /b 1
)

echo.
echo ========================================
echo   ALL TESTS PASSED SUCCESSFULLY
echo ========================================
pause
exit /b 0
