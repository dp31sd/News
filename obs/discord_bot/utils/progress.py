import asyncio
import discord

class LiveProgressTracker:
    def __init__(self, interaction: discord.Interaction, title: str = "⚡ İşlem Sürüyor"):
        self.interaction = interaction
        self.title = title
        self.is_done = False
        self.task = None

    def _render_bar(self, pct: int, step_desc: str) -> discord.Embed:
        filled = int((pct / 100) * 15)
        bar = "▓" * filled + "░" * (15 - filled)
        e = discord.Embed(
            title=f"⚡ {self.title}",
            description=f"`[{bar}]` **{pct}%**\n\n🔹 *{step_desc}*",
            color=0x9B59B6,
            timestamp=discord.utils.utcnow()
        )
        e.set_footer(text="SuS Bytecode Engine v4.5 | İşlem yürütülüyor...")
        return e

    async def start(self):
        if self.task and not self.task.done():
            return
        self.is_done = False
        self.task = asyncio.create_task(self._animate())

    async def _animate(self):
        steps = [
            (15, "JAR arşivi analiz ediliyor ve sınıflar çözülüyor..."),
            (35, "Bytecode talimatları işleniyor ve şifreleme katmanı uygulanıyor..."),
            (60, "Sabitler katlanıyor ve kontrol akış grafikleri oluşturuluyor..."),
            (80, "Decompiler motoru çalıştırılıyor / Sınıflar yeniden yapılandırılıyor..."),
            (95, "Çıktı arşivi paketleniyor ve rapor hazırlanıyor...")
        ]
        try:
            for pct, desc in steps:
                if self.is_done:
                    break
                try:
                    await self.interaction.edit_original_response(embed=self._render_bar(pct, desc))
                except Exception:
                    pass
                await asyncio.sleep(2.5)
        except asyncio.CancelledError:
            pass

    async def stop(self):
        self.is_done = True
        if self.task and not self.task.done():
            self.task.cancel()
            try:
                await self.task
            except asyncio.CancelledError:
                pass
            except Exception:
                pass
