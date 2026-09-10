import os
import sys
import asyncio
import uuid
import json
import random
import re
import time
import datetime
from pathlib import Path
from urllib.parse import urlparse
import discord
from discord import app_commands
from discord.ext import commands
from dotenv import load_dotenv

# ─── Utility Modülleri ────────────────────────
try:
    from data.database import (
        init_db, get_user_xp, add_user_xp,
        get_leaderboard_data, get_user_rank_position,
        get_afk, set_afk, remove_afk,
        add_warn, get_warns, clear_warns,
        add_ticket, get_ticket, remove_ticket,
        check_and_increment_quota
    )
    from utils.rank_card import create_rank_card
    from utils.progress import LiveProgressTracker
    from utils.vip import is_user_vip, verify_user_quota
    DB_AVAILABLE = True
except ImportError as _ie:
    DB_AVAILABLE = False
    print(f"[!] Utility import hatası: {_ie} — JSON fallback kullanılıyor")

# ──────────────────────────────────────────────
# CONFIG & ENV PARSER
# ──────────────────────────────────────────────
load_dotenv()

def get_env_int(key: str, default: int = 0) -> int:
    val = os.getenv(key)
    if not val:
        return default
    try:
        clean = val.split('#')[0].strip()
        return int(clean) if clean else default
    except Exception:
        return default

def get_env_color(key: str, default: int = 0x9B59B6) -> int:
    val = os.getenv(key)
    if not val:
        return default
    try:
        clean = val.split('#')[0].strip()
        return int(clean, 16) if clean.startswith("0x") else int(clean)
    except Exception:
        return default

TOKEN                  = os.getenv("DISCORD_BOT_TOKEN", "YOUR_BOT_TOKEN_HERE")
GUILD_ID               = get_env_int("GUILD_ID", 0)
MOD_LOG_CHANNEL        = get_env_int("MOD_LOG_CHANNEL", 0)
WELCOME_CHANNEL        = get_env_int("WELCOME_CHANNEL", 0)
TICKET_CATEGORY        = get_env_int("TICKET_CATEGORY", 0)
TICKET_LOG             = get_env_int("TICKET_LOG", 0)
VERIFY_ROLE            = get_env_int("VERIFY_ROLE", 0)
AUTO_ROLE              = get_env_int("AUTO_ROLE", 0)
STARBOARD_CH           = get_env_int("STARBOARD_CHANNEL", 0)
STAR_THRESHOLD         = get_env_int("STAR_THRESHOLD", 3)

# ─── Gelişmiş Ayarlar ─────────────────────────
MAX_FILE_SIZE_MB       = get_env_int("MAX_FILE_SIZE_MB", 50)
ENGINE_TIMEOUT_SECONDS = get_env_int("ENGINE_TIMEOUT_SECONDS", 180)
MAX_CONCURRENT_TASKS   = max(1, get_env_int("MAX_CONCURRENT_TASKS", 2))
DEFAULT_DEOBF_ENGINE   = os.getenv("DEFAULT_DEOBF_ENGINE", "auto").strip().lower()
DEFAULT_OBF_PRESET     = os.getenv("DEFAULT_OBF_PRESET", "aggressive").strip().lower()
STATUS_ACTIVITY        = os.getenv("STATUS_ACTIVITY", "⚡ /help | SuS Suite v4.0").strip()
LOG_LEVEL              = os.getenv("LOG_LEVEL", "INFO").strip()

BRAND_COLOR   = get_env_color("BRAND_COLOR", 0x9B59B6)
FOOTER_TEXT   = "⚡ SuS Cracker Suite v4.1 | sus-cracker.dev"
DANGER_COLOR  = 0xE74C3C
SUCCESS_COLOR = 0x2ECC71
INFO_COLOR    = 0x3498DB
WARNING_COLOR = 0xF39C12

BASE_DIR   = Path(__file__).parent.resolve()
ENGINE_BIN = BASE_DIR / "bin"
ENGINE_LIB = BASE_DIR / "engine" / "lib"
TEMP_DIR   = BASE_DIR / "temp"
OUTPUT_DIR = BASE_DIR / "output"
DATA_DIR   = BASE_DIR / "data"

for _d in [TEMP_DIR, OUTPUT_DIR, DATA_DIR]:
    _d.mkdir(exist_ok=True)

TASK_SEMAPHORE = asyncio.Semaphore(MAX_CONCURRENT_TASKS)

# ──────────────────────────────────────────────
# ATOMIC DATA HELPERS
# ──────────────────────────────────────────────
def load_json(name: str) -> dict:
    p = DATA_DIR / f"{name}.json"
    if p.exists():
        try:
            with open(p, encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_json(name: str, data: dict):
    tmp_path = DATA_DIR / f"{name}.tmp"
    final_path = DATA_DIR / f"{name}.json"
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        tmp_path.replace(final_path)
    except Exception as ex:
        print(f"[-] save_json error ({name}): {ex}")

# ──────────────────────────────────────────────
# BOT SETUP
# ──────────────────────────────────────────────
intents = discord.Intents.all()
bot = commands.Bot(command_prefix="!", intents=intents)
BOT_START_TIME = time.time()

def mk_embed(title: str, desc: str = "", color: int = BRAND_COLOR, *, footer: bool = True) -> discord.Embed:
    e = discord.Embed(title=title, description=desc, color=color,
                      timestamp=discord.utils.utcnow())
    if footer:
        e.set_footer(text=FOOTER_TEXT)
    return e

def get_cp() -> str:
    sep = ";" if sys.platform.startswith("win") else ":"
    return f"{ENGINE_BIN}{sep}{ENGINE_LIB}/*"


def ensure_engine() -> bool:
    """Bot acilirken motorun derli oldugunu dogrula, yoksa otomatik derlemeyi dene.

    Ubuntu'da en sik gorulen hata: bin/ derlenmemis -> ClassNotFound.
    Bu fonksiyon bota girmeden sorunu acikca soyler ve mumkunse cozer.
    """
    import shutil
    import subprocess

    engine_class = ENGINE_BIN / "sus" / "cracker" / "SusBytecodeEngine.class"
    engine_src = BASE_DIR / "engine" / "src" / "sus" / "cracker" / "SusBytecodeEngine.java"
    if engine_class.exists():
        return True

    print("=" * 62)
    print("[!] Java motoru derlenmemis: bin/sus/cracker/SusBytecodeEngine.class yok")
    lib_jars = list(ENGINE_LIB.glob("*.jar")) if ENGINE_LIB.exists() else []
    if not lib_jars:
        print("[!] engine/lib/*.jar BOS! Kutuphaneler VDS'ye gelmemis.")
        print("[!] .gitignore'da *.jar engeli vardi — guncel repoyu cekip lib'leri scp ile at:")
        print("[!]   scp -i my-server-key.pem -r discord_bot/engine/lib ubuntu@SUNUCU_IP:~/obs/discord_bot/engine/")
        print("=" * 62)
        return False
    javac = shutil.which("javac")
    if not javac:
        print("[!] 'javac' yok (sadece JRE kurulu). Kur: sudo apt install -y openjdk-21-jdk")
        print("=" * 62)
        return False
    print("[*] Motor otomatik derleniyor...")
    try:
        ENGINE_BIN.mkdir(parents=True, exist_ok=True)
        sep = ";" if sys.platform.startswith("win") else ":"
        cp = f"{ENGINE_LIB}{sep}*"
        r = subprocess.run(
            [javac, "-encoding", "UTF-8", "-cp", cp, "-d", str(ENGINE_BIN), str(engine_src)],
            capture_output=True, text=True, timeout=120,
        )
        if r.returncode == 0 and engine_class.exists():
            print("[+] Motor otomatik derlendi.")
            return True
        print(f"[-] javac basarisiz (kod {r.returncode}):")
        print((r.stdout or "")[-1500:])
        print((r.stderr or "")[-1500:])
        print("[*] Elle derle: javac -encoding UTF-8 -cp \"discord_bot/engine/lib/*\" -d discord_bot/bin discord_bot/engine/src/sus/cracker/SusBytecodeEngine.java  (Linux)")
        print("=" * 62)
        return False
    except Exception as ex:
        print(f"[-] Otomatik derleme hatasi: {ex}")
        print("=" * 62)
        return False


# Motoru acilista dogrula — eksikse Discord'a baglanmadan net hata ver
ENGINE_OK = ensure_engine()
if not ENGINE_OK:
    print("[!] Bot baslatildi ama Java motoru HAZIR DEGIL — /jar* komutlari ClassNotFound verecek.")

async def run_engine(mode: str, inp, out=None, *extras):
    cmd = ["java", "-cp", get_cp(), "sus.cracker.SusBytecodeEngine", mode, str(inp)]
    if out:   cmd.append(str(out))
    for ext in extras:
        if ext is not None:
            cmd.append(str(ext))

    # Motor yoksa Java'yi bosuna calistirip ClassNotFound ile ugrasma
    if not (ENGINE_BIN / "sus" / "cracker" / "SusBytecodeEngine.class").exists():
        return -1, "", ("Java motoru derlenmemis (SusBytecodeEngine.class yok). "
                        "Cozum: ./start_bot.sh ile baslat veya "
                        "javac -encoding UTF-8 -cp \"discord_bot/engine/lib/*\" -d discord_bot/bin "
                        "discord_bot/engine/src/sus/cracker/SusBytecodeEngine.java")

    async with TASK_SEMAPHORE:
        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
        except FileNotFoundError:
            return -1, "", "Sistemde 'java' bulunamadi! Kur: sudo apt install -y openjdk-21-jdk"
        try:
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=float(ENGINE_TIMEOUT_SECONDS))
            out_s, err_s = stdout.decode(errors="replace"), stderr.decode(errors="replace")
            if proc.returncode != 0 and ("ClassNotFoundException" in err_s or "Could not find or load main class" in err_s):
                err_s += ("\n[Cozum] Motor classpath hatasi: ./start_bot.sh ile baslat (motoru derler). "
                          "Elle: javac -encoding UTF-8 -cp \"discord_bot/engine/lib/*\" -d discord_bot/bin "
                          "discord_bot/engine/src/sus/cracker/SusBytecodeEngine.java")
            return proc.returncode, out_s, err_s
        except asyncio.TimeoutError:
            try:
                proc.kill()
            except Exception:
                pass
            try:
                await asyncio.wait_for(proc.communicate(), timeout=10)
            except Exception:
                pass
            try:
                await proc.wait()
            except Exception:
                pass
            return -1, "", f"İşlem zaman aşımına uğradı ({ENGINE_TIMEOUT_SECONDS} sn)."

# ──────────────────────────────────────────────
# XP & AFK COOLDOWNS
# ──────────────────────────────────────────────
XP_COOLDOWN: dict[str, float] = {}

# ══════════════════════════════════════════════
# PERSISTENT VIEWS
# ══════════════════════════════════════════════
class VerifyButton(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="✅ Doğrula", style=discord.ButtonStyle.success, custom_id="sus_verify_btn")
    async def verify(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.guild is None:
            return await interaction.response.send_message(
                "❌ Bu buton sadece sunucu içinde kullanılabilir.", ephemeral=True)
        if not VERIFY_ROLE and not get_guild_cfg(interaction.guild.id).get("verify_role"):
            return await interaction.response.send_message(
                "❌ VERIFY_ROLE ayarlanmamış. `/setup` çalıştır veya .env dosyasına ekle.", ephemeral=True)
        role = interaction.guild.get_role(eff_verify_role(interaction.guild))
        if not role:
            return await interaction.response.send_message("❌ Doğrulama rolü bulunamadı.", ephemeral=True)
        member = interaction.user
        if not isinstance(member, discord.Member):
            return await interaction.response.send_message("❌ Üye bilgisi alınamadı.", ephemeral=True)
        if role in member.roles:
            return await interaction.response.send_message("✅ Zaten doğrulanmışsın!", ephemeral=True)
        try:
            await member.add_roles(role, reason="Verify button")
            await interaction.response.send_message(
                f"🎉 Doğrulandın! **{role.name}** rolü verildi.", ephemeral=True)
        except discord.Forbidden:
            try:
                await interaction.response.send_message("❌ Bot bu rolü verme yetkisine sahip değil.", ephemeral=True)
            except Exception:
                pass
        except (discord.NotFound, discord.HTTPException):
            try:
                if not interaction.response.is_done():
                    await interaction.response.send_message("❌ Doğrulama sırasında bir hata oluştu.", ephemeral=True)
                else:
                    await interaction.followup.send("❌ Doğrulama sırasında bir hata oluştu.", ephemeral=True)
            except Exception:
                pass


class TicketCategorySelect(discord.ui.Select):
    def __init__(self):
        options = [
            discord.SelectOption(label="Teknik Destek", description="Mod, kurulum veya bot ile ilgili genel teknik yardım", emoji="🛠️", value="destek"),
            discord.SelectOption(label="Crack Talebi", description="Özel mod/JAR crack ve analiz talepleri", emoji="🔓", value="crack"),
            discord.SelectOption(label="Lisans Satın Alım", description="SuS Suite VIP, Bot ve Özel Lisans işlemleri", emoji="💎", value="lisans"),
        ]
        super().__init__(placeholder="Destek kategorisi seçin...", min_values=1, max_values=1, options=options)

    async def callback(self, interaction: discord.Interaction):
        if interaction.guild is None:
            return await interaction.response.send_message(
                "❌ Ticket sistemi sadece sunucu içinde kullanılabilir.", ephemeral=True)
        category_choice = self.values[0]
        cat_info = {
            "destek": ("destek", "🛠️ Teknik Destek", 0x3498DB),
            "crack":  ("crack",  "🔓 Crack Talebi",   0xE74C3C),
            "lisans": ("lisans", "💎 Lisans Satın Alım", 0x9B59B6),
        }
        slug, title, col = cat_info.get(category_choice, ("ticket", "🎫 Genel Destek", BRAND_COLOR))

        guild = interaction.guild
        cat_id = eff_ticket_category(guild)
        category = guild.get_channel(cat_id) if cat_id else None
        if category is not None and not isinstance(category, discord.CategoryChannel):
            category = None
        safe_name = "".join(c for c in interaction.user.name.lower() if c.isalnum() or c == "-")[:15]
        ch_name = f"t-{slug}-{safe_name}"

        existing = discord.utils.get(guild.text_channels, name=ch_name)
        if existing:
            return await interaction.response.send_message(
                f"❌ Zaten bu kategoride açık bir ticketin var: {existing.mention}", ephemeral=True)

        await interaction.response.defer(ephemeral=True)

        staff_role = None
        try:
            sr_id = eff_staff_role(guild)
            if sr_id:
                staff_role = guild.get_role(sr_id)
        except Exception:
            staff_role = None
        overwrites = {
            guild.default_role: discord.PermissionOverwrite(view_channel=False),
            interaction.user:   discord.PermissionOverwrite(view_channel=True, send_messages=True, read_message_history=True),
            guild.me:           discord.PermissionOverwrite(view_channel=True, send_messages=True, manage_channels=True),
        }
        if staff_role is not None:
            overwrites[staff_role] = discord.PermissionOverwrite(view_channel=True, send_messages=True, read_message_history=True)
        try:
            ch = await guild.create_text_channel(
                ch_name, category=category, overwrites=overwrites,
                topic=f"Kategori: {title} | {interaction.user} ({interaction.user.id})")
        except discord.Forbidden:
            return await interaction.followup.send("❌ Bot kanal oluşturma yetkisine sahip değil.", ephemeral=True)
        except discord.HTTPException:
            return await interaction.followup.send("❌ Kanal oluşturulamadı (isim çakışması olabilir, tekrar dene).", ephemeral=True)

        e = mk_embed(f"{title} — Ticket Açıldı",
            f"Merhaba {interaction.user.mention}! Destek ekibimiz seninle ilgilenecektir.\n\n"
            f"📌 **Seçilen Kategori:** `{title}`\n"
            "Lütfen talebini, ilgili dosyaları veya ekran görüntülerini detaylıca yaz.\n"
            "Ticket'ı kapatmak istediğinde aşağıdaki butona basabilirsin.", col)
        await ch.send(content=interaction.user.mention, embed=e, view=CloseTicketView())
        # Ticket'a özel ses kanalı (sessizce, hata olsa bile ticket çalışır)
        try:
            member = interaction.user if isinstance(interaction.user, discord.Member) else guild.get_member(interaction.user.id)
            if member is not None:
                await _ticket_voice_create(guild, ch, member, staff_role)
        except Exception:
            pass
        await interaction.followup.send(f"✅ Ticket açıldı: {ch.mention}", ephemeral=True)

        if DB_AVAILABLE:
            try:
                await add_ticket(str(ch.id), str(interaction.user.id), title)
            except Exception:
                pass
        tdata = load_json("tickets")
        tdata[str(ch.id)] = {"user": interaction.user.id, "category": title, "opened": time.time()}
        save_json("tickets", tdata)


class TicketCategoryView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=120)
        self.add_item(TicketCategorySelect())


class TicketView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="🎫 Ticket Aç", style=discord.ButtonStyle.primary, custom_id="sus_open_ticket")
    async def open_ticket(self, interaction: discord.Interaction, button: discord.ui.Button):
        e = mk_embed("🎫 Destek Kategorisi Seçin",
            "Lütfen açmak istediğiniz bilet türünü aşağıdaki menüden seçin:\n\n"
            "🛠️ **Teknik Destek:** Genel mod, kurulum veya motor yardımı\n"
            "🔓 **Crack Talebi:** Özel JAR deobfuscate ve crack istekleri\n"
            "💎 **Lisans Satın Alım:** VIP erişim, KeyAuth ve lisans hizmetleri", BRAND_COLOR)
        await interaction.response.send_message(embed=e, view=TicketCategoryView(), ephemeral=True)



class CloseTicketView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="🔒 Ticket'ı Kapat", style=discord.ButtonStyle.danger, custom_id="sus_close_ticket")
    async def close_ticket(self, interaction: discord.Interaction, button: discord.ui.Button):
        ch = interaction.channel
        if ch is None or not hasattr(ch, "name") or not (ch.name.startswith("ticket-") or ch.name.startswith("t-")):
            return await interaction.response.send_message("❌ Bu bir ticket kanalı değil.", ephemeral=True)

        await interaction.response.defer()
        await interaction.followup.send("🔒 Ticket 5 saniye içinde kapatılıyor...")

        transcript_lines = []
        try:
            async for msg in ch.history(limit=300, oldest_first=True):
                ts = msg.created_at.strftime("%d.%m.%Y %H:%M")
                content = redact_urls(msg.content or "")[:500]
                transcript_lines.append(f"[{ts}] {msg.author} ({msg.author.id}): {content}")
        except Exception:
            pass

        txt = "\n".join(transcript_lines) or "(Mesaj bulunamadı)"

        log_id = eff_ticket_log(interaction.guild) if interaction.guild is not None else 0
        if log_id and interaction.guild is not None:
            log_ch = interaction.guild.get_channel(log_id)
            if log_ch:
                f_path = TEMP_DIR / f"transcript_{ch.id}_{uuid.uuid4().hex[:6]}.txt"
                try:
                    f_path.write_text(txt, encoding="utf-8")
                    log_e = mk_embed(
                        "📋 Ticket Transkripti",
                        f"**Kanal:** {ch.name}\n**Kapatan:** {interaction.user.mention}\n"
                        f"**Kullanıcı:** <@{load_json('tickets').get(str(ch.id), {}).get('user', '?')}>",
                        INFO_COLOR)
                    dfile = discord.File(f_path, filename=f"transcript_{ch.name}.txt")
                    try:
                        await log_ch.send(embed=log_e, file=dfile)
                    finally:
                        close_discord_files([dfile])
                except Exception:
                    pass
                finally:
                    safe_unlink(f_path)

        await asyncio.sleep(5)
        try:
            if interaction.guild is not None:
                try:
                    await _ticket_voice_delete(interaction.guild, ch.id)
                except Exception:
                    pass
            await ch.delete(reason=f"Ticket kapatıldı - {interaction.user}")
        except discord.NotFound:
            pass

# ══════════════════════════════════════════════
# EVENTS
# ══════════════════════════════════════════════
@bot.event
async def on_ready():
    print(f"[+] {bot.user} ({bot.user.id}) online | discord.py {discord.__version__}")
    bot.add_view(VerifyButton())
    bot.add_view(TicketView())
    bot.add_view(CloseTicketView())

    # SQLite veritabanını başlat
    if DB_AVAILABLE:
        try:
            await init_db()
            print("[+] SQLite database initialized (WAL mode)")
        except Exception as exc:
            print(f"[-] DB init error: {exc}")

    try:
        await bot.change_presence(activity=discord.Activity(type=discord.ActivityType.listening, name=STATUS_ACTIVITY))
    except Exception as exc:
        print(f"[-] Activity error: {exc}")

    try:
        synced = await bot.tree.sync()
        print(f"[+] {len(synced)} slash commands synced")
    except Exception as exc:
        print(f"[-] Sync error: {exc}")

