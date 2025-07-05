@echo off
:repeat
java -jar app\build\libs\app.jar
echo Application stopped. Restarting in 3 seconds...
timeout /t 3
goto repeat
