import io
import aiohttp
from PIL import Image, ImageDraw, ImageFont, ImageFilter

async def create_rank_card(username: str, avatar_url: str, current_xp: int, needed_xp: int, level: int, rank_pos: int) -> io.BytesIO:
    width, height = 900, 280
    image = Image.new("RGBA", (width, height), (15, 12, 32, 255))
    draw = ImageDraw.Draw(image)

    # 1. Gradient Background Overlay
    for y in range(height):
        alpha = int(25 + (y / height) * 45)
        draw.line([(0, y), (width, y)], fill=(155, 89, 182, alpha))

    # 2. Glowing Rounded Box
    box_rect = [15, 15, width - 15, height - 15]
    draw.rounded_rectangle(box_rect, radius=20, outline=(155, 89, 182, 120), width=2)

    # 3. Fetch and Process Avatar
    avatar_size = 180
    avatar_pos = (50, 50)
    avatar_img = None

    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(avatar_url, timeout=aiohttp.ClientTimeout(total=4)) as resp:
                if resp.status == 200:
                    av_bytes = await resp.read()
                    avatar_img = Image.open(io.BytesIO(av_bytes)).convert("RGBA")
    except Exception:
        avatar_img = None

    if not avatar_img:
        avatar_img = Image.new("RGBA", (avatar_size, avatar_size), (142, 68, 173, 255))

    avatar_img = avatar_img.resize((avatar_size, avatar_size), Image.Resampling.LANCZOS)

    # Circle mask for avatar
    mask = Image.new("L", (avatar_size, avatar_size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse((0, 0, avatar_size, avatar_size), fill=255)

    # Avatar Glow
    glow_box = [avatar_pos[0] - 6, avatar_pos[1] - 6, avatar_pos[0] + avatar_size + 6, avatar_pos[1] + avatar_size + 6]
    draw.ellipse(glow_box, outline=(236, 72, 153, 200), width=4)

    image.paste(avatar_img, avatar_pos, mask)

    # 4. Text & Badges
    try:
        font_large = ImageFont.truetype("arial.ttf", 36)
        font_mid   = ImageFont.truetype("arial.ttf", 26)
        font_small = ImageFont.truetype("arial.ttf", 20)
        font_bold  = ImageFont.truetype("arialbd.ttf", 40)
    except Exception:
        font_large = ImageFont.load_default()
        font_mid   = ImageFont.load_default()
        font_small = ImageFont.load_default()
        font_bold  = ImageFont.load_default()

    # Username
    display_user = username if len(username) <= 16 else username[:16] + "..."
    draw.text((270, 55), display_user, font=font_bold, fill=(255, 255, 255))

    # Rank & Level Badges
    rank_str = f"SIRA #{rank_pos}"
    level_str = f"SEVİYE {level}"
    draw.text((700, 55), rank_str, font=font_mid, fill=(241, 196, 15))
    draw.text((700, 95), level_str, font=font_large, fill=(155, 89, 182))

    # XP Text
    pct = min(1.0, max(0.0, current_xp / needed_xp)) if needed_xp > 0 else 1.0
    xp_str = f"{current_xp:,} / {needed_xp:,} XP  ({int(pct*100)}%)"
    draw.text((270, 150), xp_str, font=font_small, fill=(200, 200, 220))

    # 5. Progress Bar
    bar_x = 270
    bar_y = 185
    bar_w = 580
    bar_h = 32

    # Background bar
    draw.rounded_rectangle([bar_x, bar_y, bar_x + bar_w, bar_y + bar_h], radius=16, fill=(35, 25, 55, 255))

    # Fill bar
    fill_w = max(bar_h, int(bar_w * pct))
    if pct > 0:
        # Neon gradient fill
        fill_img = Image.new("RGBA", (fill_w, bar_h), (0, 0, 0, 0))
        fill_draw = ImageDraw.Draw(fill_img)
        fill_draw.rounded_rectangle([0, 0, fill_w, bar_h], radius=16, fill=(155, 89, 182, 255))

        # Overlay on image
        image.paste(fill_img, (bar_x, bar_y), fill_img)

    # Output buffer
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    buffer.seek(0)
    return buffer
