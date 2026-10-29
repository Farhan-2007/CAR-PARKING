```bat
@echo off
cd /d "%~dp0"

javac -cp ".;postgresql-42.7.14.jar" *.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)

java -cp ".;postgresql-42.7.14.jar" Main
pause
```