"""Build Android launcher assets from the full-bleed Bước Chân Việt art."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "preview" / "bcv-app-icon-launcher.png"
RES = ROOT / "app" / "src" / "main" / "res"


source = Image.open(SOURCE).convert("RGB")
foreground = source.resize((432, 432), Image.Resampling.LANCZOS)
foreground.save(RES / "drawable-nodpi" / "bcv_launcher_foreground.png", optimize=True)

for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96,
                      "xxhdpi": 144, "xxxhdpi": 192}.items():
    icon = source.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    icon.save(RES / f"mipmap-{density}" / "ic_launcher.webp", format="WEBP", lossless=True)

    mask = Image.new("L", (size, size))
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    icon.putalpha(mask)
    icon.save(RES / f"mipmap-{density}" / "ic_launcher_round.webp", format="WEBP", lossless=True)

print("Generated adaptive foreground and 10 density-specific launcher icons.")
