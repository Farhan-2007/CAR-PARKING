@echo off
cd /d "%~dp0"

java -cp ".;postgresql-42.7.4.jar" Main

pause