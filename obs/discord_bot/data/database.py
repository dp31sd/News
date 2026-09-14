import os
import time
import json
from pathlib import Path
import aiosqlite

BASE_DIR = Path(__file__).parent.parent.resolve()
DATA_DIR = BASE_DIR / "data"
DB_PATH  = DATA_DIR / "bot_database.db"

DATA_DIR.mkdir(exist_ok=True)

async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("PRAGMA journal_mode = WAL")
        await db.execute("PRAGMA synchronous = NORMAL")

        # XP & Seviye Tablosu
        await db.execute("""
            CREATE TABLE IF NOT EXISTS users_xp (
                user_id TEXT PRIMARY KEY,
                xp INTEGER NOT NULL DEFAULT 0,
                level INTEGER NOT NULL DEFAULT 1,
                last_xp REAL NOT NULL DEFAULT 0
            )
        """)

        # Uyarılar Tablosu
        await db.execute("""
            CREATE TABLE IF NOT EXISTS warns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                reason TEXT NOT NULL,
                moderator TEXT NOT NULL,
                timestamp REAL NOT NULL
            )
        """)

        # AFK Tablosu
        await db.execute("""
            CREATE TABLE IF NOT EXISTS afk (
                user_id TEXT PRIMARY KEY,
                reason TEXT NOT NULL,
                timestamp REAL NOT NULL
            )
        """)

        # Starboard Tablosu
        await db.execute("""
            CREATE TABLE IF NOT EXISTS starboard (
                msg_id TEXT PRIMARY KEY,
                stars INTEGER NOT NULL,
                author TEXT NOT NULL
            )
        """)

        # Ticket Tablosu
        await db.execute("""
            CREATE TABLE IF NOT EXISTS tickets (
                channel_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'Genel Destek',
                opened_at REAL NOT NULL
            )
        """)

        # Kullanıcı Günlük Kota Tablosu
        await db.execute("""
            CREATE TABLE IF NOT EXISTS user_quotas (
                user_id TEXT NOT NULL,
                date_str TEXT NOT NULL,
                ops_count INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (user_id, date_str)
            )
        """)

        # SHA-256 Tehdit Tarama Cache Tablosu
        await db.execute("""
            CREATE TABLE IF NOT EXISTS scan_cache (
                sha256 TEXT PRIMARY KEY,
                threat_score INTEGER NOT NULL,
                verdict TEXT NOT NULL,
                classes INTEGER NOT NULL,
                silentnet INTEGER NOT NULL,
                payloads_json TEXT NOT NULL,
                loaders_json TEXT NOT NULL,
                injections_json TEXT NOT NULL,
                findings_json TEXT NOT NULL,
                raw_output TEXT NOT NULL,
                cached_at REAL NOT NULL
            )
        """)

        await db.commit()

    # Eski JSON verilerini SQLite'a taşı (migration)
    await _migrate_legacy_json()

async def _migrate_legacy_json():
    # 1. xp.json
    xp_file = DATA_DIR / "xp.json"
    if xp_file.exists():
        try:
            with open(xp_file, encoding="utf-8") as f:
                data = json.load(f)
            async with aiosqlite.connect(DB_PATH) as db:
                for uid, d in data.items():
                    await db.execute("""
                        INSERT OR IGNORE INTO users_xp (user_id, xp, level, last_xp)
                        VALUES (?, ?, ?, ?)
                    """, (uid, d.get("xp", 0), d.get("level", 1), 0))
                await db.commit()
        except Exception:
            pass

    # 2. afk.json
    afk_file = DATA_DIR / "afk.json"
    if afk_file.exists():
        try:
            with open(afk_file, encoding="utf-8") as f:
                data = json.load(f)
            async with aiosqlite.connect(DB_PATH) as db:
                for uid, d in data.items():
                    await db.execute("""
                        INSERT OR IGNORE INTO afk (user_id, reason, timestamp)
                        VALUES (?, ?, ?)
                    """, (uid, d.get("reason", "Belirtilmedi"), d.get("time", time.time())))
                await db.commit()
        except Exception:
            pass

# ─── XP / RANK METOTLARI ──────────────────────
async def get_user_xp(user_id: str):
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT xp, level, last_xp FROM users_xp WHERE user_id = ?", (user_id,)) as cur:
            row = await cur.fetchone()
            if row:
                return {"xp": row[0], "level": row[1], "last_xp": row[2]}
            return {"xp": 0, "level": 1, "last_xp": 0}

async def add_user_xp(user_id: str, amount: int):
    async with aiosqlite.connect(DB_PATH) as db:
        user = await get_user_xp(user_id)
        new_xp = user["xp"] + amount
        new_level = user["level"]
        leveled_up = False

        needed = new_level * 100
        while new_xp >= needed:
            new_xp -= needed
            new_level += 1
            leveled_up = True
            needed = new_level * 100

        await db.execute("""
            INSERT INTO users_xp (user_id, xp, level, last_xp)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                xp = excluded.xp,
                level = excluded.level,
                last_xp = excluded.last_xp
        """, (user_id, new_xp, new_level, time.time()))
        await db.commit()

        return leveled_up, new_level, new_xp

async def get_leaderboard_data(limit: int = 10, offset: int = 0):
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("""
            SELECT user_id, xp, level FROM users_xp
            ORDER BY level DESC, xp DESC
            LIMIT ? OFFSET ?
        """, (limit, offset)) as cur:
            rows = await cur.fetchall()

        async with db.execute("SELECT COUNT(*) FROM users_xp") as cur:
            total_count = (await cur.fetchone())[0]

        return rows, total_count

