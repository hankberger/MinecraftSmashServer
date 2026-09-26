"""Store layout built from the existing UI palette, vanilla portraits and item icons."""
import io
from PIL import Image, ImageDraw


def add_store_ui(z, cards, label, png, write_json, strips, index, providers, ascii_provider):
    width, height = 324, 144
    for member in (False, True):
        panel = Image.new('RGBA', (width, height), '#102a2d')
        d = ImageDraw.Draw(panel)
        d.rounded_rectangle((0, 0, width-2, height-1), radius=5, fill='#102a2d', outline='#718f80')
        d.rounded_rectangle((8, 8, 117, 101), radius=3, fill='#1f4544', outline='#3c7063')
        d.rounded_rectangle((125, 8, 315, 101), radius=3, fill='#343a2d', outline='#b69959')
        label(panel, 'YOUR CREDITS', 18, 16, color=(182,211,197,255))
        coin = Image.open(io.BytesIO(z.read('assets/minecraft/textures/item/gold_ingot.png'))).convert('RGBA')
        panel.alpha_composite(coin.resize((38,38), Image.Resampling.NEAREST), (43,30))
        label(panel, 'RINGSHIFT PLUS', 137, 16, color=(255,221,144,255))
        label(panel, 'MEMBER' if member else '$7.99 / month', 137, 30,
              color=(169,230,176,255) if member else (222,215,187,255))
        for i, fighter in enumerate(('steve','alex','zombie','skeleton','villager')):
            x=137+i*33
            d.rectangle((x,43,x+26,69), fill='#182e2d', outline='#69755a')
            panel.alpha_composite(cards[fighter].crop((6,2,30,26)),(x+1,44))
        label(panel, '5 skins + lobby badge', 137, 76, color=(240,234,215,255))
        label(panel, '1,000 credits / month', 137, 88, color=(240,234,215,255))
        # One background glyph draws the full card; later lines are transparent,
        # so the larger dynamic wallet text can span two native text rows.
        # The vanilla font atlas limits each glyph to 256 pixels wide.
        for part,(start,end) in enumerate(((0,122),(121,width))):
            name='store_panel_'+('member' if member else 'guest')+'_'+str(part)
            tile=panel.crop((start,0,end,height))
            ImageDraw.Draw(tile).line((tile.width-1,0,tile.width-1,height-1),fill=(0,0,0,0))
            char=chr(0xe000+len(index));path='ui/dialog_'+name+'.png'
            png('assets/smash/textures/'+path,tile)
            providers.append({'type':'bitmap','file':'smash:'+path,'height':height,'ascent':8,'chars':[char]})
            index['dialog_'+name]={'char':char,'x':0,'width':tile.width}

    for name,w,text,fill,border,ink in [
        ('credits',110,'GET CREDITS','#2e6258','#8ec4a4',(242,246,223,255)),
        ('plus',190,'VIEW PLUS','#d9b86b','#ffe4a0',(37,43,32,255)),
        ('member',190,'MANAGE PLUS','#3d654c','#a6d298',(236,247,218,255)),
        ('disabled',110,'UNAVAILABLE','#263f3f','#4f6964',(156,174,160,255)),
        ('plus_disabled',190,'UNAVAILABLE','#3c4235','#626953',(156,174,160,255)),
    ]:
        button=Image.new('RGBA',(w,27),fill);d=ImageDraw.Draw(button)
        d.rectangle((0,0,w-2,26),outline=border)
        d.line((2,2,w-4,2),fill=border)
        label(button,text,w//2,10,color=ink,center=True)
        strips('store_'+name,button)
    provider=dict(ascii_provider);provider.update(height=16,ascent=8)
    write_json('assets/smash/font/store_balance.json',{'providers':[{'type':'space','advances':{' ':8}},provider]})
