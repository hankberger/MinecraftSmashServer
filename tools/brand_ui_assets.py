"""BrawlParty artwork adapted to native GUI dimensions; no runtime image downloads."""
from pathlib import Path
from PIL import Image, ImageDraw

ASSETS = Path(__file__).with_name('ui_assets')
CORAL, FOREST, CREAM, MINT = '#ff624b', '#18211e', '#fffefb', '#d9e7dc'
INK = (255, 254, 251, 255)
DARK_INK = (24, 33, 30, 255)
MUTED = (142, 164, 150, 255)


def asset(name):
    return Image.open(ASSETS / name).convert('RGBA')


def fit(im, size):
    result = im.copy()
    result.thumbnail(size, Image.Resampling.LANCZOS)
    return result


def art(name, size, trim=False):
    source = asset('art/' + name + '.png')
    if trim:
        source = source.crop(source.getbbox())
    return fit(source, size)


def portrait(fighter, size):
    return fit(asset('portraits/' + fighter + '.png'), size)


def panel(name):
    return asset('ui/' + name + '-panel.png')


def button(width, selected=False, disabled=False):
    style = 'primary' if selected else 'secondary'
    state = 'disabled' if disabled else 'rest'
    return asset(f'ui/{style}-{state}-{width}x18.png')


def fighter_card(fighter, selected, label, size=54, density=3):
    """Higher-density portraits retain detail at GUI Scale Auto; layout stays fixed."""
    n = size * density
    im = Image.new('RGBA', (n, n), FOREST)
    draw = ImageDraw.Draw(im)
    for y in range(n):
        t = y / max(1, n-1)
        draw.line((0, y, n-1, y), fill=(int(44-20*t), int(63-30*t), int(52-22*t), 255))
    footer = 12 if size == 54 else 10
    render = portrait(fighter, ((size-3)*density, (size-footer-1)*density))
    im.alpha_composite(render, ((n-render.width)//2, density))
    draw.rectangle((density, (size-footer)*density, n-density-1, n-density-1), fill=CORAL if selected else FOREST)
    draw.rectangle((0, 0, n-1, n-1), outline=CORAL if selected else '#52665b', width=(2 if selected else 1)*density)
    name = 'Iron Golem' if fighter == 'iron_golem' else fighter.capitalize()
    text = Image.new('RGBA', (80, 8))
    label(text, name, 0, 0, color=DARK_INK if selected else INK)
    text = text.crop((0, 0, text.getbbox()[2], 8))
    text = text.resize((text.width*density, 8*density), Image.Resampling.NEAREST)
    if text.width > (size-6)*density:
        # Compress at source density so long names retain every letter stem.
        text = text.resize(((size-6)*density, 8*density), Image.Resampling.LANCZOS)
    im.alpha_composite(text, ((n-text.width)//2, (size-footer+2)*density))
    if selected:
        # A check as well as a color change makes selected state unambiguous.
        x, y = n-11*density, 3*density
        draw.rectangle((x, y, x+7*density, y+7*density), fill=CORAL)
        draw.line([(x+density,y+3*density),(x+3*density,y+5*density),(x+6*density,y+2*density)], fill=FOREST, width=density)
    return im