async def get_user_rank_position(user_id: str) -> int:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("""
            SELECT COUNT(*) FROM users_xp
            WHERE (level > (SELECT level FROM users_xp WHERE user_id = ?))
               OR (level = (SELECT level FROM users_xp WHERE user_id = ?)
                   AND xp > (SELECT xp FROM users_xp WHERE user_id = ?))
        """, (user_id, user_id, user_id)) as cur:
            count = (await cur.fetchone())[0]
            return count + 1

# ─── AFK METOTLARI ─────────────────────────────
async def get_afk(user_id: str):
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT reason, timestamp FROM afk WHERE user_id = ?", (user_id,)) as cur:
            row = await cur.fetchone()
            if row:
                return {"reason": row[0], "time": row[1]}
            return None

async def set_afk(user_id: str, reason: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("""
            INSERT OR REPLACE INTO afk (user_id, reason, timestamp)
            VALUES (?, ?, ?)
        """, (user_id, reason, time.time()))
        await db.commit()

async def remove_afk(user_id: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("DELETE FROM afk WHERE user_id = ?", (user_id,))
        await db.commit()

# ─── WARN METOTLARI ────────────────────────────
async def add_warn(user_id: str, reason: str, moderator: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("""
            INSERT INTO warns (user_id, reason, moderator, timestamp)
            VALUES (?, ?, ?, ?)
        """, (user_id, reason, moderator, time.time()))
        await db.commit()
        async with db.execute("SELECT COUNT(*) FROM warns WHERE user_id = ?", (user_id,)) as cur:
            return (await cur.fetchone())[0]

async def get_warns(user_id: str):
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT id, reason, moderator, timestamp FROM warns WHERE user_id = ? ORDER BY id ASC", (user_id,)) as cur:
            return await cur.fetchall()

async def clear_warns(user_id: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("DELETE FROM warns WHERE user_id = ?", (user_id,))
        await db.commit()

# ─── TICKET METOTLARI ──────────────────────────
async def add_ticket(channel_id: str, user_id: str, category: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("""
            INSERT OR REPLACE INTO tickets (channel_id, user_id, category, opened_at)
            VALUES (?, ?, ?, ?)
        """, (channel_id, user_id, category, time.time()))
        await db.commit()

async def get_ticket(channel_id: str):
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT user_id, category, opened_at FROM tickets WHERE channel_id = ?", (channel_id,)) as cur:
            row = await cur.fetchone()
            if row:
                return {"user_id": row[0], "category": row[1], "opened_at": row[2]}
            return None

async def remove_ticket(channel_id: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("DELETE FROM tickets WHERE channel_id = ?", (channel_id,))
        await db.commit()

# ─── KOTA & VIP METOTLARI ──────────────────────
async def check_and_increment_quota(user_id: str, max_daily: int = 5, is_vip: bool = False) -> tuple[bool, int]:
    if is_vip:
        return True, 999
    date_str = time.strftime("%Y-%m-%d")
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT ops_count FROM user_quotas WHERE user_id = ? AND date_str = ?", (user_id, date_str)) as cur:
            row = await cur.fetchone()
            current_ops = row[0] if row else 0

        if current_ops >= max_daily:
            return False, current_ops

        new_ops = current_ops + 1
        await db.execute("""
            INSERT INTO user_quotas (user_id, date_str, ops_count)
            VALUES (?, ?, ?)
            ON CONFLICT(user_id, date_str) DO UPDATE SET ops_count = excluded.ops_count
        """, (user_id, date_str, new_ops))
        await db.commit()
        return True, new_ops

# ─── SCAN CACHE METOTLARI ──────────────────────
SCAN_CACHE_TTL = 86400  # 24 saat

async def get_cached_scan(sha256: str) -> dict | None:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute(
            "SELECT threat_score, verdict, classes, silentnet, payloads_json, loaders_json, injections_json, findings_json, raw_output, cached_at FROM scan_cache WHERE sha256 = ?",
            (sha256,)
        ) as cur:
            row = await cur.fetchone()
            if not row:
                return None
            cached_at = row[9]
            if time.time() - cached_at > SCAN_CACHE_TTL:
                await db.execute("DELETE FROM scan_cache WHERE sha256 = ?", (sha256,))
                await db.commit()
                return None
            return {
                "threat_score": row[0],
                "verdict": row[1],
                "classes": row[2],
                "silentnet": bool(row[3]),
                "payloads": json.loads(row[4]),
                "loaders": json.loads(row[5]),
                "injections": json.loads(row[6]),
                "findings": json.loads(row[7]),
                "raw_output": row[8],
                "cached_at": cached_at
            }

async def save_cached_scan(sha256: str, data: dict):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("""
            INSERT OR REPLACE INTO scan_cache 
            (sha256, threat_score, verdict, classes, silentnet, payloads_json, loaders_json, injections_json, findings_json, raw_output, cached_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            sha256,
            data.get("threat_score", 0),
            data.get("verdict", "Bilinmiyor"),
            data.get("classes", 0),
            1 if data.get("silentnet") else 0,
            json.dumps(data.get("payloads", [])),
            json.dumps(data.get("loaders", [])),
            json.dumps(data.get("injections", [])),
            json.dumps(data.get("findings", [])),
            data.get("raw_output", ""),
            time.time()
        ))
        await db.commit()