# ──────────────────────────────────────────────
# MEMBER JOIN / LEAVE
# ──────────────────────────────────────────────
@bot.event
async def on_member_join(member: discord.Member):
    ar = eff_auto_role(member.guild)
    if ar:
        role = member.guild.get_role(ar)
        if role:
            try:
                await member.add_roles(role, reason="Auto-Role on join")
            except discord.Forbidden:
                pass

    if WELCOME_CHANNEL or get_guild_cfg(member.guild.id).get("welcome"):
        w_id = eff_welcome(member.guild)
        ch = member.guild.get_channel(w_id) if w_id else None
        if ch and isinstance(ch, discord.TextChannel):
            e = mk_embed(
                f"👋 Hoş Geldin, {member.display_name}!",
                f"**{member.guild.name}** sunucusuna katıldın!\n"
                f"Sen sunucunun **{member.guild.member_count}.** üyesisin.\n\n"
                "Kuralları oku ve doğrulama yap! 🎉",
                SUCCESS_COLOR)
            e.set_thumbnail(url=member.display_avatar.url)
            await ch.send(embed=e)


@bot.event
async def on_member_remove(member: discord.Member):
    w_id = eff_welcome(member.guild)
    if w_id:
        ch = member.guild.get_channel(w_id)
        if ch and isinstance(ch, discord.TextChannel):
            e = mk_embed(
                f"👋 Güle Güle, {member.display_name}!",
                f"**{member.display_name}** sunucudan ayrıldı. Toplam üye: **{member.guild.member_count}**",
                DANGER_COLOR)
            e.set_thumbnail(url=member.display_avatar.url)
            await ch.send(embed=e)

# ──────────────────────────────────────────────
# STARBOARD
# ──────────────────────────────────────────────
@bot.event
async def on_reaction_add(reaction: discord.Reaction, user: discord.User):
    if not reaction.message.guild or user.bot:
        return
    if str(reaction.emoji) != "⭐":
        return
    if reaction.count < STAR_THRESHOLD:
        return
    sb_id = eff_starboard(reaction.message.guild)
    if not sb_id:
        return

    ch = reaction.message.guild.get_channel(sb_id)
    if not ch:
        return

    starred = load_json("starboard")
    msg_id  = str(reaction.message.id)
    if msg_id in starred:
        return

    e = mk_embed(
        f"⭐ {reaction.count} Yıldız!",
        reaction.message.content or "[Medya / Embed mesajı]",
        color=0xF1C40F)
    e.add_field(name="Kaynak", value=f"[Mesaja git]({reaction.message.jump_url})")
    e.set_author(name=reaction.message.author.display_name,
                 icon_url=reaction.message.author.display_avatar.url)
    if reaction.message.attachments:
        e.set_image(url=reaction.message.attachments[0].url)
    await ch.send(embed=e)

    starred[msg_id] = {"stars": reaction.count, "author": str(reaction.message.author)}
    save_json("starboard", starred)

# ──────────────────────────────────────────────
# MESSAGE EVENT (XP + AFK)
# ──────────────────────────────────────────────
@bot.event
async def on_message(message: discord.Message):
    if message.author.bot or not message.guild:
        return
    await bot.process_commands(message)

    uid = str(message.author.id)

    if DB_AVAILABLE:
        # ─── 1. AFK Check (DB) ────────────────────
        afk_info = await get_afk(uid)
        if afk_info:
            await remove_afk(uid)
            e = mk_embed("👋 Tekrar Hoş Geldin!",
                f"{message.author.mention}, AFK modundan çıktın.\n**Eski Sebep:** {afk_info['reason']}",
                SUCCESS_COLOR)
            await message.channel.send(embed=e, delete_after=6)

        # ─── 2. AFK Mention Check (DB) ────────────
        if message.mentions:
            for mentioned in message.mentions:
                info = await get_afk(str(mentioned.id))
                if info:
                    since = f"<t:{int(info['time'])}:R>"
                    e = mk_embed("💤 Kullanıcı AFK",
                        f"**{mentioned.display_name}** şu an AFK.\n**Sebep:** {info['reason']}\n**Süre:** {since}",
                        WARNING_COLOR)
                    await message.channel.send(embed=e, delete_after=8)
                    break

        # ─── 3. XP System (DB) ────────────────────
        now = time.time()
        if now - XP_COOLDOWN.get(uid, 0) >= 60:
            XP_COOLDOWN[uid] = now
            xp_amount = random.randint(15, 30)
            leveled_up, new_level, new_xp = await add_user_xp(uid, xp_amount)
            if leveled_up:
                e = mk_embed("🎉 Seviye Atladın!",
                    f"Tebrikler {message.author.mention}! Artık **Seviye {new_level}** oldun! 🚀",
                    0xF39C12)
                await message.channel.send(embed=e)
    else:
        # ─── Fallback: JSON tabanlı ────────────────
        afk_data = load_json("afk")
        if uid in afk_data:
            reason = afk_data.pop(uid).get("reason", "Belirtilmedi")
            save_json("afk", afk_data)
            e = mk_embed("👋 Tekrar Hoş Geldin!", f"{message.author.mention}, AFK modundan çıktın.\n**Eski Sebep:** {reason}", SUCCESS_COLOR)
            await message.channel.send(embed=e, delete_after=6)
        if message.mentions:
            afk_data = load_json("afk")
            for mentioned in message.mentions:
                m_id = str(mentioned.id)
                if m_id in afk_data:
                    info = afk_data[m_id]
                    since = f"<t:{int(info['time'])}:R>"
                    e = mk_embed("💤 Kullanıcı AFK", f"**{mentioned.display_name}** şu an AFK.\n**Sebep:** {info['reason']}\n**Süre:** {since}", WARNING_COLOR)
                    await message.channel.send(embed=e, delete_after=8)
                    break
        now = time.time()
        if now - XP_COOLDOWN.get(uid, 0) >= 60:
            XP_COOLDOWN[uid] = now
            xp_data = load_json("xp")
            user_data = xp_data.setdefault(uid, {"xp": 0, "level": 1})
            user_data["xp"] += random.randint(15, 30)
            needed = user_data["level"] * 100
            if user_data["xp"] >= needed:
                user_data["xp"] -= needed
                user_data["level"] += 1
                lvl = user_data["level"]
                e = mk_embed("🎉 Seviye Atladın!", f"Tebrikler {message.author.mention}! Artık **Seviye {lvl}** oldun! 🚀", 0xF39C12)
                await message.channel.send(embed=e)
            save_json("xp", xp_data)

# ══════════════════════════════════════════════
# SLASH COMMANDS
# ══════════════════════════════════════════════

# ─── 1. HELP COMMAND ──────────────────────────
class HelpSelect(discord.ui.Select):
    def __init__(self):
        options = [
            discord.SelectOption(label="Bytecode Güvenlik & Deobf Araçları", description="Obfuscator, Cracker, Scanner, Deobfuscator", emoji="🛡️", value="bytecode"),
            discord.SelectOption(label="Moderasyon Komutları", description="Ban, Kick, Mute, Warn, Clear, Unban", emoji="👮", value="mod"),
            discord.SelectOption(label="Sunucu & Bot Yönetimi", description="Settings, BotReload, Ticket, Verify, Giveaway", emoji="⚙️", value="admin"),
            discord.SelectOption(label="Kullanıcı & XP", description="Rank, Leaderboard, Userinfo, AFK, Poll", emoji="📈", value="user"),
        ]
        super().__init__(placeholder="Komut kategorisi seç...", min_values=1, max_values=1, options=options)

    async def callback(self, interaction: discord.Interaction):
        val = self.values[0]
        if val == "bytecode":
            e = mk_embed("🛡️ Bytecode Güvenlik & Deobf Komutları",
                "• `/jarobfuscator [preset] [rename]` – JAR dosyasını polimorfik şifreleme ve matematiksel invariantlarla şifrele.\n"
                "• `/jardeobfuscator [engine]` – Şifreli JAR'ı temizle + hem temiz JAR hem kaynak ZIP al.\n"
                "• `/jardeobfuscationsrc [engine]` – JAR'ın şifresini çözüp tüm .java kaynak kodlarını ve raporu ZIP olarak indir.\n"
                "• `/jarcracker [target]` – **43 metot** ile crack: Meteor Addon, Fabric Mod, Forge Mod, Lunar/Badlion/LabyMod veya Genel.\n"
                "• `/jarscanner` – JAR dosyasını RAT, Webhook Logger ve zararlı kodlar için derinlemesine tara.\n"
                "• `/jarclear [honeypot_url]` – JAR'daki Webhook, RAT ve Grabber kodlarını temizle, saldırgan Webhook'unu ifşa et ve tuzağa/honeypot'a yönlendir.\n"
                "• `/jarprotect [hwid] [expire_days] [alarm_webhook]` – Mod geliştiricileri için HWID kilidi, Süre Sınırı ve Güvenlik Kalkanı enjekte et.\n"
                "• `/jardiff [original_file] [modified_file]` – İki JAR arasındaki bytecode, sınıf ve yapısal farkları karşılaştırıp raporla.", BRAND_COLOR)
        elif val == "mod":
            e = mk_embed("👮 Moderasyon Komutları",
                "• `/ban [user] [sebep]` – Kullanıcıyı banla.\n"
                "• `/kick [user] [sebep]` – Kullanıcıyı at.\n"
                "• `/mute [user] [dakika] [sebep]` – Kullanıcıyı sustur.\n"
                "• `/unmute [user]` – Susturmayı kaldır.\n"
                "• `/warn [user] [sebep]` – Kullanıcıyı uyar.\n"
                "• `/warns [user]` – Uyarıları listele.\n"
                "• `/clearwarns [user]` – Uyarıları sil.\n"
                "• `/clear [sayı]` – Mesajları sil.\n"
                "• `/unban [user_id]` – Ban kaldır.\n"
                "• `/lock [kanal]` – Kanalı kilitle.\n"
                "• `/unlock [kanal]` – Kilidi aç.\n"
                "• `/slowmode [saniye]` – Yavaş mod.\n"
                "• `/nuke [kanal]` – Kanalı sıfırla.\n"
                "• `/vkick /vmute /vunmute /vmove` – Ses moderasyonu.", DANGER_COLOR)
        elif val == "admin":
            e = mk_embed("⚙️ Sunucu & Bot Yönetimi",
                "• `/setup [temizle]` – Public sunucu kurulumu (rol/kanal/ses/ticket/rules/eula/pricing).\n"
                "• `/settings` – Botun tüm yapılandırma ayarlarını ve durumunu görüntüle.\n"
                "• `/botreload` – Konfigürasyon ve veritabanını bota yeniden yükle.\n"
                "• `/ticket-panel` – Ticket destek panelini gönder.\n"
                "• `/ticketvoice [user]` – Ticketa özel ses kanalı aç.\n"
                "• `/verify-panel` – Doğrulama panelini gönder.\n"
                "• `/giveaway [dakika] [ödül] [kazanan]` – Çekiliş başlat.\n"
                "• `/announce [başlık] [mesaj]` – Duyuru yayınla.\n"
                "• `/rules /eula /pricing` – Bilgi panellerini gönder.\n"
                "• `/vname /vlimit /vlock /vunlock /vclaim` – Özel ses oda yönetimi.\n"
                "• `/products` – Ürün ve hizmetler paneli.", SUCCESS_COLOR)
        else:
            e = mk_embed("📈 Kullanıcı & Seviye Komutları",
                "• `/rank [user]` – Seviye ve XP durumunu gör.\n"
                "• `/leaderboard` – XP sıralaması.\n"
                "• `/afk [sebep]` – AFK moduna geç.\n"
                "• `/poll [soru] [seçenekler]` – Oylama başlat.\n"
                "• `/userinfo [user]` – Kullanıcı detayları.\n"
                "• `/serverinfo` – Sunucu istatistikleri.\n"
                "• `/health` – Bot ve sistem durumunu göster.\n"
                "• `/ping` – Gecikme ölç.", INFO_COLOR)
        await interaction.response.edit_message(embed=e)


class HelpView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=120)
        self.add_item(HelpSelect())


@bot.tree.command(name="help", description="❓ Bot komutları ve yardım menüsü.")
async def help_cmd(interaction: discord.Interaction):
    e = mk_embed("⚡ SuS Cracker Bot v4.1 — Yardım Menüsü",
        "Aşağıdaki açılır menüden kategorileri seçerek detaylı komut listesini görebilirsin!\n\n"
        "🛡️ **Bytecode:** Obfuscator / Cracker (43 metot) / Scanner / Deobf / Honeypot / JarProtect / JarDiff\n"
        "⚙️ **Yönetim:** Setup / Settings / Ticket+Ses / Verify / Announce / Giveaway\n"
        "👮 **Moderasyon:** Ban / Kick / Mute / Warn / Clear / Lock / Slowmode / Nuke / Ses\n"
        "🎧 **Ses:** ➕ lobiye gir → özel oda + /vname /vlimit /vlock /vclaim\n"
        "📈 **Kullanıcı:** Görsel Rank Kartı / Sayfalamalı Leaderboard / AFK / Poll", BRAND_COLOR)
    await interaction.response.send_message(embed=e, view=HelpView())

# ─── 2. AFK COMMAND ───────────────────────────
@bot.tree.command(name="afk", description="💤 AFK moduna geç.")
@app_commands.describe(sebep="AFK olma sebebi")
async def afk_cmd(interaction: discord.Interaction, sebep: str = "Belirtilmedi"):
    uid = str(interaction.user.id)
    if DB_AVAILABLE:
        await set_afk(uid, sebep)
    else:
        afk_data = load_json("afk")
        afk_data[uid] = {"reason": sebep, "time": time.time()}
        save_json("afk", afk_data)
    e = mk_embed("💤 AFK Modu", f"{interaction.user.mention} artık AFK!\n**Sebep:** {sebep}", WARNING_COLOR)
    await interaction.response.send_message(embed=e)

async def safe_defer(interaction: discord.Interaction, thinking: bool = True):
    try:
        if not interaction.response.is_done():
            await interaction.response.defer(thinking=thinking)
    except (discord.NotFound, discord.HTTPException):
        pass

async def safe_followup(interaction: discord.Interaction, *args, **kwargs):
    try:
        return await interaction.followup.send(*args, **kwargs)
    except (discord.NotFound, discord.HTTPException):
        try:
            if interaction.channel:
                return await interaction.channel.send(*args, **kwargs)
        except Exception:
            pass

def check_file_size(file: discord.Attachment) -> bool:
    max_bytes = MAX_FILE_SIZE_MB * 1024 * 1024
    return file.size <= max_bytes

def safe_unlink(path, retries: int = 3, delay: float = 0.2):
    """Windows WinError 32 (dosya kilitli) durumuna dayanıklı silme.
    discord.File handle'ı veya antivirus/Java kilidi açık kalmış olabilir,
    bu yüzden birkaç kez retry yapar, asla exception fırlatmaz.
    NOT: event-loop'u bloklamamak için delay kısa tutuldu (max ~0.6sn)."""
    try:
        p = Path(path)
    except Exception:
        return
    for attempt in range(retries):
        try:
            p.unlink(missing_ok=True)
            return
        except PermissionError:
            if attempt < retries - 1:
                time.sleep(delay)
                continue
            print(f"[!] safe_unlink: dosya kilitli, silinemedi: {p}")
            return
        except FileNotFoundError:
            return
        except OSError as ex:
            if attempt < retries - 1:
                time.sleep(delay)
                continue
            print(f"[!] safe_unlink OSError ({p}): {ex}")
            return

def close_discord_files(files):
    for f in files:
        try:
            f.close()
        except Exception:
            pass

_SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9._-]+")

def safe_filename(name: str, max_len: int = 50, default: str = "file.jar") -> str:
    """Attachment filename'den path traversal ve geçersiz karakterleri temizler.
    'a/b', '..\\', 'C:\\x', kontrol karakterleri -> güvenli flat isim."""
    try:
        base = Path(name or default).name
    except Exception:
        base = default
    base = _SAFE_NAME_RE.sub("_", base).strip("._") or default
    if len(base) > max_len:
        stem, dot, ext = base.rpartition(".")
        ext = f".{ext}" if dot else ""
        base = stem[: max_len - len(ext)] + ext
    if "." not in base:
        base += ".jar"
    return base

def assert_inside(path: Path, parent: Path) -> Path:
    """Symlink/traversal'e karşı resolve edip parent içinde olduğunu doğrula."""
    try:
        rp = Path(path).resolve()
        pp = Path(parent).resolve()
        if rp == pp or pp in rp.parents:
            return rp
    except Exception:
        pass
    raise ValueError(f"Güvensiz dosya yolu: {path}")

_URL_RE = re.compile(r"https?://\S+", re.IGNORECASE)

def redact_urls(text: str) -> str:
    """Log/embed'e basmadan önce URL/token sızıntısını maskeler."""
    if not text:
        return text
    return _URL_RE.sub("[redacted-url]", text)

def is_safe_http_url(url: str, max_len: int = 500) -> bool:
    try:
        if not url or len(url) > max_len:
            return False
        u = urlparse(url.strip())
        if u.scheme not in ("http", "https"):
            return False
        host = (u.hostname or "").lower()
        if not host:
            return False
        # SSRF koruması: localhost + cloud metadata + file/javascript şemaları zaten elendi
        if host in ("localhost", "127.0.0.1", "0.0.0.0", "::1",
                    "169.254.169.254", "213.0.0.0", "metadata.google.internal"):
            return False
        if host.endswith((".internal", ".local")):
            return False
        return True
    except Exception:
        return False

_HWID_RE = re.compile(r"^[A-Za-z0-9._-]{0,64}$")

def sanitize_hwid(hwid: str | None) -> str:
    h = (hwid or "").strip()
    if not h:
        return "NONE"
    if not _HWID_RE.match(h):
        return "NONE"
    return h

def _warn_field(w) -> tuple[str, str]:
    """get_warns tuple (id, reason, moderator, ts) veya JSON dict — ikisini de destekler."""
    if isinstance(w, (tuple, list)):
        reason = str(w[1]) if len(w) > 1 else "Belirtilmedi"
        mod = str(w[2]) if len(w) > 2 else "?"
        return reason, mod
    if isinstance(w, dict):
        reason = str(w.get("reason", w.get("sebep", "Belirtilmedi")))
        mod = str(w.get("moderator", w.get("yetkili", "?")))
        return reason, mod
    return "Belirtilmedi", "?"

# ─── 3. POLL COMMAND ───────────────────────────
@bot.tree.command(name="poll", description="📊 Hızlı oylama başlat.")
@app_commands.describe(soru="Oylama konusu", secenekler="Seçenekleri virgülle ayır (ör: Evet, Hayır)")
async def poll_cmd(interaction: discord.Interaction, soru: str, secenekler: str = "Evet, Hayır"):
    opts = [s.strip() for s in secenekler.split(",") if s.strip()][:10]
    if len(opts) < 2:
        return await interaction.response.send_message("❌ En az 2 seçenek yazmalısın!", ephemeral=True)

    emojis = ["1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"]
    desc_lines = [f"{emojis[i]} **{opt}**" for i, opt in enumerate(opts)]

    e = mk_embed(f"📊 OYLAMA: {soru}", "\n".join(desc_lines), INFO_COLOR)
    e.set_footer(text=f"Oylamayı başlatan: {interaction.user.display_name} | {FOOTER_TEXT}")

    await interaction.response.send_message("✅ Oylama gönderildi.", ephemeral=True)
    msg = await interaction.channel.send(embed=e)
    for i in range(len(opts)):
        await msg.add_reaction(emojis[i])

