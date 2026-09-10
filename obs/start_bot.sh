#!/bin/bash
# SuS Cracker Discord Bot — Ubuntu/VDS Launcher
# Kullanim: chmod +x start_bot.sh && ./start_bot.sh
cd "$(dirname "$0")"

echo "========================================================"
echo "       SuS Cracker - Discord Bytecode Suite Bot v4.0"
echo "                  (Ubuntu / Debian)"
echo "========================================================"
echo ""

fail() { echo "[-] HATA: $1"; exit 1; }

command -v java >/dev/null 2>&1 || fail "'java' bulunamadi! Kur: sudo apt update && sudo apt install -y openjdk-21-jdk"
command -v javac >/dev/null 2>&1 || fail "'javac' bulunamadi (sadece JRE kurulmus)! Kur: sudo apt install -y openjdk-21-jdk"
command -v python3 >/dev/null 2>&1 || fail "'python3' bulunamadi! Kur: sudo apt install -y python3 python3-pip python3-venv"

echo "[*] Java:"; java -version 2>&1 | head -n 2
echo "[*] Python: $(python3 --version 2>&1)"
echo ""

# 1) Kutuphaneler VDS'ye gelmis mi? (.gitignore eskiden *.jar'i engelliyordu)
if ! ls discord_bot/engine/lib/*.jar >/dev/null 2>&1; then
    echo "[-] HATA: discord_bot/engine/lib/ bos! Kutuphaneler eksik:"
    echo "    asm-9.7.jar, asm-tree-9.7.jar, asm-commons-9.7.jar, asm-util-9.7.jar, cfr-0.152.jar, vineflower.jar"
    echo "    Cozum: Windows'tan scp ile at:"
    echo "    scp -i my-server-key.pem -r \"discord_bot/engine/lib\" ubuntu@SUNUCU_IP:~/obs/discord_bot/engine/"
    fail "lib/*.jar yok"
fi
echo "[+] Kutuphaneler tamam: $(ls discord_bot/engine/lib/*.jar | wc -l) adet jar"

# 2) Motoru derle (her baslatmada tazele — bin/ git'e girmez)
mkdir -p "discord_bot/bin" "discord_bot/temp" "discord_bot/output" "discord_bot/data"
echo "[*] Bytecode motoru derleniyor..."
if ! javac -encoding UTF-8 -cp "discord_bot/engine/lib/*" -d "discord_bot/bin" "discord_bot/engine/src/sus/cracker/SusBytecodeEngine.java"; then
    fail "Java motoru derlenemedi! JAVA_HOME=$JAVA_HOME"
fi
[ -f "discord_bot/bin/sus/cracker/SusBytecodeEngine.class" ] || fail "SusBytecodeEngine.class olusmadi!"
echo "[+] Motor derlendi."

# 3) Motor smoke-test (ClassNotFound'u bota girmeden yakala)
if ! java -cp "discord_bot/bin:discord_bot/engine/lib/*" sus.cracker.SusBytecodeEngine >/dev/null 2>&1; then
    echo "[!] UYARI: motor smoke-test basarisiz, log:"
    java -cp "discord_bot/bin:discord_bot/engine/lib/*" sus.cracker.SusBytecodeEngine 2>&1 | head -n 10
    fail "Motor calismiyor (yukaridaki loga bak)"
fi
echo "[+] Motor smoke-test OK."

# 4) .env kontrol
if [ ! -f "discord_bot/.env" ]; then
    echo "[-] HATA: discord_bot/.env yok!"
    echo "    cp discord_bot/.env.example discord_bot/.env && nano discord_bot/.env"
    fail ".env eksik"
fi
grep -q "YOUR_BOT_TOKEN_HERE" discord_bot/.env 2>/dev/null && fail ".env icinde hala YOUR_BOT_TOKEN_HERE yaziyor! Gercek token gir."

# 5) Python bagimliliklari (Ubuntu 24.04 externally-managed fix)
echo "[*] Python bagimliliklari kuruluyor..."
if ! python3 -m pip install -q -r "discord_bot/requirements.txt" 2>/dev/null; then
    echo "[*] pip --break-system-packages ile tekrar deneniyor..."
    python3 -m pip install -q --break-system-packages -r "discord_bot/requirements.txt" || echo "[-] UYARI: pip kurulumu basarisiz, mevcut ortamla devam..."
else
    echo "[+] Python paketleri OK."
fi

echo ""
echo "[*] Discord Bot baslatiliyor... (durdurmak icin CTRL+C)"
exec python3 discord_bot/bot.py
