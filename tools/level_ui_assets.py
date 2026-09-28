"""Progression cards in the existing forest/coral brand, using native bitmap fonts."""
from PIL import Image, ImageDraw
import brand_ui_assets as brand


def add_level_ui(png, providers, index):
    def glyph(name, im):
        im.putpixel((im.width-2, im.height-1), (255,255,255,1))
        ImageDraw.Draw(im).line((im.width-1,0,im.width-1,im.height-1), fill=(0,0,0,0))
        key='dialog_levels_'+name; char=chr(0xe000+len(index)); path='ui/'+key+'.png'
        png('assets/smash/textures/'+path,im)
        providers.append({'type':'bitmap','file':'smash:'+path,'height':im.height,'ascent':8,'chars':[char]})
        index[key]={'char':char,'x':0,'width':im.width}

    def panel(w,h):
        im=Image.new('RGBA',(w,h)); d=ImageDraw.Draw(im)
        d.rounded_rectangle((0,0,w-2,h-1),radius=4,fill='#182b23',outline='#597363',width=1)
        d.line((8,2,w-10,2),fill=brand.CORAL,width=2)
        d.line((8,h-3,w-10,h-3),fill='#2f483a')
        return im

    reward=panel(205,98)
    glyph('reward',reward)
    profile=panel(288,180); d=ImageDraw.Draw(profile)
    d.rounded_rectangle((16,58,270,79),radius=3,fill='#101d17',outline='#3b5847')
    d.line((18,111,268,111),fill='#3b5847')
    for side in range(2):
        tile=Image.new('RGBA',(145 if side==0 else 144,180))
        tile.alpha_composite(profile.crop((side*144,0,(side+1)*144,180)))
        glyph('profile_'+str(side),tile)
    for filled in range(25):
        im=Image.new('RGBA',(242,9)); d=ImageDraw.Draw(im)
        for i in range(24):
            d.rectangle((i*10,1,i*10+7,7),fill='#98dfb9' if i<filled else '#304e3c')
        glyph('bar_'+str(filled),im)