# ─── 4. HEALTH / SYSTEM STATUS ────────────────
@bot.tree.command(name="health", description="💻 Bot ve sistem sağlık durumunu göster.")
async def health_cmd(interaction: discord.Interaction):
    uptime_sec = int(time.time() - BOT_START_TIME)
    hours, remainder = divmod(uptime_sec, 3600)
    minutes, seconds = divmod(remainder, 60)
    uptime_str = f"{hours}h {minutes}m {seconds}s"

    temp_files = list(TEMP_DIR.glob("*"))
    output_files = list(OUTPUT_DIR.glob("*"))

    e = mk_embed("💻 Sistem & Bot Sağlık Raporu", color=SUCCESS_COLOR)
    e.add_field(name="⏱️ Uptime", value=uptime_str, inline=True)
    e.add_field(name="🏓 Ping", value=f"{round(bot.latency*1000)}ms", inline=True)
    e.add_field(name="🐍 Python", value=sys.version.split()[0], inline=True)
    e.add_field(name="📁 Geçici Dosyalar", value=f"Temp: {len(temp_files)} | Output: {len(output_files)}", inline=True)
    e.add_field(name="🏰 Sunucular", value=str(len(bot.guilds)), inline=True)
    try:
        _slots = TASK_SEMAPHORE._value
    except Exception:
        _slots = "?"
    e.add_field(name="⚙️ Java Engine", value=f"ASM 9.7 (Hazır, Slot: {_slots}/{MAX_CONCURRENT_TASKS})", inline=True)
    await interaction.response.send_message(embed=e)

# ─── 5. SETTINGS & BOTRELOAD COMMANDS ──────────
@bot.tree.command(name="settings", description="⚙️ Botun tüm yapılandırma ayarlarını ve durumunu görüntüle.")
@app_commands.default_permissions(manage_guild=True)
async def settings_cmd(interaction: discord.Interaction):
    guild = interaction.guild

    mod_ch = guild.get_channel(MOD_LOG_CHANNEL) if MOD_LOG_CHANNEL else None
    wel_ch = guild.get_channel(WELCOME_CHANNEL) if WELCOME_CHANNEL else None
    tck_ca = guild.get_channel(TICKET_CATEGORY) if TICKET_CATEGORY else None
    tck_lg = guild.get_channel(TICKET_LOG) if TICKET_LOG else None
    v_role = guild.get_role(VERIFY_ROLE) if VERIFY_ROLE else None
    a_role = guild.get_role(AUTO_ROLE) if AUTO_ROLE else None
    s_ch   = guild.get_channel(STARBOARD_CH) if STARBOARD_CH else None

    e = mk_embed("⚙️ SuS Cracker Bot — Yapılandırma Paneli", color=BRAND_COLOR)

    # Motor ayarları
    e.add_field(name="🛡️ Motor Ayarları",
        value=f"• **Maks Dosya Boyutu:** `{MAX_FILE_SIZE_MB} MB`\n"
              f"• **Zaman Aşımı:** `{ENGINE_TIMEOUT_SECONDS} sn`\n"
              f"• **Eşzamanlı Görev:** `{MAX_CONCURRENT_TASKS}`\n"

              f"• **Varsayılan Deobf Motoru:** `{DEFAULT_DEOBF_ENGINE}`\n"
              f"• **Varsayılan Obf Seviyesi:** `{DEFAULT_OBF_PRESET}`", inline=False)

    # Kanal ve roller
    e.add_field(name="📌 Kanal & Rol Bağlantıları",
        value=f"• **Mod Log:** {mod_ch.mention if mod_ch else '`Kapalı (0)`'}\n"
              f"• **Hoş Geldin:** {wel_ch.mention if wel_ch else '`Kapalı (0)`'}\n"
              f"• **Ticket Kategori:** {tck_ca.name if tck_ca else '`Kapalı (0)`'}\n"
              f"• **Ticket Log:** {tck_lg.mention if tck_lg else '`Kapalı (0)`'}\n"
              f"• **Starboard:** {s_ch.mention if s_ch else '`Kapalı (0)`'} (⭐ {STAR_THRESHOLD})\n"
              f"• **Doğrulama Rolü:** {v_role.mention if v_role else '`Kapalı (0)`'}\n"
              f"• **Oto Rol:** {a_role.mention if a_role else '`Kapalı (0)`'}", inline=False)

    e.add_field(name="💡 Ayarları Düzenleme",
        value="Tüm ayarlar `discord_bot/.env` dosyasından okunur. Değişiklik yaptıktan sonra `/botreload` komutuyla güncelleyebilirsiniz.", inline=False)

    await interaction.response.send_message(embed=e, ephemeral=True)


@bot.tree.command(name="botreload", description="🔄 Konfigürasyon ve verileri bota yeniden yükle.")
@app_commands.default_permissions(manage_guild=True)
async def botreload_cmd(interaction: discord.Interaction):
    global MAX_FILE_SIZE_MB, ENGINE_TIMEOUT_SECONDS, MAX_CONCURRENT_TASKS
    global DEFAULT_DEOBF_ENGINE, DEFAULT_OBF_PRESET, STATUS_ACTIVITY, BRAND_COLOR, LOG_LEVEL

    load_dotenv(override=True)
    MAX_FILE_SIZE_MB       = get_env_int("MAX_FILE_SIZE_MB", 50)
    ENGINE_TIMEOUT_SECONDS = get_env_int("ENGINE_TIMEOUT_SECONDS", 180)
    MAX_CONCURRENT_TASKS   = max(1, get_env_int("MAX_CONCURRENT_TASKS", 2))
    DEFAULT_DEOBF_ENGINE   = os.getenv("DEFAULT_DEOBF_ENGINE", "auto").strip().lower()
    DEFAULT_OBF_PRESET     = os.getenv("DEFAULT_OBF_PRESET", "aggressive").strip().lower()
    STATUS_ACTIVITY        = os.getenv("STATUS_ACTIVITY", "⚡ /help | SuS Suite v4.1").strip()
    LOG_LEVEL              = os.getenv("LOG_LEVEL", "INFO").strip()
    BRAND_COLOR            = get_env_color("BRAND_COLOR", 0x9B59B6)

    try:
        await bot.change_presence(activity=discord.Activity(type=discord.ActivityType.listening, name=STATUS_ACTIVITY))
    except Exception:
        pass

    e = mk_embed("🔄 Ayarlar Yeniden Yüklendi",
        f"✅ `.env` konfigürasyonu başarıyla güncellendi!\n\n"
        f"• **Maks Dosya:** `{MAX_FILE_SIZE_MB}MB`\n"
        f"• **Motor Zaman Aşımı:** `{ENGINE_TIMEOUT_SECONDS}sn`\n"
        f"• **Eşzamanlılık:** `{MAX_CONCURRENT_TASKS}`\n"
        f"• **Varsayılan Deobf:** `{DEFAULT_DEOBF_ENGINE}`\n"
        f"• **Varsayılan Obf:** `{DEFAULT_OBF_PRESET}`\n"
        f"• **Durum Metni:** `{STATUS_ACTIVITY}`", SUCCESS_COLOR)
    await interaction.response.send_message(embed=e, ephemeral=True)

# ─── 6. BYTECODE COMMANDS ──────────────────────

