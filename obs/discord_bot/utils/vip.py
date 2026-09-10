import os
import discord
from data.database import check_and_increment_quota

def is_user_vip(member: discord.Member) -> bool:
    if member.guild_permissions.administrator or member.guild_permissions.manage_guild:
        return True
    if member.premium_since is not None:
        return True
    vip_role_id = os.getenv("VIP_ROLE_ID", "0").split("#")[0].strip()
    try:
        vid = int(vip_role_id)
        if vid and any(r.id == vid for r in member.roles):
            return True
    except Exception:
        pass
    return False

async def verify_user_quota(interaction: discord.Interaction, file: discord.Attachment) -> tuple[bool, str]:
    member = interaction.user
    vip = is_user_vip(member) if isinstance(member, discord.Member) else False

    # Max File Size Check
    max_mb = 50 if vip else 20
    if file.size > max_mb * 1024 * 1024:
        return False, f"❌ Dosya boyutu sınırı aşıldı! {'VIP' if vip else 'Standart'} limit: **{max_mb} MB**."

    # Daily Operations Quota Check
    ok, count = await check_and_increment_quota(str(member.id), max_daily=5, is_vip=vip)
    if not ok:
        return False, f"❌ Günlük işlem limitine ulaştın (5/5)! Gece 00:00'da sıfırlanır veya VIP/Booster olarak sınırsız kullanabilirsin."

    return True, ""
