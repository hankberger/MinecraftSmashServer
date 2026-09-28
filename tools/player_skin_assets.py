"""Pixel-aligned clothing on vanilla's 64x64 player UVs; no armor or model changes.

Kept alongside the native UI asset generator so rebuilding the server pack also
rebuilds both outfits. Faces, hair, bare hands and wide/slim arm geometry stay
vanilla. Only clothing and Alex's small flower hair clip change.
"""
import io
from PIL import Image, ImageDraw


def faces(x, y, width, height=12, depth=4):
    """The six UV faces of one vanilla skin cuboid."""
    return {
        'top': (x+depth, y, width, depth),
        'bottom': (x+depth+width, y, width, depth),
        'right': (x, y+depth, depth, height),
        'front': (x+depth, y+depth, width, height),
        'left': (x+depth+width, y+depth, depth, height),
        'back': (x+2*depth+width, y+depth, width, height),
    }


def paint(skin, uv, pixel, sleeve=None):
    for side, (x, y, width, height) in uv.items():
        for v in range(height):
            # Leave the original skin visible below the rolled-up sleeve.
            if sleeve is not None and (side == 'bottom' or side not in ('top', 'bottom') and v >= sleeve):
                continue
            for u in range(width):
                skin.putpixel((x+u, y+v), (*pixel(side, u, v), 255))


def outfit(original, alex):
    skin = original.copy().convert('RGBA')
    draw = ImageDraw.Draw(skin)
    # Flatten the clothing layers, retaining the original head overlay/hair.
    for box in ((16,32,40,48),(40,32,56,48),(48,48,64,64),(0,32,16,48),(0,48,16,64)):
        draw.rectangle((box[0],box[1],box[2]-1,box[3]-1), fill=(0,0,0,0))

    def flannel(side, u, v):
        colors = ((183,53,46),(142,39,37),(92,35,34),(218,86,66))
        if u % 4 == 0 and v % 4 == 0: return colors[2]
        if u % 4 == 0 or v % 4 == 0: return colors[1]
        return colors[3] if side == 'top' else colors[0]

    def cream(side, u, v):
        return (232,215,174) if side in ('front','top') else (209,189,146)

    torso = faces(16,16,8)
    paint(skin,torso,cream if alex else flannel)
    for x, y in ((40,16),(32,48)):
        paint(skin,faces(x,y,3 if alex else 4),cream if alex else flannel,sleeve=5 if alex else 8)
    draw = ImageDraw.Draw(skin)
    if alex:
        # Soft cloth overalls: bib pocket, stitched straps and cross-over back.
        for side in ('front','back'):
            x,y,_,_=torso[side]
            draw.rectangle((x,y+6,x+7,y+11),fill='#527660')
            draw.rectangle((x+2,y+3,x+5,y+8),fill='#63876a')
            for strap in (1,6): draw.line((x+strap,y,x+strap,y+6),fill='#527660')
        for side in ('left','right'):
            x,y,w,_=torso[side];draw.rectangle((x,y+7,x+w-1,y+11),fill='#486a55')
        draw.line((23,26,24,26),fill='#9bbb84')
        draw.point((21,23),fill='#ddbd66');draw.point((26,23),fill='#ddbd66')
        # A tiny violet flower sits in the hair overlay, never a helmet slot.
        for pos in ((45,8),(44,9),(46,9),(45,10)): draw.point(pos,fill='#b58be0')
        draw.point((45,9),fill='#f3d66d')
    else:
        # Shirt placket, small buttons and one stitched chest pocket.
        draw.line((24,22,24,31),fill='#8e2725')
        for y in (23,27,30): draw.point((24,y),fill='#e6c49d')
        draw.rectangle((26,23,27,25),fill='#c34639')
        draw.line((26,23,27,23),fill='#e16b52')

    # Keep the original small neck opening instead of painting over the skin.
    for x in (23,24):
        for y in (20,21): skin.putpixel((x,y),original.getpixel((x,y)))

    def trousers(side, u, v):
        if side == 'bottom' or side not in ('top','bottom') and v >= 10:
            return (222,217,193) if not alex and v == 11 else (64,59,49)
        if alex: return (85,117,88) if side in ('front','top') else (65,93,72)
        return (54,79,103) if side in ('front','top') else (43,62,85)
    for x,y in ((0,16),(16,48)): paint(skin,faces(x,y,4),trousers)
    return skin


def add_player_skins(client, png):
    for alex, model, name in ((False,'wide/steve','steve_lumberjack'),(True,'slim/alex','alex_gardener')):
        original=Image.open(io.BytesIO(client.read('assets/minecraft/textures/entity/player/'+model+'.png'))).convert('RGBA')
        png('assets/smash/textures/entity/player/'+name+'.png',outfit(original,alex))
