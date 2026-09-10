@echo off
chcp 65001 >nul
title SuS Cracker Discord Bot v4.0 Launcher
echo ========================================================
echo        SuS Cracker - Discord Bytecode Suite Bot v4.0
echo ========================================================
echo.

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [-] HATA: 'java' bulunamadi! Lutfen JDK 21+ kurun ve PATH'e ekleyin.
    pause
    exit /b 1
)
java -version 2>&1 | findstr /i "version" >nul
if %errorlevel% neq 0 (
    echo [-] UYARI: Java surumu okunamadi, devam ediliyor...
)

if not exist "discord_bot\bin" mkdir "discord_bot\bin"

echo [*] Bytecode motoru kontrol ediliyor ve derleniyor...
javac -encoding UTF-8 -cp "discord_bot\engine\lib\*" -d "discord_bot\bin" "discord_bot\engine\src\sus\cracker\SusBytecodeEngine.java"
if %errorlevel% neq 0 (
    echo [-] HATA: Java motoru derlenemedi! Lutfen JDK 21+ kurulu oldugundan emin olun.
    echo [-] JAVA_HOME: %JAVA_HOME%
    pause
    exit /b %errorlevel%
)

if not exist "discord_bot\.env" (
    echo [-] HATA: discord_bot\.env bulunamadi! .env.example dosyasini kopyalayip token girin.
    pause
    exit /b 1
)

echo [*] Python bagimliliklari kontrol ediliyor...
python -m pip install -q -r "discord_bot\requirements.txt"
if %errorlevel% neq 0 (
    echo [-] UYARI: requirements kurulumu basarisiz, mevcut ortamla devam ediliyor...
)

echo [*] Discord Bot baslatiliyor...
python discord_bot/bot.py
set EXITCODE=%errorlevel%
echo [*] Bot kapandi (kod: %EXITCODE%)
pause
exit /b %EXITCODE%