@bot.tree.command(name="jarobfuscator", description="🛡️ Minecraft mod/JAR dosyasını gelişmiş yöntemlerle obfuscate et.")
@app_commands.describe(
    file="Obfuscate edilecek .jar dosyası",
    preset="Obfuscation seviyesi: standard, aggressive veya extreme",
    rename_classes="Dahili sınıfları ve alanları görünmez Unicode ile gizle"
)
@app_commands.choices(preset=[
    app_commands.Choice(name="Standard (Temel XOR & Sabit Karıştırma)", value="standard"),
    app_commands.Choice(name="Aggressive (Polimorfik XOR + Opaque Predicates)", value="aggressive"),
    app_commands.Choice(name="Extreme (Maksimum Şifreleme + Tam İsim Gizleme)", value="extreme")
])
async def jarobfuscator(
    interaction: discord.Interaction,
    file: discord.Attachment,
    preset: app_commands.Choice[str] = None,
    rename_classes: bool = True
):
    if not file.filename.lower().endswith((".jar", ".zip")):
        return await interaction.response.send_message("❌ Geçerli bir `.jar` dosyası yükle!", ephemeral=True)

    # VIP Quota Check
    if DB_AVAILABLE:
        ok, msg = await verify_user_quota(interaction, file)
        if not ok:
            return await interaction.response.send_message(msg, ephemeral=True)
    elif not check_file_size(file):
        return await interaction.response.send_message(f"❌ Dosya çok büyük! Maksimum: `{MAX_FILE_SIZE_MB} MB`", ephemeral=True)

    await safe_defer(interaction, thinking=True)
    tid = str(uuid.uuid4())[:8]
    inp = TEMP_DIR  / f"{tid}_{safe_filename(file.filename)}"
    out = OUTPUT_DIR / f"{tid}_obfuscated_{safe_filename(file.filename)}"
    chosen_preset = preset.value if preset else DEFAULT_OBF_PRESET

    # Canlı progress tracker
    tracker = LiveProgressTracker(interaction, "🛡️ SuS Obfuscator — Şifreleniyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    start_time = time.time()
    try:
        await file.save(inp)
        ret, stdout, stderr = await run_engine("obfuscate", inp, out, chosen_preset, str(rename_classes).lower())
        elapsed = round(time.time() - start_time, 2)

        if tracker:
            await tracker.stop()

        if ret != 0 or not out.exists():
            log = redact_urls(stdout + stderr)[:1000]
            return await safe_followup(interaction, f"❌ Obfuscation hatası:\n```\n{log}\n```")

        out_size_mb = out.stat().st_size / (1024 * 1024)
        if out_size_mb > 24.5:
            return await safe_followup(interaction, f"⚠️ İşlem tamamlandı ancak çıktı dosyası (`{out_size_mb:.1f} MB`) Discord'un 25MB yükleme sınırını aşıyor!")

        e = mk_embed(f"🛡️ SuS Obfuscator v4.2 — Tamamlandı [{chosen_preset.upper()}]",
            f"**`{file.filename}`** başarıyla kırılamaz seviyede şifrelendi! (⏱️ `{elapsed}s`)\n\n"
            "🔒 **Uygulanan Üst Düzey Güvenlik Katmanları:**\n"
            "• **Çok Katmanlı Dinamik String Şifreleme** (İndeks bazlı polinomik XOR)\n"
            "• **Control Flow Flattening** (Doğrusal akış kırıcı durum makineleri)\n"
            "• **Değişken & Parametre İsimlerinin Yok Edilmesi** (`var_` kalıntıları temizlendi)\n"
            "• **Aritmetik & Bitwise Sabit Karıştırma** (Çok adımlı nötrleyici zincirler)\n"
            "• **Matematiksel Invariant & Sahte Hata Tuzakları** (Anti-Decompiler)\n"
            "• **Görünmez Unicode / Homoglyph Gizleme**\n"
            "• **Debug, LineNumber & SourceFile Bilgilerinin Sıfırlanması**", SUCCESS_COLOR)

        e.add_field(name="📊 Dosya Detayları",
            value=f"• Orijinal: `{file.size / 1024:.1f} KB`\n"
                  f"• Korunan: `{out.stat().st_size / 1024:.1f} KB`\n"
                  f"• Seviye: `{chosen_preset.upper()}`", inline=True)

        dfile = discord.File(out, filename=f"obfuscated_{safe_filename(file.filename)}")
        try:
            await safe_followup(interaction, embed=e, file=dfile)
        finally:
            close_discord_files([dfile])
    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        for _f in [inp, out]:
            safe_unlink(_f)



@bot.tree.command(name="jardeobfuscator", description="⚡ JAR'ı deobfuscate et + temiz JAR ve kaynak kodu al.")
@app_commands.describe(
    file="Deobfuscate edilecek .jar dosyası",
    engine="Decompiler motor tercihi (Auto / CFR / Vineflower)"
)
@app_commands.choices(engine=[
    app_commands.Choice(name="Auto (Önce CFR, hata durumunda Vineflower)", value="auto"),
    app_commands.Choice(name="Vineflower (Gelişmiş Decompiler & Member Renamer)", value="vineflower"),
    app_commands.Choice(name="CFR (Agresif Anti-Obf & Decompile Motoru)", value="cfr")
])
async def jardeobfuscator(
    interaction: discord.Interaction,
    file: discord.Attachment,
    engine: app_commands.Choice[str] = None
):
    if not file.filename.lower().endswith((".jar", ".zip")):
        return await interaction.response.send_message("❌ Geçerli bir `.jar` dosyası yükle!", ephemeral=True)

    if DB_AVAILABLE:
        ok, msg = await verify_user_quota(interaction, file)
        if not ok:
            return await interaction.response.send_message(msg, ephemeral=True)
    elif not check_file_size(file):
        return await interaction.response.send_message(f"❌ Dosya çok büyük! Maksimum limit: `{MAX_FILE_SIZE_MB} MB`", ephemeral=True)

    await safe_defer(interaction, thinking=True)
    tid   = str(uuid.uuid4())[:8]
    inp   = TEMP_DIR  / f"{tid}_{safe_filename(file.filename)}"
    clean = OUTPUT_DIR / f"{tid}_deobfuscated_{safe_filename(file.filename)}"
    src   = OUTPUT_DIR / f"{tid}_secure_source.zip"
    chosen_engine = engine.value if engine else DEFAULT_DEOBF_ENGINE

    tracker = LiveProgressTracker(interaction, "⚡ SuS Deobfuscator — Çözülüyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    start_time = time.time()
    try:
        await file.save(inp)
        ret, stdout, stderr = await run_engine("deobfuscate", inp, clean, src, chosen_engine)
        elapsed = round(time.time() - start_time, 2)

        if tracker:
            await tracker.stop()

        if ret != 0 or not clean.exists():
            log = redact_urls(stdout + stderr)[:1000]
            return await safe_followup(interaction, f"❌ DeObfuscation hatası:\n```\n{log}\n```")

        e = mk_embed("⚡ SuS Deobfuscator v4.1 — Tamamlandı",
            f"`{file.filename}` başarıyla çözüldü, optimize edildi ve kaynak kodları çıkarıldı! (⏱️ `{elapsed}s`)\n\n"
            "📦 **Teslim Edilen Paketler:**\n"
            f"• `deobfuscated_{file.filename}` – Temiz bytecode JAR\n"
            "• `source_code.zip` – Eksiksiz `.java` kaynak kodları + `DEOBFUSCATION_REPORT.md`", 0x1ABC9C)

        files_out = []
        if clean.exists() and clean.stat().st_size <= 24.5 * 1024 * 1024:
            files_out.append(discord.File(clean, filename=f"deobfuscated_{safe_filename(file.filename)}"))
        if src.exists() and src.stat().st_size <= 24.5 * 1024 * 1024:
            files_out.append(discord.File(src, filename=f"source_code_{safe_filename(file.filename).replace('.jar','')}.zip"))

        if not files_out:
            return await safe_followup(interaction, "⚠️ Çıktı dosyaları Discord'un 25MB yükleme sınırını aşıyor!")

        try:
            await safe_followup(interaction, embed=e, files=files_out)
        finally:
            close_discord_files(files_out)
    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        for _f in [inp, clean, src]:
            safe_unlink(_f)


@bot.tree.command(name="jardeobfuscationsrc", description="⚡ JAR modunun şifresini çözüp tüm .java kaynak kodlarını ve raporu ZIP olarak verir.")
@app_commands.describe(
    file="Şifresi çözülecek ve .java kaynak kodları çıkarılacak .jar dosyası",
    engine="Decompiler motor tercihi (Auto / CFR / Vineflower)"
)
@app_commands.choices(engine=[
    app_commands.Choice(name="Auto (Önce CFR, hata durumunda Vineflower)", value="auto"),
    app_commands.Choice(name="Vineflower (Gelişmiş Decompiler & Member Renamer)", value="vineflower"),
    app_commands.Choice(name="CFR (Agresif Anti-Obf & Decompile Motoru)", value="cfr")
])
async def jardeobfuscationsrc(
    interaction: discord.Interaction,
    file: discord.Attachment,
    engine: app_commands.Choice[str] = None
):
    if not file.filename.lower().endswith((".jar", ".zip")):
        return await interaction.response.send_message("❌ Geçerli bir `.jar` dosyası yükle!", ephemeral=True)

    if DB_AVAILABLE:
        ok, msg = await verify_user_quota(interaction, file)
        if not ok:
            return await interaction.response.send_message(msg, ephemeral=True)
    elif not check_file_size(file):
        return await interaction.response.send_message(f"❌ Dosya çok büyük! Maksimum limit: `{MAX_FILE_SIZE_MB} MB`", ephemeral=True)

    await safe_defer(interaction, thinking=True)
    tid   = str(uuid.uuid4())[:8]
    inp   = TEMP_DIR  / f"{tid}_{safe_filename(file.filename)}"
    clean = OUTPUT_DIR / f"{tid}_deobfuscated_{safe_filename(file.filename)}"
    src   = OUTPUT_DIR / f"{tid}_secure_source.zip"
    chosen_engine = engine.value if engine else DEFAULT_DEOBF_ENGINE

    tracker = LiveProgressTracker(interaction, "⚡ SuS DeobfSrc — Kaynak Kod Çıkarılıyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    start_time = time.time()
    try:
        await file.save(inp)
        ret, stdout, stderr = await run_engine("deobfuscate", inp, clean, src, chosen_engine)
        elapsed = round(time.time() - start_time, 2)

        if tracker:
            await tracker.stop()

        if ret != 0 or not src.exists():
            log = redact_urls(stdout + stderr)[:1000]
            return await safe_followup(interaction, f"❌ DeObfuscation / Kaynak Kod Çıkarma Hatası:\n```\n{log}\n```")

        if src.stat().st_size > 24.5 * 1024 * 1024:
            return await safe_followup(interaction, f"⚠️ Kaynak kodu ZIP arşivi (`{src.stat().st_size / (1024*1024):.1f} MB`) Discord'un 25MB sınırını aşıyor!")

        e = mk_embed("⚡ SuS Deobfuscator — Java Kaynak Kodu Arşivi",
            f"**`{file.filename}`** modunun şifrelemesi çözüldü ve asıl **.java** kaynak kodları çıkarıldı! (⏱️ `{elapsed}s`)\n\n"
            "📦 **Arşiv İçeriği:**\n"
            "• Tüm temizlenmiş `.java` sınıfları\n"
            "• `fabric.mod.json`, `mods.toml`, `assets/` ve `data/` varlıkları\n"
            "• `DEOBFUSCATION_REPORT.md` ayrıntılı analiz raporu", 0x1ABC9C)

        e.add_field(name="⚙️ Decompiler Tercihi", value=f"`{chosen_engine.upper()}`", inline=True)
        e.add_field(name="📦 Arşiv Boyutu", value=f"`{src.stat().st_size / 1024:.1f} KB`", inline=True)

        dfile = discord.File(src, filename=f"source_code_{safe_filename(file.filename).replace('.jar', '')}.zip")
        try:
            await safe_followup(interaction, embed=e, file=dfile)
        finally:
            close_discord_files([dfile])
    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        for _f in [inp, clean, src]:
            safe_unlink(_f)


@bot.tree.command(name="deobfuscationsrc", description="⚡ JAR modunun şifresini çözüp tüm .java kaynak kodlarını ve raporu ZIP olarak verir.")
@app_commands.describe(
    file="Şifresi çözülecek ve .java kaynak kodları çıkarılacak .jar dosyası",
    engine="Decompiler motor tercihi (Auto / CFR / Vineflower)"
)
@app_commands.choices(engine=[
    app_commands.Choice(name="Auto (Önce CFR, hata durumunda Vineflower)", value="auto"),
    app_commands.Choice(name="Vineflower (Gelişmiş Decompiler & Member Renamer)", value="vineflower"),
    app_commands.Choice(name="CFR (Agresif Anti-Obf & Decompile Motoru)", value="cfr")
])
async def deobfuscationsrc(
    interaction: discord.Interaction,
    file: discord.Attachment,
    engine: app_commands.Choice[str] = None
):
    await jardeobfuscationsrc.callback(interaction, file, engine)


@bot.tree.command(name="jarcracker", description="🔓 [SuS Cracker v4.1] JAR'ı 43 metotla crack et — Meteor/Fabric/Client/Forge destekli.")
@app_commands.describe(
    file="Crack edilecek .jar dosyası",
    target="Hedef platform türü (varsayılan: any)"
)
@app_commands.choices(target=[
    app_commands.Choice(name="🌠 Any (Genel — Tüm Tipler)",                   value="any"),
    app_commands.Choice(name="☄️ Meteor Client Addon",                         value="meteor"),
    app_commands.Choice(name="🧵 Fabric Mod (1.20.x / 1.21.x)",               value="fabric"),
    app_commands.Choice(name="🔧 Forge Mod (1.12 / 1.16 / 1.20+)",            value="forge"),
    app_commands.Choice(name="🌙 Lunar Client / Badlion / LabyMod / Impact",   value="client"),
])
async def jarcracker(
    interaction: discord.Interaction,
    file: discord.Attachment,
    target: app_commands.Choice[str] = None
):
    if not file.filename.lower().endswith((".jar", ".zip")):
        return await interaction.response.send_message("❌ Geçerli bir `.jar` dosyası yükle!", ephemeral=True)

    if DB_AVAILABLE:
        ok, msg = await verify_user_quota(interaction, file)
        if not ok:
            return await interaction.response.send_message(msg, ephemeral=True)
    elif not check_file_size(file):
        return await interaction.response.send_message(f"❌ Dosya çok büyük! Maksimum limit: `{MAX_FILE_SIZE_MB} MB`", ephemeral=True)

    chosen_target = target.value if target else "any"
    target_labels = {
        "any":    "🌠 Any (Genel)",
        "meteor": "☄️ Meteor Client Addon",
        "fabric": "🧵 Fabric Mod",
        "forge":  "🔧 Forge Mod",
        "client": "🌙 Lunar / Badlion / LabyMod / Impact",
    }
    target_label = target_labels.get(chosen_target, chosen_target.upper())

    await safe_defer(interaction, thinking=True)
    tid = str(uuid.uuid4())[:8]
    inp = TEMP_DIR  / f"{tid}_{safe_filename(file.filename)}"
    out = OUTPUT_DIR / f"{tid}_cracked_{safe_filename(file.filename)}"

    tracker = LiveProgressTracker(interaction, f"🔓 SuS Cracker — {target_label} İşleniyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    start_time = time.time()
    try:
        await file.save(inp)
        ret, stdout, stderr = await run_engine("crack", inp, out, chosen_target)
        elapsed = round(time.time() - start_time, 2)

        if tracker:
            await tracker.stop()

        if ret != 0 or not out.exists():
            log_err = redact_urls(stdout + stderr)[:1200]
            return await safe_followup(interaction, f"❌ Crack hatası:\n```\n{log_err}\n```")

        lines = [l for l in stdout.strip().splitlines() if l.strip().startswith("*") or l.strip().startswith("[")]
        patched_count_line = next((l for l in stdout.splitlines() if "Patches Applied" in l or "Methods Applied" in l), "")
        classes_line       = next((l for l in stdout.splitlines() if "Classes Scanned" in l), "")
        patched_num  = int(patched_count_line.split(":")[-1].strip()) if patched_count_line and patched_count_line.split(":")[-1].strip().isdigit() else len(lines)
        scanned_num  = int(classes_line.split(":")[-1].strip()) if classes_line and classes_line.split(":")[-1].strip().isdigit() else 0

        cats = {
            "🔐 Auth & License":    [],
            "🌐 Network & API":     [],
            "🛡️ Anti-Analysis":    [],
            "🎭 Cosmetic & VIP":    [],
            "⚙️ System & Module":   [],
            "🧬 Bytecode & Struct": [],
        }
        for l in lines:
            ll = l.lower()
            if any(k in ll for k in ["hwid", "keyauth", "license", "auth", "reflect", "static field", "heuristic", "threadlocal", "annotation", "class.forname"]):
                cats["🔐 Auth & License"].append(l.strip())
            elif any(k in ll for k in ["webhook", "http", "endpoint", "cloud", "ws", "ip", "grabber", "keyauth api"]):
                cats["🌐 Network & API"].append(l.strip())
            elif any(k in ll for k in ["anti-vm", "anti-debug", "stacktrace", "agent", "cert", "freeze", "runtimeexec"]):
                cats["🛡️ Anti-Analysis"].append(l.strip())
            elif any(k in ll for k in ["vip", "cosmetic", "premium", "baritone", "scape", "badge", "ranked", "subscriber"]):
                cats["🎭 Cosmetic & VIP"].append(l.strip())
            elif any(k in ll for k in ["module", "meteor", "exit", "config", "json", "timer", "scheduler", "fabric", "forge", "native", "hash"]):
                cats["⚙️ System & Module"].append(l.strip())
            else:
                cats["🧬 Bytecode & Struct"].append(l.strip())

        max_methods = 43
        pct = min(patched_num / max(max_methods, 1), 1.0)
        filled = int(pct * 22)
        bar = "▓" * filled + "░" * (22 - filled)
        bar_pct = int(pct * 100)

        out_size_kb = out.stat().st_size / 1024
        orig_size_kb = file.size / 1024
        over_limit = out.stat().st_size > 24.5 * 1024 * 1024

        e = mk_embed(
            "🔓 SuS Cracker v4.1 — Crack Tamamlandı!",
            f"**`{file.filename}`** başarıyla crack edildi! ⏱️ `{elapsed}s`\n"
            f"**Hedef Platform:** {target_label}\n"
            f"**Toplam Patch:** `{patched_num}` uygulama\n"
            f"**Tarama:** `{scanned_num if scanned_num else 'N/A'}` class\n\n"
            f"**İlerleme:**\n`{bar}` `{bar_pct}%` — `{patched_num}`/`{max_methods}` metot aktif",
            0xF1C40F
        )

        methods_table = (
            "```"
            "M01 HWID Spoof      │ M02 KeyAuth Strip  │ M03 Webhook NOP\n"
            "M04 Anti-VM         │ M05 Anti-Debug     │ M06 Meteor Lock\n"
            "M07 TimeBomb        │ M08 HTTP 200 Mock  │ M09 Hash Bypass\n"
            "M10 Native NOP      │ M11 VIP Unlock     │ M12 Cloud Auth\n"
            "M13 Exit Defuse     │ M14 IP Blacklist   │ M15 Discord Role\n"
            "M16 JavaAgent       │ M17 Cosmetics Unlk │ M18 Freeze NOP\n"
            "M19 CertPin         │ M20 Baritone Allow │ M21 Token Secure\n"
            "M22 RSA/AES Bypass  │ M23 WS Auth        │ M24 Config Key\n"
            "M25 Watermark Strip │ M26 Smart HWID     │ M27 Mixin Hook\n"
            "M28 Branch Reversal │ M29 JSON Meta Clean│ M30 KeyAuth Mock\n"
            "M31 Reflect Auth    │ M32 Static Flag    │ M33 Timer Kill\n"
            "M34 StackTrace Defz │ M35 Heuristic Auth │ M36 Meteor Ext\n"
            "M37 Client Premium  │ M38 Forge VerLock  │ M39 ProcBld Deep\n"
            "M40 forName Bypass  │ M41 ThreadLocal    │ M42 URL Redir\n"
            "M43 Annotation Strip"
            "```"
        )
        e.add_field(name="🗂️ Crack Metot Tablosu (43/43)", value=methods_table, inline=False)

        cat_lines = []
        for cat_name, cat_items in cats.items():
            if cat_items:
                cat_lines.append(f"**{cat_name}** — `{len(cat_items)}` patch")
        if cat_lines:
            e.add_field(name="📊 Kategori Dağılımı", value="\n".join(cat_lines), inline=True)

        e.add_field(name="📁 Dosya Bilgisi",
            value=f"• Orijinal: `{orig_size_kb:.1f} KB`\n"
                  f"• Crack'li: `{out_size_kb:.1f} KB`\n"
                  f"• Platform: `{chosen_target.upper()}`",
            inline=True)

        visible_patches = [redact_urls(l.strip().lstrip("* ")) for l in lines if l.strip()][-12:]
        if visible_patches:
            patch_log_str = "\n".join(visible_patches)
            if len(patch_log_str) > 900:
                patch_log_str = patch_log_str[:900] + "\n..."
            e.add_field(name="📋 Son Patch Logu", value=f"```\n{patch_log_str}\n```", inline=False)
        else:
            raw_log = redact_urls(stdout.strip() + stderr.strip())[-600:]
            if raw_log:
                e.add_field(name="📋 Engine Çıktısı", value=f"```\n{raw_log}\n```", inline=False)

        if over_limit:
            e.add_field(name="⚠️ Uyarı",
                value=f"Crack'li dosya (`{out.stat().st_size / (1024*1024):.1f} MB`) Discord'un 25MB limitini aşıyor!",
                inline=False)
            await safe_followup(interaction, embed=e)
        else:
            dfile = discord.File(out, filename=f"cracked_{safe_filename(file.filename)}")
            try:
                await safe_followup(interaction, embed=e, file=dfile)
            finally:
                close_discord_files([dfile])

    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        for _f in [inp, out]:
            safe_unlink(_f)


class CleanScanView(discord.ui.View):
    def __init__(self, filename: str):
        super().__init__(timeout=180)
        self.filename = filename

    @discord.ui.button(label="🧹 Dosyayı Zararlılardan Temizle", style=discord.ButtonStyle.danger, custom_id="sus_clean_scan_btn")
    async def clean_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        await interaction.response.send_message(
            f"💡 `{self.filename}` dosyasını temizlemek için `/jarclear` komutunu kullanarak dosyayı yükleyebilirsin!",
            ephemeral=True
        )


@bot.tree.command(name="jarscanner", description="🔍 JAR dosyasını malware/RAT/SilentNet için tara (SilentNet bulursa otomatik temizler).")
@app_commands.describe(file="Taranacak .jar dosyası")
async def jarscanner(interaction: discord.Interaction, file: discord.Attachment):
    if not file.filename.lower().endswith((".jar", ".zip")):
        return await interaction.response.send_message("❌ Geçerli bir `.jar` dosyası yükle!", ephemeral=True)

    if DB_AVAILABLE:
        ok, msg = await verify_user_quota(interaction, file)
        if not ok:
            return await interaction.response.send_message(msg, ephemeral=True)
    elif not check_file_size(file):
        return await interaction.response.send_message(f"❌ Dosya çok büyük! Maksimum limit: `{MAX_FILE_SIZE_MB} MB`", ephemeral=True)

    await safe_defer(interaction, thinking=True)
    tid = str(uuid.uuid4())[:8]
    sname = safe_filename(file.filename)
    inp = TEMP_DIR / f"{tid}_{sname}"
    out = None

    tracker = LiveProgressTracker(interaction, "🔍 SuS Scanner — Tehdit Taraması Yapılıyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    try:
        await file.save(inp)
        ret, stdout, stderr = await run_engine("scan", inp)
        if tracker:
            await tracker.stop()

        raw = stdout or stderr or ""
        output = redact_urls(raw)

        # ── SilentNet imza ayrıştırma ──
        silentnet = "[SILENTNET_DETECT]" in raw
        github_hits = [l.replace("[GITHUB_PAYLOAD]", "").strip()
                       for l in raw.splitlines() if "[GITHUB_PAYLOAD]" in l]
        encrypted_hits = [l.replace("[ENCRYPTED_CLASS]", "").strip()
                          for l in raw.splitlines() if "[ENCRYPTED_CLASS]" in l]

        if silentnet:
            # Otomatik temizlik: github payload'u sil + fabric.mod.json onar
            out = OUTPUT_DIR / f"{tid}_cleaned_{sname}"
            ret2, stdout2, stderr2 = await run_engine(
                "clean", inp, out, "http://127.0.0.1:9999/cleaned_webhook")
            removed, fabric_fixed = 0, False
            m = re.search(r"\[SILENTNET_CLEANED\]\s*removed=(\d+)\s*fabricPatched=(true|false)",
                          stdout2 or "")
            if m:
                removed, fabric_fixed = int(m.group(1)), m.group(2) == "true"

            e = mk_embed("🚨 SILENTNET DETECT — Zararlı Temizlendi",
                         f"**`{sname[:100]}`** içinde SilentNet stealer payload'u tespit edildi ve etkisiz hale getirildi!",
                         DANGER_COLOR)
            e.add_field(name="📁 Zararlı Klasör",
                        value=f"`github/` — `{len(github_hits)}` girdi", inline=True)
            e.add_field(name="🔒 Şifreli Class",
                        value=f"`{len(encrypted_hits)}` adet", inline=True)
            e.add_field(name="🧹 Temizlenen",
                        value=f"`{removed}` dosya silindi", inline=True)
            e.add_field(name="🧵 fabric.mod.json",
                        value="✅ Onarıldı (github referansları temizlendi)" if fabric_fixed else "ℹ️ Değişiklik gerekmedi",
                        inline=True)
            shown = [f"`{redact_urls(g)[:80]}`" for g in github_hits[:10]]
            if len(github_hits) > 10:
                shown.append(f"*+{len(github_hits) - 10} girdi daha…*")
            if shown:
                e.add_field(name="🗑️ Silinen Payload", value="\n".join(shown)[:1000], inline=False)
            shown_enc = [f"`{redact_urls(x)[:80]}`" for x in encrypted_hits[:10]]
            if len(encrypted_hits) > 10:
                shown_enc.append(f"*+{len(encrypted_hits) - 10} class daha…*")
            if shown_enc:
                e.add_field(name="🔐 Şifreli Classlar", value="\n".join(shown_enc)[:1000], inline=False)

            if ret2 == 0 and out.exists() and out.stat().st_size <= 24.5 * 1024 * 1024:
                dfile = discord.File(out, filename=f"cleaned_{sname}")
                try:
                    await safe_followup(interaction, embed=e, file=dfile)
                finally:
                    close_discord_files([dfile])
            elif ret2 == 0 and out.exists():
                e.add_field(name="⚠️ Uyarı",
                            value=f"Temizlenmiş dosya (`{out.stat().st_size / (1024*1024):.1f} MB`) Discord'un 25MB sınırını aşıyor!",
                            inline=False)
                await safe_followup(interaction, embed=e)
            else:
                log = redact_urls((stdout2 + stderr2))[:800]
                e.add_field(name="⚠️ Temizlik Notu",
                            value=f"Otomatik temizlik tamamlanamadı, `/jarclear` ile tekrar dene.\n```\n{log}\n```",
                            inline=False)
                await safe_followup(interaction, embed=e)
            return

        has_threats = any(w in output for w in ("MALICIOUS", "HIGH RISK", "RAT", "STEAL", "CRITICAL", "HIGH", "SUSPICIOUS"))
        color = DANGER_COLOR if has_threats else INFO_COLOR
        e = mk_embed("🔍 SuS Scanner Raporu", f"`{sname[:100]}`", color)
        chunks = [output[i:i+900] for i in range(0, min(len(output), 3600), 900)]
        for idx, chunk in enumerate(chunks[:4]):
            e.add_field(name=f"📋 Sonuç{' (devam)' if idx else ''}", value=f"```\n{chunk}\n```", inline=False)

        if has_threats:
            e.add_field(name="💡 Öneri", value="Aşağıdaki **🧹 Dosyayı Zararlılardan Temizle** butonunu veya `/jarclear` komutunu kullanarak zararlı kodları etkisiz hale getirebilirsin!", inline=False)
            await safe_followup(interaction, embed=e, view=CleanScanView(sname))
        else:
            await safe_followup(interaction, embed=e)
    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        safe_unlink(inp)
        if out is not None:
            safe_unlink(out)


# ─── B. CANLI WEBHOOK HONEYPOT (TUZAK LOGGER AVCISI) ──────────────────────────
@bot.tree.command(
    name="jarclear",
    description="🧹 JAR'daki Webhook, RAT ve IP Grabber kodlarını temizler ve tuzağa/honeypot'a yönlendirir."
)
@app_commands.describe(
    file="Temizlenecek .jar dosyası",
    honeypot_url="İsteğe bağlı: Webhook'ların yönlendirileceği tuzak URL (varsayılan: dahili honeypot)"
)
async def jarclear(
    interaction: discord.Interaction,
    file: discord.Attachment,
    honeypot_url: str = None
):
    if not file.filename.lower().endswith((".jar", ".zip")):
        return await interaction.response.send_message("❌ Geçerli bir `.jar` dosyası yükle!", ephemeral=True)

    if DB_AVAILABLE:
        ok, msg = await verify_user_quota(interaction, file)
        if not ok:
            return await interaction.response.send_message(msg, ephemeral=True)
    elif not check_file_size(file):
        return await interaction.response.send_message(f"❌ Dosya çok büyük! Maksimum limit: `{MAX_FILE_SIZE_MB} MB`", ephemeral=True)

    await safe_defer(interaction, thinking=True)
    tid = str(uuid.uuid4())[:8]
    inp = TEMP_DIR / f"{tid}_{safe_filename(file.filename)}"
    out = OUTPUT_DIR / f"{tid}_cleaned_{safe_filename(file.filename)}"

    tracker = LiveProgressTracker(interaction, "🧹 SuS Honeypot Cleaner — Zararlılar Avlanıyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    try:
        await file.save(inp)
        if honeypot_url and honeypot_url.strip():
            cand = honeypot_url.strip()
            target_hp = cand if is_safe_http_url(cand) else "http://127.0.0.1:9999/cleaned_webhook"
        else:
            target_hp = "http://127.0.0.1:9999/cleaned_webhook"
        ret, stdout, stderr = await run_engine("clean", inp, out, target_hp)

        if tracker:
            await tracker.stop()

        if ret != 0 or not out.exists():
            log = redact_urls(stdout + stderr)[:1000]
            return await safe_followup(interaction, f"❌ Temizleme hatası:\n```\n{log}\n```")

        # Parse exposed webhooks from stdout
        exposed_webhooks = []
        for line in stdout.splitlines():
            if "[EXPOSED_WEBHOOK]" in line:
                wh = line.replace("[EXPOSED_WEBHOOK]", "").strip()
                if wh and wh not in exposed_webhooks:
                    exposed_webhooks.append(wh)

        # Motor sayaçları (dürüst rapor için)
        purged, b64purged, susp_count, silent_count = 0, 0, 0, 0
        m_purged = re.search(r"Threats Purged:\s*(\d+)", stdout)
        if m_purged:
            purged = int(m_purged.group(1))
        m_b64 = re.search(r"Obfuscated \(Base64\) Neutralized:\s*(\d+)", stdout)
        if m_b64:
            b64purged = int(m_b64.group(1))
        m_susp = re.search(r"Suspicious Methods:\s*(\d+)", stdout)
        if m_susp:
            susp_count = int(m_susp.group(1))
        m_sil = re.search(r"\[SILENTNET_CLEANED\]\s*removed=(\d+)", stdout)
        if m_sil:
            silent_count = int(m_sil.group(1))
        susp_methods = [l.replace("[SUSPICIOUS_METHOD]", "").strip()
                        for l in stdout.splitlines() if "[SUSPICIOUS_METHOD]" in l]

        disp_name = safe_filename(file.filename)[:100]

        # Hiçbir şey bulunamadıysa sahte başarı yazma
        if purged == 0 and silent_count == 0 and susp_count == 0:
            e = mk_embed("✅ SuS Cleaner — Temiz Görünüyor",
                f"**`{disp_name}`** tarandı, bilinen zararlı gösterge bulunamadı — işlem yapılmadı, jar aynen iletiliyor.",
                SUCCESS_COLOR)
            dfile = discord.File(inp, filename=f"checked_{safe_filename(file.filename)}")
            try:
                await safe_followup(interaction, embed=e, file=dfile)
            finally:
                close_discord_files([dfile])
            return

        e = mk_embed("🧹 SuS Cleaner & Honeypot — Zararlı Kodlar Temizlendi",
            f"**`{disp_name}`** dosyasındaki tehditler etkisiz hale getirildi ve güvenli hale getirildi!\n\n"
            f"• **Honeypot Hedefi:** `{redact_urls(target_hp)}`\n"
            f"• **Etkisiz Hale Getirilen:** `{purged}` tehdit"
            + (f" (`{b64purged}` Base64-gizli)" if b64purged else "")
            + (f"\n• **SilentNet Payload:** `{silent_count}` dosya silindi" if silent_count else "")
            + (f"\n• **Şüpheli Metot:** `{susp_count}` (elle incele)" if susp_count else ""),
            SUCCESS_COLOR)

        # Webhook Honeypot Exposed Alert
        if exposed_webhooks:
            wh_report = []
            for wh in exposed_webhooks:
                # Saldırgan webhook'unun tamamını asla kanala basma — sadece maskeli ID.
                # NOT: ID ham URL'den çıkarılmalı (redact sonrası çıkarılamaz).
                masked_wh = "[redacted-url]"
                try:
                    m_id = re.search(r"/webhooks/(\d+)/", wh)
                    if m_id:
                        masked_wh = f"https://discord.com/api/webhooks/{m_id.group(1)}/********************"
                except Exception:
                    masked_wh = "[redacted-url]"
                wh_report.append(f"• 🚨 **Saldırgan Webhook:** `{masked_wh}`")

            e.add_field(
                name="🕵️ [LOGGER AVCISI] İfşa Edilen Saldırgan Webhook'ları",
                value=f"`{len(exposed_webhooks)}` adet zararlı webhook etkisiz hale getirildi.\n" + "\n".join(wh_report) + "\n\n💡 *Bu webhook'lar devre dışı bırakıldı ve tuzak loglayıcımıza yönlendirildi.*",
                inline=False
            )

            # Send Security Alert to MOD_LOG_CHANNEL
            if MOD_LOG_CHANNEL and interaction.guild is not None:
                try:
                    m_ch = interaction.guild.get_channel(MOD_LOG_CHANNEL)
                    if m_ch:
                        log_e = mk_embed("🚨 Tehdit Yakalandı: Discord Webhook Logger",
                            f"**Kullanıcı:** {interaction.user.mention} ({interaction.user.id})\n"
                            f"**Dosya:** `{disp_name}`\n"
                            f"**Yakalanan webhook sayısı:** `{len(exposed_webhooks)}`",
                            DANGER_COLOR)
                        await m_ch.send(embed=log_e)
                except Exception:
                    pass

        clean_log = redact_urls(stdout.strip())[-500:] if stdout.strip() else "Temizleme logu yok."
        e.add_field(name="📋 Temizleme Raporu", value=f"```\n{clean_log}\n```", inline=False)

        if susp_methods:
            shown_s = [f"`{redact_urls(s)[:80]}`" for s in susp_methods[:10]]
            if len(susp_methods) > 10:
                shown_s.append(f"*+{len(susp_methods) - 10} metot daha…*")
            e.add_field(name="🕵️ Şüpheli Metotlar (Temizlenemedi — Elle İncele)",
                        value="\n".join(shown_s)[:1000], inline=False)

        dfile = discord.File(out, filename=f"cleaned_{safe_filename(file.filename)}")
        try:
            await safe_followup(interaction, embed=e, file=dfile)
        finally:
            close_discord_files([dfile])
    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        for _f in [inp, out]:
            safe_unlink(_f)


# ─── C. GELİŞTİRİCİLER İÇİN LİSANS & KORUMA ENJEKTÖRÜ (/jarprotect) ───────────
@bot.tree.command(
    name="jarprotect",
    description="🛡️ Geliştirici Koruma Enjektörü: JAR moduna HWID kilidi, Süre Sınırı ve Güvenlik Kalkanı ekle."
)
@app_commands.describe(
    file="Lisans ve koruma kalkanı enjekte edilecek .jar dosyası",
    hwid="Kilitlenecek donanım kimliği / kullanıcı adı (boş bırakılabilir)",
    expire_days="Lisans geçerlilik süresi (gün olarak, örn: 30 gün sonra patlar)",
    alarm_webhook="İhlal durumunda uyarı gönderecek Discord Webhook URL'si"
)
async def jarprotect(
    interaction: discord.Interaction,
    file: discord.Attachment,
    hwid: str = None,
    expire_days: int = None,
    alarm_webhook: str = None
):
    if not file.filename.lower().endswith((".jar", ".zip")):
        return await interaction.response.send_message("❌ Geçerli bir `.jar` dosyası yükle!", ephemeral=True)

    if DB_AVAILABLE:
        ok, msg = await verify_user_quota(interaction, file)
        if not ok:
            return await interaction.response.send_message(msg, ephemeral=True)
    elif not check_file_size(file):
        return await interaction.response.send_message(f"❌ Dosya çok büyük! Maksimum limit: `{MAX_FILE_SIZE_MB} MB`", ephemeral=True)

    await safe_defer(interaction, thinking=True)
    tid = str(uuid.uuid4())[:8]
    inp = TEMP_DIR / f"{tid}_{safe_filename(file.filename)}"
    out = OUTPUT_DIR / f"{tid}_protected_{safe_filename(file.filename)}"

    tracker = LiveProgressTracker(interaction, "🛡️ SuS Protector — Lisans Kalkanı Enjekte Ediliyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    start_time = time.time()
    try:
        await file.save(inp)

        # Calculate epoch expiry (0-3650 gün clamp)
        expiry_epoch = 0
        if expire_days and expire_days > 0:
            expire_days = min(expire_days, 3650)
            expiry_epoch = int((time.time() + (expire_days * 86400)) * 1000)

        target_hwid = sanitize_hwid(hwid)
        if alarm_webhook and alarm_webhook.strip():
            cand = alarm_webhook.strip()
            wh_alarm = cand if is_safe_http_url(cand) else "NONE"
        else:
            wh_alarm = "NONE"

        ret, stdout, stderr = await run_engine("protect", inp, out, target_hwid, str(expiry_epoch), wh_alarm)
        elapsed = round(time.time() - start_time, 2)

        if tracker:
            await tracker.stop()

        if ret != 0 or not out.exists():
            log = redact_urls(stdout + stderr)[:1000]
            return await safe_followup(interaction, f"❌ Koruma enjeksiyonu hatası:\n```\n{log}\n```")

        disp_name = safe_filename(file.filename)[:100]
        e = mk_embed("🛡️ SuS Protector v4.1 — Lisans Kalkanı Enjekte Edildi",
            f"**`{disp_name}`** başarıyla koruma altına alındı ve lisans kalkanı entegre edildi! (⏱️ `{elapsed}s`)\n\n"
            "🔒 **Enjekte Edilen Güvenlik Özellikleri:**\n"
            f"• **HWID / Kullanıcı Kilidi:** `{target_hwid if target_hwid != 'NONE' else 'Devre Dışı'}`\n"
            f"• **TimeBomb Süre Kilidi:** `{f'{expire_days} Gün Sonra' if expire_days else 'Sınırsız / Yok'}`\n"
            f"• **Alarm Bildirim Ağı:** `{'Aktif Webhook' if wh_alarm != 'NONE' else 'Bağlanmadı'}`\n"
            "• **Anti-Tamper & Entrypoint Muhafızı:** Sınıf yüklenişinde yetkisiz çalıştırmayı doğrudan sonlandırır.",
            0x2ECC71)

        e.add_field(name="📊 Dosya Bilgileri",
            value=f"• Orijinal Boyut: `{file.size / 1024:.1f} KB`\n"
                  f"• Korumalı Boyut: `{out.stat().st_size / 1024:.1f} KB`", inline=True)

        dfile = discord.File(out, filename=f"protected_{safe_filename(file.filename)}")
        try:
            await safe_followup(interaction, embed=e, file=dfile)
        finally:
            close_discord_files([dfile])
    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        for _f in [inp, out]:
            safe_unlink(_f)


# ─── D. BYTECODE KARŞILAŞTIRICI (/jardiff) ────────────────────────────────────
@bot.tree.command(
    name="jardiff",
    description="🔍 İki JAR dosyası arasındaki bytecode, sınıf ve yapısal farkları karşılaştırıp raporlar."
)
@app_commands.describe(
    original_file="Orijinal .jar dosyası",
    modified_file="Değiştirilmiş / Cracklenmiş / Temizlenmiş .jar dosyası"
)
async def jardiff(
    interaction: discord.Interaction,
    original_file: discord.Attachment,
    modified_file: discord.Attachment
):
    if not (original_file.filename.lower().endswith((".jar", ".zip")) and modified_file.filename.lower().endswith((".jar", ".zip"))):
        return await interaction.response.send_message("❌ Lütfen iki adet geçerli `.jar` dosyası yükle!", ephemeral=True)

    if DB_AVAILABLE:
        ok1, msg1 = await verify_user_quota(interaction, original_file)
        if not ok1:
            return await interaction.response.send_message(msg1, ephemeral=True)

    await safe_defer(interaction, thinking=True)
    tid = str(uuid.uuid4())[:8]
    inp1 = TEMP_DIR / f"{tid}_orig_{safe_filename(original_file.filename)}"
    inp2 = TEMP_DIR / f"{tid}_mod_{safe_filename(modified_file.filename)}"
    rep  = OUTPUT_DIR / f"diff_report_{tid}.txt"

    tracker = LiveProgressTracker(interaction, "🔍 SuS Differ — Bytecode Karşılaştırılıyor") if DB_AVAILABLE else None
    if tracker:
        await tracker.start()

    start_time = time.time()
    try:
        await original_file.save(inp1)
        await modified_file.save(inp2)

        ret, stdout, stderr = await run_engine("diff", inp1, inp2, rep)
        elapsed = round(time.time() - start_time, 2)

        if tracker:
            await tracker.stop()

        diff_summary = redact_urls(stdout.strip())[-1000:] if stdout.strip() else "Karşılaştırma tamamlandı."

        e = mk_embed("🔍 SuS Differ — JAR Karşılaştırma Raporu",
            f"**`{original_file.filename}`** ➔ **`{modified_file.filename}`** (⏱️ `{elapsed}s`)\n\n"
            f"```\n{diff_summary}\n```", 0x3498DB)

        e.add_field(name="📊 Boyut Karşılaştırması",
            value=f"• Orijinal: `{original_file.size / 1024:.1f} KB`\n"
                  f"• Değiştirilmiş: `{modified_file.size / 1024:.1f} KB`\n"
                  f"• Fark: `{(modified_file.size - original_file.size) / 1024:+.1f} KB`", inline=True)

        if rep.exists():
            dfile = discord.File(rep, filename=f"diff_report_{safe_filename(original_file.filename)}.txt")
            try:
                await safe_followup(interaction, embed=e, file=dfile)
            finally:
                close_discord_files([dfile])
        else:
            await safe_followup(interaction, embed=e)
    except Exception as exc:
        if tracker:
            await tracker.stop()
        await safe_followup(interaction, f"❌ Beklenmedik hata: {exc}")
    finally:
        for _f in [inp1, inp2, rep]:
            safe_unlink(_f)


# ─── VERIFY PANEL ─────────────────────────────
@bot.tree.command(name="verify-panel", description="✅ Doğrulama panelini kanalına gönder.")
@app_commands.default_permissions(manage_guild=True)
async def verify_panel(interaction: discord.Interaction):
    if interaction.guild is None or interaction.channel is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    now = discord.utils.utcnow().strftime("%d.%m.%Y %H:%M")
    e = discord.Embed(title="✅ DOĞRULAMA",
        description="Sunucudaki kanallara erişim sağlamak ve bot korumasını geçmek için aşağıdaki butona tıklayın.",
        color=SUCCESS_COLOR)
    e.set_footer(text=f"Decompleder Platform • Enterprise Security • {now}")
    try:
        await interaction.channel.send(embed=e, view=VerifyButton())
    except discord.Forbidden:
        return await interaction.response.send_message("❌ Bu kanala mesaj gönderme yetkim yok.", ephemeral=True)
    except discord.HTTPException:
        return await interaction.response.send_message("❌ Panel gönderilemedi, tekrar dene.", ephemeral=True)
    await interaction.response.send_message("✅ Doğrulama paneli gönderildi.", ephemeral=True)

# ─── TICKET PANEL ─────────────────────────────
@bot.tree.command(name="ticket-panel", description="🎫 Ticket destek panelini kanalına gönder.")
@app_commands.default_permissions(manage_guild=True)
async def ticket_panel(interaction: discord.Interaction):
    if interaction.guild is None or interaction.channel is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    e = mk_embed("🎫 Destek Sistemi",
        "Herhangi bir sorun, öneri veya satın alma talebi için ticket aç!\n\n"
        "💡 **Nasıl çalışır?**\n"
        "1. Aşağıdaki butona tıkla\n"
        "2. Sana özel bir kanal açılır\n"
        "3. Destek ekibi seninle ilgilenir", BRAND_COLOR)
    try:
        await interaction.channel.send(embed=e, view=TicketView())
    except discord.Forbidden:
        return await interaction.response.send_message("❌ Bu kanala mesaj gönderme yetkim yok.", ephemeral=True)
    except discord.HTTPException:
        return await interaction.response.send_message("❌ Panel gönderilemedi, tekrar dene.", ephemeral=True)
    await interaction.response.send_message("✅ Ticket paneli gönderildi.", ephemeral=True)

# ─── GIVEAWAY ─────────────────────────────────
@bot.tree.command(name="giveaway", description="🎁 Çekiliş başlat.")
@app_commands.describe(sure="Kaç dakika sürsün", odul="Ödül nedir", kazanan="Kaç kişi kazanacak")
@app_commands.default_permissions(manage_guild=True)
async def giveaway(interaction: discord.Interaction, sure: int, odul: str, kazanan: int = 1):
    if sure < 1 or sure > 10080:
        return await interaction.response.send_message("❌ Süre 1-10080 dakika arasında olmalı.", ephemeral=True)

    ends_at  = discord.utils.utcnow() + datetime.timedelta(minutes=sure)
    channel  = interaction.channel

    e = mk_embed(f"🎁 ÇEKİLİŞ: {odul}",
        f"🏆 **Ödül:** {odul}\n"
        f"👥 **Kazanan Sayısı:** {kazanan}\n"
        f"⏰ **Bitiş:** <t:{int(ends_at.timestamp())}:R>\n\n"
        "Katılmak için **🎉** reaksiyonu bırak!",
        0xF39C12)
    e.set_footer(text=f"Düzenleyen: {interaction.user.display_name} | {FOOTER_TEXT}")

    msg = await channel.send(embed=e)
    await msg.add_reaction("🎉")
    await interaction.response.send_message("✅ Çekiliş başlatıldı!", ephemeral=True)

    g_data = load_json("giveaways")
    g_data[str(msg.id)] = {
        "channel": channel.id, "ends_at": ends_at.timestamp(),
        "winners": kazanan, "prize": odul
    }
    save_json("giveaways", g_data)

    await asyncio.sleep(sure * 60)

    try:
        msg = await channel.fetch_message(msg.id)
    except (discord.NotFound, discord.HTTPException):
        return

    reaction = discord.utils.get(msg.reactions, emoji="🎉")
    if not reaction:
        return await channel.send("❌ Çekilişe kimse katılmadı.")

    users = [u async for u in reaction.users() if not u.bot]
    if not users:
        return await channel.send("❌ Çekilişe kimse katılmadı.")

    winners  = random.sample(users, min(kazanan, len(users)))
    wins_str = ", ".join(w.mention for w in winners)
    e2 = mk_embed("🎊 Çekiliş Sonuçlandı!",
        f"**Ödül:** {odul}\n**Kazanan(lar):** {wins_str}\n\nTebrikler! 🎉", SUCCESS_COLOR)
    await channel.send(embed=e2)

    g_data = load_json("giveaways")
    g_data.pop(str(msg.id), None)
    save_json("giveaways", g_data)

# ─── MODERATION ───────────────────────────────
async def _mod_log(guild: discord.Guild, title: str, desc: str, color: int = DANGER_COLOR):
    ch_id = eff_mod_log(guild)
    if ch_id:
        ch = guild.get_channel(ch_id)
        if ch and isinstance(ch, discord.TextChannel):
            try:
                await ch.send(embed=mk_embed(title, desc, color))
            except discord.Forbidden:
                pass


@bot.tree.command(name="ban", description="🔨 Kullanıcıyı sunucudan banla.")
@app_commands.describe(user="Kullanıcı", sebep="Sebep")
@app_commands.default_permissions(ban_members=True)
async def ban_cmd(interaction: discord.Interaction, user: discord.Member, sebep: str = "Belirtilmedi"):
    sebep = (sebep or "Belirtilmedi")[:300]
    if interaction.guild is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    if interaction.guild.owner_id != interaction.user.id and user.top_role >= interaction.user.top_role:
        return await interaction.response.send_message("❌ Bu kullanıcıyı banlayamazsın (daha yüksek/eşit rol).", ephemeral=True)
    if user.top_role >= interaction.guild.me.top_role:
        return await interaction.response.send_message("❌ Botun rolü bu kullanıcıdan düşük, işlem yapamam.", ephemeral=True)
    try:
        await user.ban(reason=f"{sebep} | Yetkili: {interaction.user}")
        await interaction.response.send_message(
            embed=mk_embed("🔨 Ban", f"{user.mention} banlandı.\n**Sebep:** {sebep}", DANGER_COLOR))
        await _mod_log(interaction.guild, "🔨 Ban",
            f"**Kullanıcı:** {user} ({user.id})\n**Yetkili:** {interaction.user}\n**Sebep:** {sebep}")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Bu kullanıcıyı banlama yetkim yok.", ephemeral=True)
    except (discord.NotFound, discord.HTTPException):
        try:
            if not interaction.response.is_done():
                await interaction.response.send_message("❌ Ban işlemi başarısız oldu.", ephemeral=True)
            else:
                await interaction.followup.send("❌ Ban işlemi başarısız oldu.", ephemeral=True)
        except Exception:
            pass


@bot.tree.command(name="kick", description="👢 Kullanıcıyı sunucudan at.")
@app_commands.describe(user="Kullanıcı", sebep="Sebep")
@app_commands.default_permissions(kick_members=True)
async def kick_cmd(interaction: discord.Interaction, user: discord.Member, sebep: str = "Belirtilmedi"):
    sebep = (sebep or "Belirtilmedi")[:300]
    if interaction.guild is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    if interaction.guild.owner_id != interaction.user.id and user.top_role >= interaction.user.top_role:
        return await interaction.response.send_message("❌ Bu kullanıcıyı atamazsın.", ephemeral=True)
    if user.top_role >= interaction.guild.me.top_role:
        return await interaction.response.send_message("❌ Botun rolü bu kullanıcıdan düşük, işlem yapamam.", ephemeral=True)
    try:
        await user.kick(reason=f"{sebep} | Yetkili: {interaction.user}")
        await interaction.response.send_message(
            embed=mk_embed("👢 Kick", f"{user.mention} atıldı.\n**Sebep:** {sebep}", DANGER_COLOR))
        await _mod_log(interaction.guild, "👢 Kick",
            f"**Kullanıcı:** {user} ({user.id})\n**Yetkili:** {interaction.user}\n**Sebep:** {sebep}")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Bu kullanıcıyı atma yetkim yok.", ephemeral=True)
    except (discord.NotFound, discord.HTTPException):
        try:
            if not interaction.response.is_done():
                await interaction.response.send_message("❌ Kick işlemi başarısız oldu.", ephemeral=True)
            else:
                await interaction.followup.send("❌ Kick işlemi başarısız oldu.", ephemeral=True)
        except Exception:
            pass


@bot.tree.command(name="mute", description="🔇 Kullanıcıyı sustur (timeout).")
@app_commands.describe(user="Kullanıcı", dakika="Süre dakika cinsinden (1-40320)", sebep="Sebep")
@app_commands.default_permissions(moderate_members=True)
async def mute_cmd(interaction: discord.Interaction, user: discord.Member, dakika: int = 10, sebep: str = "Belirtilmedi"):
    sebep = (sebep or "Belirtilmedi")[:300]
    if interaction.guild is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    if interaction.guild.owner_id != interaction.user.id and user.top_role >= interaction.user.top_role:
        return await interaction.response.send_message("❌ Bu kullanıcıyı susturamassın.", ephemeral=True)
    if user.top_role >= interaction.guild.me.top_role:
        return await interaction.response.send_message("❌ Botun rolü bu kullanıcıdan düşük, işlem yapamam.", ephemeral=True)
    dakika = max(1, min(dakika, 40320))
    try:
        until = discord.utils.utcnow() + datetime.timedelta(minutes=dakika)
        await user.timeout(until, reason=f"{sebep} | Yetkili: {interaction.user}")
        await interaction.response.send_message(
            embed=mk_embed("🔇 Mute",
                f"{user.mention} **{dakika}dk** susturuldu.\n**Sebep:** {sebep}", DANGER_COLOR))
        await _mod_log(interaction.guild, "🔇 Mute",
            f"**Kullanıcı:** {user} ({user.id})\n**Süre:** {dakika}dk\n"
            f"**Yetkili:** {interaction.user}\n**Sebep:** {sebep}")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Bu kullanıcıyı susturma yetkim yok.", ephemeral=True)
    except (discord.NotFound, discord.HTTPException):
        try:
            if not interaction.response.is_done():
                await interaction.response.send_message("❌ Susturma işlemi başarısız oldu.", ephemeral=True)
            else:
                await interaction.followup.send("❌ Susturma işlemi başarısız oldu.", ephemeral=True)
        except Exception:
            pass


@bot.tree.command(name="unmute", description="🔊 Kullanıcının timeout'unu kaldır.")
@app_commands.describe(user="Kullanıcı")
@app_commands.default_permissions(moderate_members=True)
async def unmute_cmd(interaction: discord.Interaction, user: discord.Member):
    if interaction.guild is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    try:
        await user.timeout(None, reason=f"Unmute | Yetkili: {interaction.user}")
        await interaction.response.send_message(
            embed=mk_embed("🔊 Unmute", f"{user.mention} susturması kaldırıldı.", SUCCESS_COLOR))
        await _mod_log(interaction.guild, "🔊 Unmute",
            f"**Kullanıcı:** {user} ({user.id})\n**Yetkili:** {interaction.user}", SUCCESS_COLOR)
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)
    except (discord.NotFound, discord.HTTPException):
        try:
            if not interaction.response.is_done():
                await interaction.response.send_message("❌ Unmute işlemi başarısız oldu.", ephemeral=True)
            else:
                await interaction.followup.send("❌ Unmute işlemi başarısız oldu.", ephemeral=True)
        except Exception:
            pass


@bot.tree.command(name="warn", description="⚠️ Kullanıcıya uyarı ver.")
@app_commands.describe(user="Kullanıcı", sebep="Sebep")
@app_commands.default_permissions(manage_messages=True)
async def warn_cmd(interaction: discord.Interaction, user: discord.Member, sebep: str):
    sebep = (sebep or "Belirtilmedi")[:300]
    uid = str(user.id)
    if DB_AVAILABLE:
        count = await add_warn(uid, sebep, str(interaction.user))
    else:
        data = load_json("warns")
        data.setdefault(uid, []).append({
            "sebep": sebep, "yetkili": str(interaction.user), "zaman": time.time()
        })
        save_json("warns", data)
        count = len(data[uid])

    await interaction.response.send_message(
        embed=mk_embed("⚠️ Uyarı",
            f"{user.mention} uyarıldı! (**{count}. uyarı**)\n**Sebep:** {sebep}", 0xE67E22))
    await _mod_log(interaction.guild, "⚠️ Uyarı",
        f"**Kullanıcı:** {user} ({user.id})\n**Uyarı #{count}**\n"
        f"**Sebep:** {sebep}\n**Yetkili:** {interaction.user}", 0xE67E22)


@bot.tree.command(name="warns", description="📋 Kullanıcının uyarılarını görüntüle.")
@app_commands.describe(user="Kullanıcı")
@app_commands.default_permissions(manage_messages=True)
async def warns_cmd(interaction: discord.Interaction, user: discord.Member):
    uid = str(user.id)
    if DB_AVAILABLE:
        warns = await get_warns(uid)
    else:
        warns = load_json("warns").get(uid, [])

    if not warns:
        return await interaction.response.send_message(
            embed=mk_embed("📋 Uyarılar", f"{user.mention} için aktif uyarı bulunamadı.", INFO_COLOR))

    lines = [f"**{i+1}.** {_warn_field(w)[0]} — *{_warn_field(w)[1]}*" for i, w in enumerate(warns)]
    await interaction.response.send_message(
        embed=mk_embed(f"📋 {user.display_name} Uyarıları ({len(warns)})", "\n".join(lines), 0xE67E22))


@bot.tree.command(name="clearwarns", description="🗑️ Kullanıcının tüm uyarılarını temizle.")
@app_commands.describe(user="Kullanıcı")
@app_commands.default_permissions(manage_guild=True)
async def clearwarns_cmd(interaction: discord.Interaction, user: discord.Member):
    uid = str(user.id)
    if DB_AVAILABLE:
        await clear_warns(uid)
    else:
        data = load_json("warns")
        data.pop(uid, None)
        save_json("warns", data)

    await interaction.response.send_message(
        embed=mk_embed("🗑️ Uyarılar Temizlendi", f"{user.mention} kullanıcısının tüm uyarıları silindi.", SUCCESS_COLOR))


@bot.tree.command(name="clear", description="🧹 Kanaldan belirtilen sayıda mesaj sil.")
@app_commands.describe(sayi="Silinecek mesaj sayısı (1-100)")
@app_commands.default_permissions(manage_messages=True)
async def clear_cmd(interaction: discord.Interaction, sayi: int):
    sayi = max(1, min(sayi, 100))
    if interaction.guild is None or interaction.channel is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    await interaction.response.defer(ephemeral=True)
    try:
        deleted = await interaction.channel.purge(limit=sayi)
        await interaction.followup.send(
            embed=mk_embed("🧹 Temizlendi", f"**{len(deleted)}** mesaj silindi.", SUCCESS_COLOR),
            ephemeral=True)
    except discord.Forbidden:
        await interaction.followup.send("❌ Mesaj silme yetkim yok.", ephemeral=True)
    except (discord.NotFound, discord.HTTPException):
        try:
            await interaction.followup.send("❌ Mesajlar silinirken bir hata oluştu.", ephemeral=True)
        except Exception:
            pass


@bot.tree.command(name="unban", description="✅ Kullanıcının banını kaldır.")
@app_commands.describe(user_id="Kullanıcı ID'si")
@app_commands.default_permissions(ban_members=True)
async def unban_cmd(interaction: discord.Interaction, user_id: str):
    if interaction.guild is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    try:
        uid  = int(user_id)
        user = await bot.fetch_user(uid)
        await interaction.guild.unban(user, reason=f"Yetkili: {interaction.user}")
        await interaction.response.send_message(
            embed=mk_embed("✅ Unban", f"**{user}** (`{uid}`) banı kaldırıldı.", SUCCESS_COLOR))
        await _mod_log(interaction.guild, "✅ Unban",
            f"**Kullanıcı:** {user} ({uid})\n**Yetkili:** {interaction.user}", SUCCESS_COLOR)
    except ValueError:
        await interaction.response.send_message("❌ Geçerli bir kullanıcı ID'si gir.", ephemeral=True)
    except discord.NotFound:
        try:
            if not interaction.response.is_done():
                await interaction.response.send_message("❌ Bu ID banlı listesinde bulunamadı.", ephemeral=True)
            else:
                await interaction.followup.send("❌ Bu ID banlı listesinde bulunamadı.", ephemeral=True)
        except Exception:
            pass
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)
    except discord.HTTPException:
        try:
            if not interaction.response.is_done():
                await interaction.response.send_message("❌ Unban işlemi başarısız oldu.", ephemeral=True)
            else:
                await interaction.followup.send("❌ Unban işlemi başarısız oldu.", ephemeral=True)
        except Exception:
            pass

# ─── UTILITY COMMANDS ─────────────────────────
@bot.tree.command(name="ping", description="🏓 Bot gecikmesini göster.")
async def ping_cmd(interaction: discord.Interaction):
    lat   = round(bot.latency * 1000)
    color = SUCCESS_COLOR if lat < 100 else (0xE67E22 if lat < 200 else DANGER_COLOR)
    await interaction.response.send_message(
        embed=mk_embed("🏓 Pong!", f"API Gecikmesi: **{lat}ms**", color))


@bot.tree.command(name="botinfo", description="🤖 Bot hakkında bilgi al.")
async def botinfo_cmd(interaction: discord.Interaction):
    total_members = sum(g.member_count for g in bot.guilds if g.member_count)
    e = mk_embed("🤖 SuS Cracker Bot",
        f"**Sunucu Sayısı:** {len(bot.guilds)}\n"
        f"**Toplam Üye:** {total_members:,}\n"
        f"**API Ping:** {round(bot.latency*1000)}ms\n"
        f"**Versiyon:** v4.1 (Enhanced Edition)\n"
        f"**discord.py:** {discord.__version__}\n\n"
        "**Özellikler:**\n"
        "JAR Obfuscator • DeObfuscator • DeobfSrc • Cracker (43 Metot) • Scanner\n"
        "Webhook Honeypot • JarProtect (Lisans Enjektörü) • JarDiff Karşılaştırıcı\n"
        "Görsel Rank Kartı • Sayfalamalı Leaderboard • SQLite WAL • VIP Öncelik",
        BRAND_COLOR)
    e.set_thumbnail(url=bot.user.display_avatar.url)
    await interaction.response.send_message(embed=e)


@bot.tree.command(name="userinfo", description="👤 Kullanıcı bilgilerini görüntüle.")
@app_commands.describe(user="Kullanıcı (boş bırakırsan kendin)")
async def userinfo_cmd(interaction: discord.Interaction, user: discord.Member = None):
    user = user or interaction.user
    if DB_AVAILABLE:
        user_xp = await get_user_xp(str(user.id))
        warns = await get_warns(str(user.id))
        lvl = user_xp["level"]
        xp = user_xp["xp"]
        warn_c = len(warns)
    else:
        xp_d   = load_json("xp").get(str(user.id), {"xp": 0, "level": 1})
        lvl    = xp_d["level"]
        xp     = xp_d["xp"]
        warn_c = len(load_json("warns").get(str(user.id), []))

    e = mk_embed(f"👤 {user.display_name}", color=BRAND_COLOR)
    e.add_field(name="ID",      value=str(user.id),    inline=True)
    if isinstance(user, discord.Member) and getattr(user, "joined_at", None):
        e.add_field(name="Katılım", value=f"<t:{int(user.joined_at.timestamp())}:R>", inline=True)
    else:
        e.add_field(name="Katılım", value="Sunucu dışında görüntülenemiyor", inline=True)
    e.add_field(name="Kayıt",   value=f"<t:{int(user.created_at.timestamp())}:R>", inline=True)
    e.add_field(name="Seviye",  value=f"**{lvl}** (XP: {xp})", inline=True)
    e.add_field(name="Uyarı",   value=str(warn_c), inline=True)
    e.add_field(name="Bot?",    value="✅" if user.bot else "❌", inline=True)
    if isinstance(user, discord.Member):
        try:
            roles_str = " ".join(r.mention for r in reversed(user.roles[1:])) or "Yok"
        except Exception:
            roles_str = "Yok"
        if len(roles_str) > 1000:
            roles_str = roles_str[:1000] + "…"
        e.add_field(name="Roller", value=roles_str, inline=False)
    else:
        e.add_field(name="Roller", value="Sunucu dışında görüntülenemiyor", inline=False)
    e.set_thumbnail(url=user.display_avatar.url)
    await interaction.response.send_message(embed=e)


@bot.tree.command(name="serverinfo", description="🏠 Sunucu bilgilerini görüntüle.")
async def serverinfo_cmd(interaction: discord.Interaction):
    g = interaction.guild
    if g is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    e = mk_embed(f"🏠 {g.name}", color=BRAND_COLOR)
    e.add_field(name="Üye",          value=f"{g.member_count:,}", inline=True)
    e.add_field(name="Kanallar",      value=str(len(g.channels)),  inline=True)
    e.add_field(name="Roller",        value=str(len(g.roles)),     inline=True)
    e.add_field(name="Sahip",         value=g.owner.mention if g.owner else "?", inline=True)
    e.add_field(name="Oluşturulma",   value=f"<t:{int(g.created_at.timestamp())}:R>", inline=True)
    e.add_field(name="Boost Seviyesi",value=f"Level {g.premium_tier} ({g.premium_subscription_count} boost)", inline=True)
    e.add_field(name="Emojis",        value=str(len(g.emojis)), inline=True)
    e.add_field(name="Stickers",      value=str(len(g.stickers)), inline=True)
    if g.icon:
        e.set_thumbnail(url=g.icon.url)
    await interaction.response.send_message(embed=e)


# ─── B. GÖRSEL RANK KARTI (/rank) ─────────────────────────────────────────────
@bot.tree.command(name="rank", description="📈 XP seviyeni ve görsel neon rank kartını görüntüle.")
@app_commands.describe(user="Kullanıcı (boş bırakırsan kendin)")
async def rank_cmd(interaction: discord.Interaction, user: discord.Member = None):
    await safe_defer(interaction, thinking=True)
    target_user = user or interaction.user

    if DB_AVAILABLE:
        user_d = await get_user_xp(str(target_user.id))
        rank_pos = await get_user_rank_position(str(target_user.id))
        lvl    = user_d["level"]
        xp     = user_d["xp"]
        needed = lvl * 100
        try:
            # Pillow Canvas Rank Card Generation
            card_buf = await create_rank_card(
                username=target_user.name,
                avatar_url=target_user.display_avatar.url,
                current_xp=xp,
                needed_xp=needed,
                level=lvl,
                rank_pos=rank_pos
            )
            return await safe_followup(
                interaction,
                file=discord.File(card_buf, filename=f"rank_{target_user.name}.png")
            )
        except Exception as exc:
            print(f"[-] Rank card render error: {exc}")

    # Fallback Embed
    xp_d   = load_json("xp").get(str(target_user.id), {"xp": 0, "level": 1})
    lvl    = xp_d["level"]
    xp     = xp_d["xp"]
    needed = lvl * 100
    pct    = min(xp / needed, 1.0)
    filled = int(pct * 20)
    bar    = "█" * filled + "░" * (20 - filled)
    e = mk_embed(f"📈 {target_user.display_name} — Rank", color=0xF39C12)
    e.add_field(name="Seviye",    value=f"**{lvl}**",        inline=True)
    e.add_field(name="XP",        value=f"{xp} / {needed}", inline=True)
    e.add_field(name="İlerleme",  value=f"`{bar}` {int(pct*100)}%", inline=False)
    e.set_thumbnail(url=target_user.display_avatar.url)
    await safe_followup(interaction, embed=e)


# ─── C. SAYFALAMALI LİDERLİK TABLOSU (/leaderboard) ───────────────────────────
class LeaderboardPaginationView(discord.ui.View):
    def __init__(self, current_page: int, total_pages: int, total_users: int):
        super().__init__(timeout=180)
        self.page = current_page
        self.total_pages = max(1, total_pages)
        self.total_users = total_users
        self._update_btn_states()

    def _update_btn_states(self):
        self.first_btn.disabled = (self.page <= 0)
        self.prev_btn.disabled  = (self.page <= 0)
        self.next_btn.disabled  = (self.page >= self.total_pages - 1)
        self.last_btn.disabled  = (self.page >= self.total_pages - 1)

    async def _render(self, interaction: discord.Interaction):
        self._update_btn_states()
        embed = await self._build_embed()
        await interaction.response.edit_message(embed=embed, view=self)

    async def _build_embed(self) -> discord.Embed:
        offset = self.page * 10
        if DB_AVAILABLE:
            rows, total = await get_leaderboard_data(limit=10, offset=offset)
            self.total_users = total
            self.total_pages = max(1, (total + 9) // 10)
        else:
            xp_data = load_json("xp")
            sorted_all = sorted(
                xp_data.items(),
                key=lambda x: (x[1].get("level", 1), x[1].get("xp", 0)),
                reverse=True
            )
            self.total_users = len(sorted_all)
            self.total_pages = max(1, (self.total_users + 9) // 10)
            rows = [(uid, d.get("xp", 0), d.get("level", 1)) for uid, d in sorted_all[offset:offset+10]]

        medals = ["🥇", "🥈", "🥉"]
        lines = []
        for idx, (uid, xp, lvl) in enumerate(rows):
            pos = offset + idx + 1
            badge = medals[pos - 1] if pos <= 3 else f"`#{pos:02d}`"
            lines.append(f"{badge} <@{uid}> — **Seviye {lvl}** • `{xp:,} XP`")

        e = mk_embed(
            f"🏆 Sunucu Liderlik Tablosu (Sayfa {self.page + 1}/{self.total_pages})",
            f"Toplam **{self.total_users}** kayıtlı kullanıcı arasından sıralama:\n\n" +
            ("\n".join(lines) if lines else "*Bu sayfada kullanıcı bulunmuyor.*"),
            0xF1C40F
        )
        e.set_footer(text=f"Sayfa {self.page + 1}/{self.total_pages} • Toplam {self.total_users} Üye • SuS XP Sistemi")
        return e

    @discord.ui.button(label="⏮️ İlk", style=discord.ButtonStyle.secondary, custom_id="lb_btn_first")
    async def first_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        self.page = 0
        await self._render(interaction)

    @discord.ui.button(label="◀️ Geri", style=discord.ButtonStyle.primary, custom_id="lb_btn_prev")
    async def prev_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        if self.page > 0:
            self.page -= 1
        await self._render(interaction)

    @discord.ui.button(label="▶️ İleri", style=discord.ButtonStyle.primary, custom_id="lb_btn_next")
    async def next_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        if self.page < self.total_pages - 1:
            self.page += 1
        await self._render(interaction)

    @discord.ui.button(label="⏭️ Son", style=discord.ButtonStyle.secondary, custom_id="lb_btn_last")
    async def last_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        self.page = self.total_pages - 1
        await self._render(interaction)


@bot.tree.command(name="leaderboard", description="🏆 İnteraktif sayfalamalı XP sıralamasını göster.")
async def leaderboard_cmd(interaction: discord.Interaction):
    await safe_defer(interaction, thinking=True)
    if DB_AVAILABLE:
        _, total = await get_leaderboard_data(limit=10, offset=0)
    else:
        total = len(load_json("xp"))

    total_pages = max(1, (total + 9) // 10)
    view = LeaderboardPaginationView(current_page=0, total_pages=total_pages, total_users=total)
    embed = await view._build_embed()
    await safe_followup(interaction, embed=embed, view=view)



# ─── PRODUCT PANEL ────────────────────────────
@bot.tree.command(name="products", description="🛒 SuS Cracker ürün ve hizmetlerini görüntüle.")
async def products_cmd(interaction: discord.Interaction):
    e = mk_embed("🛒 SuS Cracker Suite v4.1 — Ürünler & Hizmetler",
        "Profesyonel bytecode güvenlik, analiz ve lisanslama çözümleri\n\n"
        "**🛡️ JAR Obfuscator (3 Seviye)**\n"
        "> Polimorfik sınıf içi string şifreleme, matematiksel opaque predicates, bitwise sabit karıştırma, Unicode/homoglyph sınıf & üye gizleme.\n\n"
        "**⚡ JAR DeObfuscator & DeobfSrc**\n"
        "> ASM ClassRemapper güvenli remapping, sabit katlama, ölü kod budama + CFR & Vineflower çift decompiler boru hattı ile tam `.java` kaynak kodları.\n\n"
        "**🔓 JarCracker (43 Metot)**\n"
        "> Meteor Client Addon, Fabric Mod, Forge Mod, Lunar/Client için HWID, KeyAuth, AntiVM, AntiDebug, Mixin, Reflection bypass.\n\n"
        "**🧹 JarClear & Webhook Honeypot**\n"
        "> RAT ve logger temizliği, saldırganın gizli Discord Webhook'unu ifşa etme ve tuzağa/honeypot'a yönlendirme.\n\n"
        "**🛡️ JarProtect (Geliştirici Lisans Enjektörü)**\n"
        "> Mod geliştiricileri için modlarına HWID kilidi, Süre Sınırı (TimeBomb) ve alarm webhook'u enjekte etme.\n\n"
        "**🔍 JarDiff (Bytecode Karşılaştırıcı)**\n"
        "> İki JAR arasındaki bytecode farklarını, eklenen/silinen sınıfları analiz eden differ.\n\n"
        "**📩 Komutlar:**\n"
        "`/jarobfuscator` • `/jardeobfuscator` • `/jardeobfuscationsrc` • `/jarcracker` • `/jarscanner` • `/jarclear` • `/jarprotect` • `/jardiff`",
        BRAND_COLOR)
    e.set_thumbnail(url=bot.user.display_avatar.url)
    await interaction.response.send_message(embed=e)


# ══════════════════════════════════════════════
# PUBLIC SERVER / SETUP / TICKET-VOICE / TEMP-VOICE / MOD-EXTRA
# (Ubuntu uyumlu — sadece discord.py, ek paket yok)
# ══════════════════════════════════════════════

def get_guild_cfg(guild_id) -> dict:
    all_cfg = load_json("guild_config")
    return all_cfg.get(str(guild_id), {}) if isinstance(all_cfg, dict) else {}


def set_guild_cfg(guild_id, patch: dict):
    all_cfg = load_json("guild_config")
    if not isinstance(all_cfg, dict):
        all_cfg = {}
    cur = all_cfg.get(str(guild_id), {})
    if not isinstance(cur, dict):
        cur = {}
    cur.update(patch)
    all_cfg[str(guild_id)] = cur
    save_json("guild_config", all_cfg)


def _eff(guild: discord.Guild | None, cfg_key: str, global_val: int) -> int:
    """Sunucu-ozel ayar varsa onu, yoksa .env globalini dondur."""
    try:
        if guild is not None:
            cfg = get_guild_cfg(guild.id)
            v = int(cfg.get(cfg_key, 0) or 0)
            if v:
                return v
    except Exception:
        pass
    return int(global_val or 0)


def eff_ticket_category(guild) -> int: return _eff(guild, "ticket_category", TICKET_CATEGORY)
def eff_ticket_log(guild) -> int:      return _eff(guild, "ticket_log", TICKET_LOG)
def eff_verify_role(guild) -> int:     return _eff(guild, "verify_role", VERIFY_ROLE)
def eff_auto_role(guild) -> int:       return _eff(guild, "auto_role", AUTO_ROLE)
def eff_mod_log(guild) -> int:         return _eff(guild, "mod_log", MOD_LOG_CHANNEL)
def eff_welcome(guild) -> int:         return _eff(guild, "welcome", WELCOME_CHANNEL)
def eff_starboard(guild) -> int:       return _eff(guild, "starboard", STARBOARD_CH)
def eff_staff_role(guild) -> int:      return _eff(guild, "staff_role", 0)
def eff_temp_lobby(guild) -> int:      return _eff(guild, "temp_lobby", 0)
def eff_temp_category(guild) -> int:   return _eff(guild, "temp_category", 0)


def _find_category(guild: discord.Guild, names: list[str]):
    for c in guild.categories:
        cl = c.name.lower()
        for n in names:
            if n in cl:
                return c
    return None


def _find_text(guild: discord.Guild, names: list[str]):
    for ch in guild.text_channels:
        cl = ch.name.lower()
        for n in names:
            if n in cl:
                return ch
    return None


def _find_voice(guild: discord.Guild, names: list[str]):
    for ch in guild.voice_channels:
        cl = ch.name.lower()
        for n in names:
            if n in cl:
                return ch
    return None


def _find_role(guild: discord.Guild, names: list[str]):
    for r in guild.roles:
        rl = r.name.lower()
        for n in names:
            if n in rl:
                return r
    return None


async def _setup_get_or_create_category(guild: discord.Guild, name: str):
    for c in guild.categories:
        if c.name == name:
            return c, False
    try:
        c = await guild.create_category(name)
        await asyncio.sleep(0.4)
        return c, True
    except (discord.Forbidden, discord.HTTPException):
        return None, False


async def _setup_get_or_create_text(guild: discord.Guild, category, name: str, topic: str = ""):
    for ch in guild.text_channels:
        if ch.name == name:
            # Eski kurulumdan kalma kanal baska kategorideyse dogru yere tasi
            try:
                if category is not None and ch.category_id != category.id:
                    await ch.edit(category=category)
                    await asyncio.sleep(0.3)
                    return ch, True
            except (discord.Forbidden, discord.HTTPException):
                pass
            return ch, False
    try:
        ow = None
        if name.startswith("✅") or "rules" in name:
            # kurallar herkese acik, yazma kapali
            ow = {
                guild.default_role: discord.PermissionOverwrite(view_channel=True, send_messages=False, add_reactions=False),
                guild.me: discord.PermissionOverwrite(view_channel=True, send_messages=True, manage_messages=True),
            }
        ch = await guild.create_text_channel(name, category=category, topic=topic[:100] if topic else None, overwrites=ow)
        await asyncio.sleep(0.4)
        return ch, True
    except (discord.Forbidden, discord.HTTPException):
        return None, False


async def _setup_get_or_create_voice(guild: discord.Guild, category, name: str, locked: bool = False, staff_role=None):
    for ch in guild.voice_channels:
        if ch.name == name:
            return ch, False
    try:
        ow = None
        if locked:
            ow = {guild.default_role: discord.PermissionOverwrite(view_channel=True, connect=False)}
            if staff_role is not None:
                ow[staff_role] = discord.PermissionOverwrite(view_channel=True, connect=True)
            ow[guild.me] = discord.PermissionOverwrite(view_channel=True, connect=True, move_members=True)
        ch = await guild.create_voice_channel(name, category=category, overwrites=ow)
        await asyncio.sleep(0.4)
        return ch, True
    except (discord.Forbidden, discord.HTTPException):
        return None, False


def rules_embed() -> discord.Embed:
    e = discord.Embed(title="📜 Codex Server Rules", color=BRAND_COLOR,
                      timestamp=discord.utils.utcnow())
    e.description = (
        "🚫 **No Advertising**\n"
        "Advertising of any kind is strictly prohibited and will result in an immediate ban.\n\n"
        "🚫 **No Spamming**\n"
        "Do not flood chat with repeated messages or disruptive content.\n\n"
        "❌ **No Harassment**\n"
        "Harassment in any form — including blackmail, threats, or sharing personal "
        "information — will lead to a permanent ban.\n\n"
        "📄 **Follow Discord's Terms**\n"
        "You must follow [Discord's Terms of Service](https://discord.com/terms) and "
        "[Community Guidelines](https://discord.com/guidelines) at all times."
    )
    e.set_footer(text="Codex Client • Official Rules")
    return e


def eula_embed() -> discord.Embed:
    e = discord.Embed(title="End User License Agreement (EULA) — Codex Client", color=INFO_COLOR)
    e.description = (
        "By downloading, using, or interacting with **Codex Client**, you agree to the following terms. "
        "If you do not accept these terms, you are not permitted to use Codex Client.\n\n"
        "**1. License**\nYou are granted a personal, non-transferable, revocable license to use **Codex Client**. "
        "This software is not sold or owned by the user.\n\n"
        "**2. Ownership**\nAll rights, code, and assets related to Codex Client remain the property of the developers. "
        "You may not claim any part of it as your own.\n\n"
        "**3. Data Collection**\nBy running Codex Client, you agree that the software may automatically collect "
        "and send basic data such as:\n- Date and time of use\n- Your device name\n- HWID / Hardware Identification\n\n"
        "**4. Restrictions**\nYou may not:\n- Modify, reverse-engineer, or decompile the client\n"
        "- Redistribute or **resell** it\n- Use it in any illegal or abusive way\n\n"
        "**5. Disclaimer**\nCodex Client is provided \"as is.\" No warranties are given regarding safety, stability, "
        "or functionality. Use at your own risk.\n\n"
        "*By placing Codex Client into your game files, running it, or otherwise using it, you confirm your acceptance "
        "of all terms outlined in this agreement.*"
    )
    return e


def pricing_embed() -> discord.Embed:
    e = discord.Embed(title="💎 Codex Client Pricing & Information", color=0x5865F2,
                      timestamp=discord.utils.utcnow())
    e.description = (
        "**Welcome to Codex Client!**\n\nChoose your subscription plan below:\n\n"
        "↳ **Lifetime:** `$12 / 600TRY`\n↳ **Monthly:** `$6 / 300TRY`\n\n"
        "Use our ticket system or purchase channels to get started!"
    )
    e.set_footer(text="Codex Client • Information")
    return e


def ticket_panel_embed() -> discord.Embed:
    e = discord.Embed(title="Tickets", color=0x5865F2)
    e.description = (
        "Need help? **Make a selection below** to open a ticket.\n\n"
        "**Ticket Info:**\n"
        "↳ Do not open troll tickets. This will result in a ban or blacklist.\n"
        "↳ If you didn't get a response from us, we are rather busy or sleeping, be patient.\n"
        "↳ Tickets are purged regularly. If your ticket gets deleted/closed and you still have issues please re-create."
    )
    e.set_footer(text="Codex Client • Support Team")
    return e


def bot_info_embed() -> discord.Embed:
    e = discord.Embed(title="🤖 SuS Cracker Suite — Bot Bilgi", color=BRAND_COLOR,
                      timestamp=discord.utils.utcnow())
    e.description = (
        "**Minecraft JAR guvenlik ve analiz botu** — obfuscate, deobfuscate, crack, tara, temizle.\n\n"
        "🛡️ **Obfuscator** — polimorfik string sifreleme, control-flow flattening, Unicode gizleme\n"
        "⚡ **Deobfuscator** — CFR + Vineflower cift motor, temiz JAR + `.java` kaynak ZIP\n"
        "🔓 **Cracker (43 metot)** — Meteor Addon, Fabric/Forge mod, HWID/KeyAuth bypass\n"
        "🔍 **Scanner** — RAT, webhook logger, zararli kod taramasi\n"
        "🧹 **JarClear** — zararli kodu temizler + saldirgan webhook'unu honeypot'a yonlendirir\n"
        "🛡️ **JarProtect** — moduna HWID kilidi + sure siniri enjekte eder\n\n"
        "⚙️ **Motor:** ASM 9.7 • Java 21 • `/health` ile durumu gorebilirsin."
    )
    e.set_footer(text="SuS Cracker Suite • /help yazarak basla")
    return e


def bot_commands_embed() -> discord.Embed:
    e = discord.Embed(title="⌨️ Bot Komutları", color=INFO_COLOR)
    e.description = (
        "**🛡️ Bytecode**\n"
        "`/jarobfuscator` • `/jardeobfuscator` • `/jardeobfuscationsrc`\n"
        "`/jarcracker` • `/jarscanner` • `/jarclear` • `/jarprotect` • `/jardiff`\n\n"
        "**🎫 Destek**\n"
        "`/ticket-panel` ile buton kur, kategoriden secerek ticket ac.\n"
        "Ticket acilinca sana ozel **ses kanali** da acilir.\n\n"
        "**🎧 Ses**\n"
        "`➕・Ses Oluştur` kanalina gir → sana ozel oda acilir.\n"
        "`/vname` • `/vlimit` • `/vlock` • `/vunlock` • `/vclaim`\n\n"
        "**👮 Moderasyon**\n"
        "`/ban` • `/kick` • `/mute` • `/warn` • `/clear`\n"
        "`/lock` • `/unlock` • `/slowmode` • `/nuke`\n\n"
        "**📈 Diger**\n"
        "`/rank` • `/leaderboard` • `/afk` • `/poll` • `/giveaway` • `/help`"
    )
    e.set_footer(text="Detay icin /help • Yetkili komutlar icin yetki gerekir")
    return e


def bot_faq_embed() -> discord.Embed:
    e = discord.Embed(title="❓ Sık Sorulan Sorular (SSS)", color=SUCCESS_COLOR)
    e.description = (
        "**Bot nasil kullanilir?**\n"
        "JAR dosyani komuta ek olarak yukle, örn: `/jarscanner` + dosyan. "
        "Sonuc kisa surer, buyuk dosyalarda biraz bekle.\n\n"
        "**Dosya boyutu siniri nedir?**\n"
        "Standart uye 20MB, VIP/Booster sinirsiz yakin limit + gunluk kota yok. "
        "Discord'un 25MB yukleme siniri nedeniyle cikti 25MB'i gecemez.\n\n"
        "**Ticket nasil acilir?**\n"
        "`🎫・ticket` kanalindaki butona bas, kategoriyi sec. Troll ticket acmak ban sebebidir.\n\n"
        "**Obfuscation hatasi aliyorum?**\n"
        "Girdigin dosyanin gercekten `.jar` oldugundan emin ol, bozuk/kirik JAR'lar islenemez. "
        "Hata surerse dosyayla birlikte yeni ticket ac.\n\n"
        "**VIP nasil olurum?**\n"
        "Ticket acarak bilgi alabilirsin."
    )
    e.set_footer(text="Sorun cozulmedi mi? Ticket ac • SuS Destek")
    return e


@bot.tree.command(name="setup", description="🛠️ Public sunucu kurulumu: roller, kanallar, sesler, ticket ve bilgi panelleri.")
@app_commands.describe(temizle="True ise önceki setup kanallarını silip sıfırdan kur (dikkat!)")
@app_commands.default_permissions(manage_guild=True)
async def setup_cmd(interaction: discord.Interaction, temizle: bool = False):
    if interaction.guild is None:
        return await interaction.response.send_message("❌ Bu komut sadece sunucu içinde kullanılabilir.", ephemeral=True)
    guild = interaction.guild
    me = guild.me
    need = ["manage_channels", "manage_roles", "manage_messages", "move_members"]
    missing = [p for p in need if not getattr(me.guild_permissions, p, False)]
    if missing:
        return await interaction.response.send_message(
            f"❌ Bot yetkisi eksik: `{', '.join(missing)}`. Bot rolünü en üste alıp bu yetkileri ver.", ephemeral=True)
    await safe_defer(interaction, thinking=True)
    created, skipped, errors = [], [], []

    async def _maybe_delete_setup():
        if not temizle:
            # Client bolumu artik "» Bot" — eski Client kanallarini sessizce tasi/temizle
            try:
                legacy = [c for c in guild.text_channels
                          if c.name in ("ℹ️・client-info", "📄・eula-faq", "👀・sneak-peak", "⛏️・base-finds")]
                for ch in legacy:
                    try:
                        await ch.delete(reason="setup: Client bolumu Bot ile degisti")
                        await asyncio.sleep(0.3)
                    except Exception:
                        pass
                old_cat = discord.utils.get(guild.categories, name="» Client")
                if old_cat is not None and len(old_cat.channels) == 0:
                    try:
                        await old_cat.delete(reason="setup: Client kategorisi kaldirildi")
                    except Exception:
                        pass
            except Exception:
                pass
            return
        targets = ["rules", "announcements", "giveaways", "update", "client-info", "eula-faq",
                   "sneak-peak", "base-finds", "reviews", "ticket", "media-info", "media-vids",
                   "bot-info", "・commands", "・faq",
                   "chat", "ticket-logs", "mod-log", "welcome", "staff voice", "voice 1", "voice 2",
                   "ses oluştur", "tickets", "important", "client", "» bot", "media", "codex chat", "voices"]
        for ch in list(guild.channels):
            try:
                if any(t in ch.name.lower() for t in targets):
                    await ch.delete(reason="setup temizle")
                    await asyncio.sleep(0.3)
            except Exception:
                pass

    await _maybe_delete_setup()

    # ── Roller ──
    roles_want = {
        "Staff": {"color": 0xE74C3C, "perms": ["manage_messages", "kick_members", "mute_members"]},
        "Support": {"color": 0x3498DB, "perms": ["manage_messages"]},
        "Verified": {"color": 0x2ECC71, "perms": []},
        "Member": {"color": 0x95A5A6, "perms": []},
        "VIP": {"color": 0xF1C40F, "perms": []},
    }
    role_ids = {}
    for rname, spec in roles_want.items():
        r = discord.utils.get(guild.roles, name=rname)
        if r is None:
            try:
                r = await guild.create_role(name=rname, color=discord.Color(spec["color"]),
                                            mentionable=(rname in ("Staff", "Support")))
                await asyncio.sleep(0.4)
                created.append(f"rol:{rname}")
            except Exception as ex:
                errors.append(f"rol:{rname} ({ex})")
                continue
        else:
            skipped.append(f"rol:{rname}")
        role_ids[rname] = r.id
    staff_role = guild.get_role(role_ids.get("Staff", 0))
    verified_role = guild.get_role(role_ids.get("Verified", 0))
    member_role = guild.get_role(role_ids.get("Member", 0))

    # ── Kategoriler ──
    cats = {}
    for cname in ["» Important", "» Bot", "» Media", "Codex Chat", "🎫 Tickets", "🔊 Voices"]:
        c, is_new = await _setup_get_or_create_category(guild, cname)
        if c is None:
            errors.append(f"kategori:{cname}")
            continue
        cats[cname] = c
        (created if is_new else skipped).append(f"kategori:{cname}")

    # ── Metin kanalları (» Bot: bot bilgi / komut / SSS agirlikli) ──
    want_text = [
        ("» Important", "✅・rules", "Sunucu kurallari — lutfen okuyun."),
        ("» Important", "📢・announcements", "Resmi duyurular."),
        ("» Important", "🎉・giveaways", "Cekilisler."),
        ("» Important", "🛡️・mod-log", "Moderasyon kayitlari (sadece yetkili)."),
        ("» Important", "👋・welcome", "Hos geldin / gule gule mesajlari."),
        ("» Bot", "🤖・bot-info", "Bot hakkinda: ozellikler ve motor bilgisi."),
        ("» Bot", "⌨️・commands", "Tum komutlarin listesi."),
        ("» Bot", "🔄・update", "Guncelleme notlari."),
        ("» Bot", "❓・faq", "Sik sorulan sorular."),
        ("» Bot", "⭐・reviews", "Kullanici yorumlari."),
        ("» Bot", "🎫・ticket", "Destek talebi olustur."),
        ("» Media", "ℹ️・media-info", "Medya kurallari ve bilgi."),
        ("» Media", "🎬・media-vids", "Video ve medya paylasimlari."),
        ("Codex Chat", "💬・chat", "Genel sohbet."),
        ("🎫 Tickets", "📋・ticket-logs", "Ticket transkript kayitlari (sadece yetkili)."),
    ]
    text_ids = {}
    for cat_name, ch_name, topic in want_text:
        cat = cats.get(cat_name)
        if cat is None:
            errors.append(f"kanal:{ch_name} (kategori yok)")
            continue
        ch, is_new = await _setup_get_or_create_text(guild, cat, ch_name, topic)
        if ch is None:
            errors.append(f"kanal:{ch_name}")
            continue
        text_ids[ch_name] = ch.id
        (created if is_new else skipped).append(f"kanal:{ch_name}")
        if ch_name in ("🛡️・mod-log", "📋・ticket-logs") and staff_role is not None:
            try:
                await ch.set_permissions(guild.default_role, view_channel=False)
                await ch.set_permissions(staff_role, view_channel=True, send_messages=True, read_message_history=True)
            except Exception:
                pass

    # ── Ses kanalları (ekran 1 birebir + olusturma lobisi) ──
    vcat = cats.get("🔊 Voices")
    voice_ids = {}
    if vcat is not None:
        for vname, locked in [("🔒 Staff Voice", True), ("Voice 1", False), ("Voice 2", False), ("➕・Ses Oluştur", False)]:
            ch, is_new = await _setup_get_or_create_voice(guild, vcat, vname, locked=locked, staff_role=staff_role)
            if ch is None:
                errors.append(f"ses:{vname}")
                continue
            voice_ids[vname] = ch.id
            (created if is_new else skipped).append(f"ses:{vname}")

    # ── Bilgi panellerini gonder (varsa tekrar gonderme) ──
    async def _send_once(ch_id: int, embed: discord.Embed, view=None, marker: str = ""):
        if not ch_id:
            return
        ch = guild.get_channel(ch_id)
        if ch is None or not isinstance(ch, discord.TextChannel):
            return
        try:
            async for m in ch.history(limit=20):
                if m.author == me and m.embeds and marker and marker in (m.embeds[0].title or ""):
                    return
        except Exception:
            pass
        try:
            await ch.send(embed=embed, view=view)
            await asyncio.sleep(0.4)
        except Exception as ex:
            errors.append(f"panel:{marker} ({ex})")

    await _send_once(text_ids.get("✅・rules", 0), rules_embed(), marker="Rules")
    await _send_once(text_ids.get("🤖・bot-info", 0), bot_info_embed(), marker="SuS Cracker Suite")
    await _send_once(text_ids.get("⌨️・commands", 0), bot_commands_embed(), marker="Komutlar")
    await _send_once(text_ids.get("❓・faq", 0), bot_faq_embed(), marker="Sorulan")
    ticket_ch = guild.get_channel(text_ids.get("🎫・ticket", 0))
    if ticket_ch is not None and isinstance(ticket_ch, discord.TextChannel):
        try:
            found = False
            async for m in ticket_ch.history(limit=20):
                if m.author == me and m.embeds and "Tickets" in (m.embeds[0].title or ""):
                    found = True
                    break
            if not found:
                await ticket_ch.send(embed=ticket_panel_embed(), view=TicketView())
        except Exception as ex:
            errors.append(f"panel:Tickets ({ex})")

    # ── Sunucu ayarlarini kaydet (JSON — .env globalini ezer, Ubuntu'da kalici) ──
    try:
        tcat = cats.get("🎫 Tickets")
        set_guild_cfg(guild.id, {
            "ticket_category": (tcat.id if tcat else 0),
            "ticket_log": text_ids.get("📋・ticket-logs", 0),
            "mod_log": text_ids.get("🛡️・mod-log", 0),
            "welcome": text_ids.get("👋・welcome", 0),
            "verify_role": (verified_role.id if verified_role else 0),
            "auto_role": (member_role.id if member_role else 0),
            "staff_role": (staff_role.id if staff_role else 0),
            "support_role": role_ids.get("Support", 0),
            "temp_lobby": voice_ids.get("➕・Ses Oluştur", 0),
            "temp_category": (vcat.id if vcat else 0),
            "ticket_voice_category": (vcat.id if vcat else 0),
        })
    except Exception as ex:
        errors.append(f"ayar-kayit ({ex})")

    e = mk_embed("🛠️ Kurulum Tamamlandı", color=SUCCESS_COLOR)
    e.add_field(name="✅ Oluşturulan", value="\n".join(f"`{c}`" for c in created[:25]) or "*yok (hepsi mevcut)*", inline=True)
    e.add_field(name="⏭️ Zaten vardı", value="\n".join(f"`{c}`" for c in skipped[:25]) or "*yok*", inline=True)
    if errors:
        e.add_field(name="⚠️ Hatalar", value="\n".join(f"`{x}`" for x in errors[:10]), inline=False)
    e.add_field(name="📌 Sonraki adım",
                value="• `/ticket-panel` ile istedigin kanala ticket butonu ekle\n"
                      "• `/verify-panel` ile dogrulama butonu gonder\n"
                      "• Ses: `➕・Ses Oluştur` kanalina giren kisiye ozel oda acilir\n"
                      "• Ticket acilinca otomatik **ozel ses kanali** da acilir",
                inline=False)
    await safe_followup(interaction, embed=e)


# ─── TICKET SES KANALI (ticket acilinca ozel ses, kapaninca sil) ───
async def _ticket_voice_create(guild: discord.Guild, text_ch: discord.TextChannel, user: discord.Member, staff_role=None):
    cfg = get_guild_cfg(guild.id)
    cat_id = int(cfg.get("ticket_voice_category", 0) or cfg.get("temp_category", 0) or 0)
    category = guild.get_channel(cat_id) if cat_id else None
    if category is None or not isinstance(category, discord.CategoryChannel):
        category = _find_category(guild, ["ticket", "voice"])
    vname = f"🔊-{text_ch.name}"
    ow = {
        guild.default_role: discord.PermissionOverwrite(view_channel=False, connect=False),
        user: discord.PermissionOverwrite(view_channel=True, connect=True, speak=True),
        guild.me: discord.PermissionOverwrite(view_channel=True, connect=True, move_members=True, manage_channels=True),
    }
    if staff_role is not None:
        ow[staff_role] = discord.PermissionOverwrite(view_channel=True, connect=True, speak=True)
    try:
        vc = await guild.create_voice_channel(vname, category=category, overwrites=ow,
                                              reason=f"Ticket ses: {text_ch.name}")
        tdata = load_json("tickets")
        info = tdata.get(str(text_ch.id), {})
        if isinstance(info, dict):
            info["voice_id"] = vc.id
            tdata[str(text_ch.id)] = info
            save_json("tickets", tdata)
        return vc
    except (discord.Forbidden, discord.HTTPException):
        return None


async def _ticket_voice_delete(guild: discord.Guild, text_channel_id: int):
    try:
        tdata = load_json("tickets")
        info = tdata.get(str(text_channel_id), {})
        vid = info.get("voice_id", 0) if isinstance(info, dict) else 0
        if vid:
            vc = guild.get_channel(int(vid))
            if vc is not None:
                try:
                    await vc.delete(reason="Ticket kapandi")
                except Exception:
                    pass
    except Exception:
        pass


@bot.tree.command(name="ticketvoice", description="🎫 Mevcut ticket'a özel ses kanalı aç.")
@app_commands.default_permissions(manage_channels=True)
async def ticketvoice_cmd(interaction: discord.Interaction, user: discord.Member | None = None):
    if interaction.guild is None or not isinstance(interaction.channel, discord.TextChannel):
        return await interaction.response.send_message("❌ Bu komut ticket kanalında kullanılır.", ephemeral=True)
    ch = interaction.channel
    if not (ch.name.startswith("ticket-") or ch.name.startswith("t-")):
        return await interaction.response.send_message("❌ Bu bir ticket kanalı değil.", ephemeral=True)
    target = user or interaction.user
    if not isinstance(target, discord.Member):
        return await interaction.response.send_message("❌ Üye bulunamadı.", ephemeral=True)
    await safe_defer(interaction, thinking=True)
    staff_role = None
    sr = eff_staff_role(interaction.guild)
    if sr:
        staff_role = interaction.guild.get_role(sr)
    vc = await _ticket_voice_create(interaction.guild, ch, target, staff_role)
    if vc is None:
        return await safe_followup(interaction, "❌ Ses kanalı açılamadı (yetki/kategori sorunu).")
    await safe_followup(interaction, f"🔊 Ses kanalı açıldı: {vc.mention} (sadece {target.mention} + yetkili girebilir)")


# ─── TEMP VOICE (➕ lobiye girene özel oda — ekran 1) ───
@bot.event
async def on_voice_state_update(member: discord.Member, before: discord.VoiceState, after: discord.VoiceState):
    if member.bot or member.guild is None:
        return
    guild = member.guild
    try:
        cfg = get_guild_cfg(guild.id)
        lobby_id = int(cfg.get("temp_lobby", 0) or 0)
        tcat_id = int(cfg.get("temp_category", 0) or 0)
        if not lobby_id:
            lobby = _find_voice(guild, ["ses oluştur", "ses olustur", "create", "join to create", "➕"])
            lobby_id = lobby.id if lobby else 0
        tcat = guild.get_channel(tcat_id) if tcat_id else None
        if tcat is None or not isinstance(tcat, discord.CategoryChannel):
            tcat = _find_category(guild, ["voice", "ses"])

        # Lobiye girdi -> ozel oda kur ve tasi
        if after.channel is not None and after.channel.id == lobby_id:
            try:
                ow = {
                    guild.default_role: discord.PermissionOverwrite(view_channel=True, connect=False),
                    member: discord.PermissionOverwrite(view_channel=True, connect=True, speak=True,
                                                       manage_channels=True, move_members=True, mute_members=True),
                    guild.me: discord.PermissionOverwrite(view_channel=True, connect=True, move_members=True),
                }
                sr = eff_staff_role(guild)
                if sr:
                    sr_obj = guild.get_role(sr)
                    if sr_obj is not None:
                        ow[sr_obj] = discord.PermissionOverwrite(view_channel=True, connect=True)
                vc = await guild.create_voice_channel(f"🎧 {member.display_name}", category=tcat, overwrites=ow,
                                                      user_limit=5, reason="Temp voice")
                tv = load_json("temp_voices")
                if not isinstance(tv, dict):
                    tv = {}
                tv[str(vc.id)] = {"owner": member.id, "guild": guild.id}
                save_json("temp_voices", tv)
                try:
                    await member.move_to(vc, reason="Temp voice olustu")
                except (discord.Forbidden, discord.HTTPException):
                    pass
            except (discord.Forbidden, discord.HTTPException):
                pass

        # Bosalan temp odayi sil
        if before.channel is not None and before.channel.id != (after.channel.id if after.channel else 0):
            try:
                tv = load_json("temp_voices")
                if isinstance(tv, dict) and str(before.channel.id) in tv:
                    vc = guild.get_channel(before.channel.id)
                    if vc is not None and isinstance(vc, discord.VoiceChannel) and len(vc.members) == 0:
                        try:
                            await vc.delete(reason="Temp voice bosaldi")
                        except Exception:
                            pass
                        tv.pop(str(before.channel.id), None)
                        save_json("temp_voices", tv)
            except Exception:
                pass
    except Exception:
        pass


def _temp_owner_voice(interaction: discord.Interaction):
    if interaction.guild is None or not isinstance(interaction.user, discord.Member):
        return None, "❌ Sunucu içinde kullan."
    if interaction.user.voice is None or interaction.user.voice.channel is None:
        return None, "❌ Önce bir ses kanalına gir."
    vc = interaction.user.voice.channel
    tv = load_json("temp_voices")
    info = tv.get(str(vc.id)) if isinstance(tv, dict) else None
    if info is None:
        return None, "❌ Bu komut sadece `➕・Ses Oluştur` ile açılan özel odalarda çalışır."
    if int(info.get("owner", 0)) != interaction.user.id and not interaction.user.guild_permissions.manage_channels:
        return None, "❌ Sadece oda sahibi kullanabilir."
    return vc, ""


@bot.tree.command(name="vname", description="🎧 Özel ses odanın adını değiştir.")
async def vname_cmd(interaction: discord.Interaction, isim: str):
    vc, err = _temp_owner_voice(interaction)
    if vc is None:
        return await interaction.response.send_message(err, ephemeral=True)
    try:
        await vc.edit(name=f"🎧 {isim[:40]}", reason=f"{interaction.user}")
        await interaction.response.send_message(f"✅ Oda adı: `{vc.name}`")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="vlimit", description="🎧 Özel ses odanın kişi sınırını ayarla (0=sınırsız).")
async def vlimit_cmd(interaction: discord.Interaction, limit: int = 0):
    vc, err = _temp_owner_voice(interaction)
    if vc is None:
        return await interaction.response.send_message(err, ephemeral=True)
    limit = max(0, min(limit, 99))
    try:
        await vc.edit(user_limit=limit, reason=f"{interaction.user}")
        await interaction.response.send_message(f"✅ Oda limiti: `{limit if limit else 'sınırsız'}`")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="vlock", description="🔒 Özel ses odanı kilitle (kimse giremez).")
async def vlock_cmd(interaction: discord.Interaction):
    vc, err = _temp_owner_voice(interaction)
    if vc is None:
        return await interaction.response.send_message(err, ephemeral=True)
    try:
        await vc.set_permissions(interaction.guild.default_role, view_channel=True, connect=False)
        await interaction.response.send_message("🔒 Oda kilitlendi.")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="vunlock", description="🔓 Özel ses odanın kilidini aç.")
async def vunlock_cmd(interaction: discord.Interaction):
    vc, err = _temp_owner_voice(interaction)
    if vc is None:
        return await interaction.response.send_message(err, ephemeral=True)
    try:
        await vc.set_permissions(interaction.guild.default_role, view_channel=True, connect=True)
        await interaction.response.send_message("🔓 Oda açıldı.")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="vclaim", description="👑 Boş kalan özel odanın sahipliğini al.")
async def vclaim_cmd(interaction: discord.Interaction):
    if interaction.guild is None or not isinstance(interaction.user, discord.Member):
        return await interaction.response.send_message("❌ Sunucu içinde kullan.", ephemeral=True)
    if interaction.user.voice is None or interaction.user.voice.channel is None:
        return await interaction.response.send_message("❌ Önce odaya gir.", ephemeral=True)
    vc = interaction.user.voice.channel
    tv = load_json("temp_voices")
    info = tv.get(str(vc.id)) if isinstance(tv, dict) else None
    if info is None:
        return await interaction.response.send_message("❌ Bu özel oda değil.", ephemeral=True)
    owner = interaction.guild.get_member(int(info.get("owner", 0) or 0))
    if owner is not None and owner in vc.members:
        return await interaction.response.send_message("❌ Sahip hâlâ odada.", ephemeral=True)
    try:
        await vc.set_permissions(interaction.user, view_channel=True, connect=True, speak=True,
                                 manage_channels=True, move_members=True, mute_members=True)
        if isinstance(tv, dict):
            tv[str(vc.id)] = {"owner": interaction.user.id, "guild": interaction.guild.id}
            save_json("temp_voices", tv)
        await interaction.response.send_message("👑 Oda sahipliği sana geçti.")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


# ─── SES MODERASYON (Staff Voice dahil her odada) ───
async def _voice_target(interaction: discord.Interaction, user: discord.Member):
    if interaction.guild is None:
        return None, "❌ Sunucu içinde kullan."
    if user.voice is None or user.voice.channel is None:
        return None, f"❌ {user.display_name} bir ses kanalında değil."
    return user.voice.channel, ""


@bot.tree.command(name="vkick", description="🔇 Kullanıcıyı ses kanalından at.")
@app_commands.describe(user="Kullanıcı")
@app_commands.default_permissions(move_members=True)
async def vkick_cmd(interaction: discord.Interaction, user: discord.Member):
    vc, err = await _voice_target(interaction, user)
    if vc is None:
        return await interaction.response.send_message(err, ephemeral=True)
    try:
        await user.move_to(None, reason=f"Voice kick: {interaction.user}")
        await interaction.response.send_message(f"✅ {user.mention} sesten atıldı.")
        await _mod_log(interaction.guild, "🔇 Voice Kick",
                       f"**Kullanıcı:** {user} ({user.id})\n**Kanal:** {vc.name}\n**Yetkili:** {interaction.user}")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="vmute", description="🔇 Kullanıcıyı seste sustur.")
@app_commands.describe(user="Kullanıcı")
@app_commands.default_permissions(mute_members=True)
async def vmute_cmd(interaction: discord.Interaction, user: discord.Member):
    vc, err = await _voice_target(interaction, user)
    if vc is None:
        return await interaction.response.send_message(err, ephemeral=True)
    try:
        await user.edit(mute=True, reason=f"Voice mute: {interaction.user}")
        await interaction.response.send_message(f"🔇 {user.mention} susturuldu.")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="vunmute", description="🔊 Kullanıcının ses susturmasını kaldır.")
@app_commands.describe(user="Kullanıcı")
@app_commands.default_permissions(mute_members=True)
async def vunmute_cmd(interaction: discord.Interaction, user: discord.Member):
    try:
        await user.edit(mute=False, reason=f"Voice unmute: {interaction.user}")
        await interaction.response.send_message(f"🔊 {user.mention} susturması kaldırıldı.")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="vmove", description="↔️ Kullanıcıyı başka ses kanalına taşı.")
@app_commands.describe(user="Kullanıcı", kanal="Hedef ses kanalı")
@app_commands.default_permissions(move_members=True)
async def vmove_cmd(interaction: discord.Interaction, user: discord.Member, kanal: discord.VoiceChannel):
    try:
        await user.move_to(kanal, reason=f"Voice move: {interaction.user}")
        await interaction.response.send_message(f"✅ {user.mention} → {kanal.mention} taşındı.")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


# ─── KANAL MODERASYON EXTRA ───
@bot.tree.command(name="lock", description="🔒 Kanalı kilitle (herkes yazamaz).")
@app_commands.describe(kanal="Kanal (boşsa bu kanal)")
@app_commands.default_permissions(manage_channels=True)
async def lock_cmd(interaction: discord.Interaction, kanal: discord.TextChannel | None = None):
    ch = kanal or interaction.channel
    if not isinstance(ch, discord.TextChannel):
        return await interaction.response.send_message("❌ Metin kanalı seç.", ephemeral=True)
    try:
        await ch.set_permissions(interaction.guild.default_role, send_messages=False)
        await interaction.response.send_message(f"🔒 {ch.mention} kilitlendi.")
        await _mod_log(interaction.guild, "🔒 Lock", f"**Kanal:** {ch.mention}\n**Yetkili:** {interaction.user}")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="unlock", description="🔓 Kanal kilidini aç.")
