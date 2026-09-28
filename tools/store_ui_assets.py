"""Compact branded wallet and membership cards on vanilla dialog click regions."""
from PIL import Image, ImageDraw
import brand_ui_assets as brand


def add_store_ui(z, cards, label, png, write_json, strips, index, providers, ascii_provider):
    width, height = 324, 144
    for member in (False, True):
        panel = brand.panel('store')
        d = ImageDraw.Draw(panel)
        d.rounded_rectangle((8, 8, 117, 101), radius=3, fill='#294338', outline='#52665b')
        d.rounded_rectangle((125, 8, 315, 101), radius=3, fill='#24382f', outline=brand.CORAL)
        label(panel, 'YOUR CREDITS', 18, 16, color=brand.INK)
        label(panel, 'BRAWLPARTY PLUS', 177, 18, color=brand.INK)
        label(panel, 'MEMBER' if member else '$7.99 / month', 177, 34,
              color=(185,229,144,255) if member else (217,231,220,255))
        label(panel, '5 skins + lobby badge', 137, 60, color=brand.INK)
        label(panel, '1,000 credits / month', 137, 78, color=(217,231,220,255))
        # Preserve the existing wallet baseline, card bounds and link targets.
        # Font atlases are limited to 256 px per glyph, so split the background.
        for part,(start,end) in enumerate(((0,122),(121,width))):
            name='store_panel_'+('member' if member else 'guest')+'_'+str(part)
            tile=panel.crop((start,0,end,height))
            ImageDraw.Draw(tile).line((tile.width-1,0,tile.width-1,height-1),fill=(0,0,0,0))
            char=chr(0xe000+len(index));path='ui/dialog_'+name+'.png'
            png('assets/smash/textures/'+path,tile)
            providers.append({'type':'bitmap','file':'smash:'+path,'height':height,'ascent':8,'chars':[char]})
            index['dialog_'+name]={'char':char,'x':0,'width':tile.width}

    # Separate 3x-density art from the pixel-lettered panel so the token and
    # badge stay sharp at Auto GUI scale without exceeding the 256px atlas.
    for name,size,y in [('credit',42,28),('plus',34,17)]:
        tile=Image.new('RGBA',((size+1)*3,size*3))
        tile.alpha_composite(brand.art(name,(size*3,size*3)))
        tile.putpixel((size*3-1,tile.height-1),(255,255,255,1))
        char=chr(0xe000+len(index));path=f'ui/dialog_store_art_{name}.png'
        png('assets/smash/textures/'+path,tile)
        providers.append({'type':'bitmap','file':'smash:'+path,'height':size,'ascent':8-y,'chars':[char]})
        index[f'dialog_store_art_{name}']={'char':char,'x':0,'width':size+1}

    for name,w,text,primary,disabled in [
        ('credits',110,'GET CREDITS',True,False),
        ('plus',190,'VIEW PLUS',False,False),
        ('member',190,'MANAGE PLUS',False,False),
        ('disabled',110,'UNAVAILABLE',False,True),
        ('plus_disabled',190,'UNAVAILABLE',False,True),
    ]:
        fill='#3e4842' if disabled else brand.CORAL if primary else '#294338'
        border='#56645a' if disabled else '#ff967e' if primary else '#668373'
        button=Image.new('RGBA',(w,27));d=ImageDraw.Draw(button)
        d.rounded_rectangle((0,0,w-2,26),radius=3,fill=fill,outline=border)
        label(button,text,w//2,10,color=brand.MUTED if disabled else brand.DARK_INK if primary else brand.INK,center=True)
        strips('store_'+name,button)
    provider=dict(ascii_provider);provider.update(height=16,ascent=8)
    write_json('assets/smash/font/store_balance.json',{'providers':[{'type':'space','advances':{' ':8}},provider]})
