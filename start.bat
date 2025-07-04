@echo off
:loop
echo Starting...
java -jar app\build\libs\app.jar
echo Application stopped. Restarting in 5 seconds...
timeout /t 5
goto loop
