from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent.parent
src = root / 'cryptoloot.png'
out_dir = root / 'app' / 'src' / 'main' / 'res'

if not src.exists():
    raise SystemExit('Source image not found')

img = Image.open(src).convert('RGBA')

# Make the source logo square and centered on a transparent canvas.
w, h = img.size
size = max(w, h)
canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
offset = ((size - w) // 2, (size - h) // 2)
canvas.paste(img, offset, img)
img = canvas

# Generate launcher PNGs for each density.
for density, scale in [('mdpi', 1.0), ('hdpi', 1.5), ('xhdpi', 2.0), ('xxhdpi', 3.0), ('xxxhdpi', 4.0)]:
    target_dir = out_dir / f'mipmap-{density}'
    target_dir.mkdir(parents=True, exist_ok=True)
    for legacy_name in ('ic_launcher.webp', 'ic_launcher_round.webp', 'ic_launcher.png', 'ic_launcher_round.png'):
        legacy_path = target_dir / legacy_name
        if legacy_path.exists():
            legacy_path.unlink()

    base = int(round(108 * scale))
    resized = img.resize((base, base), Image.LANCZOS)
    bg = Image.new('RGBA', (base, base), (0x06, 0x08, 0x14, 255))
    pad = int(round(base * 0.12))
    logo_w = max(1, base - 2 * pad)
    logo = resized.resize((logo_w, logo_w), Image.LANCZOS)
    bg.alpha_composite(logo, (pad, pad))
    bg.save(target_dir / 'ic_launcher.png')
    bg.save(target_dir / 'ic_launcher_round.png')

# Generate adaptive icon assets.
out_dir.joinpath('drawable').mkdir(parents=True, exist_ok=True)
foreground = Image.new('RGBA', (108, 108), (0x06, 0x08, 0x14, 255))
logo = img.resize((84, 84), Image.LANCZOS)
foreground.alpha_composite(logo, (12, 12))
foreground.save(out_dir / 'drawable' / 'ic_launcher_foreground_asset.png')
background = Image.new('RGBA', (108, 108), (0x06, 0x08, 0x14, 255))
background.save(out_dir / 'drawable' / 'ic_launcher_background_asset.png')

(out_dir / 'drawable' / 'ic_launcher_background.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#060814" />
</shape>
''', encoding='utf-8')

(out_dir / 'drawable' / 'ic_launcher_foreground.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ic_launcher_foreground_asset"
    android:gravity="center" />
''', encoding='utf-8')

(out_dir / 'mipmap-anydpi-v26' / 'ic_launcher.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''', encoding='utf-8')

(out_dir / 'mipmap-anydpi-v26' / 'ic_launcher_round.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''', encoding='utf-8')
