@echo off
cd /d "%~dp0"

"C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot\bin\java.exe" -cp ".;mysql-connector-j-26.7.0.jar" Main

pause