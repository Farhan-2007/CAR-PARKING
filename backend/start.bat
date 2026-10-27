@echo off
cd /d "%~dp0"
java -cp ".;postgresql-42.7.14.jar" Main
pause