@app_commands.describe(kanal="Kanal (boşsa bu kanal)")
@app_commands.default_permissions(manage_channels=True)
async def unlock_cmd(interaction: discord.Interaction, kanal: discord.TextChannel | None = None):
    ch = kanal or interaction.channel
    if not isinstance(ch, discord.TextChannel):
        return await interaction.response.send_message("❌ Metin kanalı seç.", ephemeral=True)
    try:
        await ch.set_permissions(interaction.guild.default_role, send_messages=None)
        await interaction.response.send_message(f"🔓 {ch.mention} açıldı.")
        await _mod_log(interaction.guild, "🔓 Unlock", f"**Kanal:** {ch.mention}\n**Yetkili:** {interaction.user}")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="slowmode", description="🐌 Kanala yavaş mod uygula (0=kapat).")
@app_commands.describe(saniye="0-21600 arası saniye", kanal="Kanal (boşsa bu kanal)")
@app_commands.default_permissions(manage_channels=True)
async def slowmode_cmd(interaction: discord.Interaction, saniye: int = 5, kanal: discord.TextChannel | None = None):
    ch = kanal or interaction.channel
    if not isinstance(ch, discord.TextChannel):
        return await interaction.response.send_message("❌ Metin kanalı seç.", ephemeral=True)
    saniye = max(0, min(saniye, 21600))
    try:
        await ch.edit(slowmode_delay=saniye, reason=f"Slowmode: {interaction.user}")
        await interaction.response.send_message(f"🐌 {ch.mention} yavaş mod: `{saniye}sn`")
    except discord.Forbidden:
        await interaction.response.send_message("❌ Yetkim yok.", ephemeral=True)


