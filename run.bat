@echo off
call setup_driver.bat
if errorlevel 1 exit /b 1
if not exist bin mkdir bin
javac -cp "lib\sqlite-jdbc.jar" -d bin src\*.java
if errorlevel 1 exit /b 1
java -cp "bin;lib\sqlite-jdbc.jar" HardwareShopApp
