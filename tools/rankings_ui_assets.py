"""Wide rankings panel, matched to the forest/coral menu system."""
from PIL import Image, ImageDraw
import brand_ui_assets as brand


def add_rankings_ui(png, providers, index, fighters):
    def glyph(name, im):
        im.putpixel((im.width-2, im.height-1), (255, 255, 255, 1))
        ImageDraw.Draw(im).line((im.width-1,0,im.width-1,im.height-1),fill=(0,0,0,0))
        key='dialog_rankings_'+name
        char=chr(0xe000+len(index)); path='ui/'+key+'.png'
        png('assets/smash/textures/'+path,im)
        providers.append({'type':'bitmap','file':'smash:'+path,'height':im.height,'ascent':8,'chars':[char]})
        index[key]={'char':char,'x':0,'width':im.width}

    # Split the backing into two glyphs to stay within native bitmap-font limits.
    panel=Image.new('RGBA',(384,252),'#172a22'); d=ImageDraw.Draw(panel)
    d.rectangle((0,0,382,251),outline='#78946a',width=2)
    d.line((8,4,374,4),fill='#efc863')
    for i in range(10):
        y=45+i*18
        d.rectangle((8,y,374,y+17),fill='#20372b' if i%2==0 else '#192e24')
        d.line((8,y+17,374,y+17),fill='#2c4435')
    d.rectangle((8,228,374,246),fill='#304e38',outline='#86ab79')
    for side in range(2):
        tile=Image.new('RGBA',(193 if side==0 else 192,252));tile.alpha_composite(panel.crop((side*192,0,(side+1)*192,252)))
        glyph('panel_'+str(side),tile)
    for active in (False,True):
        im=Image.new('RGBA',(128,18)); d=ImageDraw.Draw(im)
        d.line((8,16,118,16),fill='#a7ddbc' if active else '#415e4d',width=2 if active else 1)
        glyph('tab_'+('on' if active else 'off'),im)
    glyph('hit',Image.new('RGBA',(128,9)))
    for fighter in fighters:
        im=Image.new('RGBA',(18,18));im.alpha_composite(brand.portrait(fighter,(16,16)),(0,1));glyph('head_'+fighter,im)