@bot.tree.command(name="nuke", description="💥 Kanalı silip aynı ayarlarla yeniden oluştur (mesajlar gider).")
@app_commands.describe(kanal="Kanal (boşsa bu kanal)")
@app_commands.default_permissions(manage_channels=True)
async def nuke_cmd(interaction: discord.Interaction, kanal: discord.TextChannel | None = None):
    ch = kanal or interaction.channel
    if not isinstance(ch, discord.TextChannel):
        return await interaction.response.send_message("❌ Metin kanalı seç.", ephemeral=True)
    if interaction.guild is None:
        return await interaction.response.send_message("❌ Sunucu içinde kullan.", ephemeral=True)
    pos, cat, ow, topic, nsfw, slow = ch.position, ch.category, ch.overwrites, ch.topic, ch.nsfw, ch.slowmode_delay
    name = ch.name
    try:
        if interaction.response.is_done():
            pass
        else:
            await interaction.response.send_message(f"💥 `{name}` temizleniyor...", ephemeral=True)
        await ch.delete(reason=f"Nuke: {interaction.user}")
        await asyncio.sleep(0.6)
        new_ch = await interaction.guild.create_text_channel(
            name, category=cat, overwrites=ow, topic=topic, nsfw=nsfw,
            slowmode_delay=slow, position=pos, reason=f"Nuke: {interaction.user}")
        await new_ch.send(embed=mk_embed("💥 Kanal Temizlendi",
                                         f"Bu kanal {interaction.user.mention} tarafından sıfırlandı.", SUCCESS_COLOR))
        await _mod_log(interaction.guild, "💥 Nuke", f"**Kanal:** #{name}\n**Yetkili:** {interaction.user}")
    except discord.Forbidden:
        await safe_followup(interaction, "❌ Yetkim yok.", ephemeral=True)
    except discord.HTTPException:
        await safe_followup(interaction, "❌ Kanal yeniden oluşturulamadı.", ephemeral=True)


