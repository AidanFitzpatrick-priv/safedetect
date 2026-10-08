@echo off
setlocal EnableExtensions
title SafeDetect installer

set "SRC=%~dp0safedetect-agent.jar"
set "DEST=%APPDATA%\.minecraft\safedetect"

echo.
echo  SafeDetect installer
echo  ====================
echo.

if not exist "%SRC%" (
    echo  safedetect-agent.jar was not found next to this file.
    echo  Extract the whole zip first, then run install.bat from the extracted folder.
    goto :fail
)

if not exist "%DEST%" mkdir "%DEST%"
copy /y "%SRC%" "%DEST%\safedetect-agent.jar" >nul 2>&1
if errorlevel 1 (
    echo  Could not copy the jar. Fully close Lunar Client and Minecraft, then run this again.
    goto :fail
)
echo  Installed to %DEST%\safedetect-agent.jar

rem Lunar splits JVM arguments on spaces, so use the short 8.3 path when the real one has any.
set "JAR=%DEST%\safedetect-agent.jar"
if not "%JAR: =%"=="%JAR%" for %%I in ("%JAR%") do set "JAR=%%~sI"
set "ARG=-javaagent:%JAR%"

<nul set /p "=%ARG%" | clip
echo.
echo  This JVM argument is now on your clipboard:
echo.
echo     %ARG%
echo.
echo  Last step, in the Lunar Client launcher:
echo    1. Click the settings cog, open the 1.8.9 / Minecraft 1.8 profile settings.
echo    2. Find "JVM Arguments" and paste (Ctrl+V). Keep anything already there,
echo       separated by a space.
echo    3. Close settings so it saves, then launch 1.8.9.
echo.
echo  In game you should see "[SD] SafeDetect is running." and the overlay window.
echo  Type /sd help for commands. Add your Hypixel API key with the Keys button
echo  at the bottom of the overlay.
echo.

set "LUNAR=%LOCALAPPDATA%\Programs\Lunar Client\Lunar Client.exe"
if exist "%LUNAR%" (
    choice /c YN /n /m "  Open the Lunar Client launcher now? [Y/N] "
    if not errorlevel 2 start "" "%LUNAR%"
)
echo.
pause
exit /b 0

:fail
echo.
pause
exit /b 1