# ─── PUBLIC BILGI KOMUTLARI (kurallari/eula/fiyati her kanala gonder) ───
@bot.tree.command(name="rules", description="📜 Sunucu kurallarını bu kanala gönder.")
@app_commands.default_permissions(manage_guild=True)
async def rules_cmd(interaction: discord.Interaction):
    await interaction.response.send_message(embed=rules_embed())


@bot.tree.command(name="eula", description="📄 EULA metnini bu kanala gönder.")
@app_commands.default_permissions(manage_guild=True)
async def eula_cmd(interaction: discord.Interaction):
    await interaction.response.send_message(embed=eula_embed())


@bot.tree.command(name="pricing", description="💎 Fiyat/bilgi panelini bu kanala gönder.")
@app_commands.default_permissions(manage_guild=True)
async def pricing_cmd(interaction: discord.Interaction):
    await interaction.response.send_message(embed=pricing_embed())


@bot.tree.command(name="announce", description="📢 Duyuru yayınla (embed).")
@app_commands.describe(baslik="Duyuru başlığı", mesaj="Duyuru metni", kanal="Hedef kanal (boşsa bu kanal)")
@app_commands.default_permissions(manage_messages=True)
async def announce_cmd(interaction: discord.Interaction, baslik: str, mesaj: str,
                       kanal: discord.TextChannel | None = None):
    ch = kanal or interaction.channel
    if not isinstance(ch, discord.TextChannel):
        return await interaction.response.send_message("❌ Metin kanalı seç.", ephemeral=True)
    e = mk_embed(f"📢 {baslik[:100]}", mesaj[:3500], BRAND_COLOR)
    e.set_author(name=interaction.user.display_name, icon_url=interaction.user.display_avatar.url)
    try:
        await ch.send(embed=e)
        if ch.id != interaction.channel_id:
            await interaction.response.send_message(f"✅ Duyuru gönderildi: {ch.mention}", ephemeral=True)
        else:
            await interaction.response.send_message("✅ Duyuru yayınlandı.", ephemeral=True)
    except discord.Forbidden:
        await interaction.response.send_message("❌ O kanala yazma yetkim yok.", ephemeral=True)


# ──────────────────────────────────────────────
# ENTRYPOINT
# ──────────────────────────────────────────────
if __name__ == "__main__":
    if TOKEN == "YOUR_BOT_TOKEN_HERE" or not TOKEN:
        print("=" * 62)
        print("[!] .env dosyasına DISCORD_BOT_TOKEN giriniz!")
        print("[!] Örnek: DISCORD_BOT_TOKEN=MTIzNDU2Nzg5...")
        print("[!]")
        print("[!] Opsiyonel ayarlar (0 bırakılabilir):")
        print("[!]   GUILD_ID, VERIFY_ROLE, AUTO_ROLE,")
        print("[!]   TICKET_CATEGORY, TICKET_LOG, WELCOME_CHANNEL,")
        print("[!]   MOD_LOG_CHANNEL, STARBOARD_CHANNEL, STAR_THRESHOLD")
        print("=" * 62)
        sys.exit(1)
    bot.run(TOKEN)
