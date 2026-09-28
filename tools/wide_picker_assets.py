"""Roomier dialog controls. Artwork and transparent continuation rows share click widths."""
from PIL import Image, ImageDraw
import brand_ui_assets as brand


def add_wide_picker(png, write_json, providers, index, label, fighters, ascii_provider):
    def glyph(name, im, density=1, offset=0):
        im=im.copy()
        if im.height//density<8-offset:
            padded=Image.new('RGBA',(im.width,(8-offset)*density));padded.alpha_composite(im);im=padded
        ImageDraw.Draw(im).rectangle((im.width-density,0,im.width-1,im.height-1),fill=(0,0,0,0))
        im.putpixel((im.width-density-1,im.height-1),(255,255,255,1))
        key='dialog_wide_'+name;char=chr(0xe000+len(index));path='ui/'+key+'.png'
        png('assets/smash/textures/'+path,im)
        providers.append({'type':'bitmap','file':'smash:'+path,'height':im.height//density,'ascent':8-offset,'chars':[char]})
        index[key]={'char':char,'x':0,'width':im.width//density}

    def frame(w,h,fill=brand.FOREST,border='#52665b'):
        im=Image.new('RGBA',(w,h))
        ImageDraw.Draw(im).rounded_rectangle((0,0,w-2,h-1),radius=2,fill=fill,outline=border)
        return im

    for fighter in fighters:
        for selected in (False,True):
            card=brand.fighter_card(fighter,False,label,size=54,density=3)
            if selected:
                d=ImageDraw.Draw(card);d.rectangle((0,0,158,161),outline=brand.CREAM,width=3)
                d.rectangle((132,6,152,26),fill=brand.FOREST)
                d.line(((135,16),(141,22),(150,10)),fill=brand.CREAM,width=3)
            glyph('card_'+fighter+('_on' if selected else ''),card,3)
        face=Image.new('RGBA',(57,54));face.alpha_composite(brand.portrait(fighter,(54,54)))
        glyph('head_'+fighter,face,3,4)

    for width in (18,40,54,76,80,90,102,228):
        glyph('hit_'+str(width),Image.new('RGBA',(width,9)))
        for style in ('base','selected','primary','disabled'):
            fill=brand.CORAL if style=='primary' else '#263b30' if style=='selected' else '#21332a'
            border=brand.CREAM if style=='selected' else '#ff967e' if style=='primary' else '#415a4b'
            im=frame(width,27 if width==76 else 18,fill,border)
            if style=='selected':ImageDraw.Draw(im).line(((width-9,3),(width-7,5),(width-4,1)),fill=brand.CREAM,width=1)
            glyph('button_'+str(width)+'_'+style,im)
    glyph('header',frame(80,9))
    for name,border in [('own',brand.CREAM),('other','#415a4b')]:glyph('member_'+name,frame(80,27,'#1c2e25',border))
    glyph('empty',frame(80,27))
    wallet=frame(80,36);wallet.alpha_composite(brand.art('credit',(10,10)),(4,4));glyph('wallet',wallet)
    glyph('queue',frame(228,45,'#243b2e','#839785'))
    crown=Image.new('RGBA',(8,6));ImageDraw.Draw(crown).polygon([(0,0),(2,2),(3,0),(5,2),(6,0),(6,5),(0,5)],fill='#efc863');glyph('crown',crown,offset=1)
    ready=Image.new('RGBA',(8,7));ImageDraw.Draw(ready).line(((0,3),(2,5),(6,0)),fill='#b9e590',width=2);glyph('ready',ready,offset=19)
    for offset in (0,4,5,6,9,10,13,14,18,23):
        provider=dict(ascii_provider);provider.update(height=8,ascent=8-offset)
        write_json(f'assets/smash/font/dialog_text_{offset}.json',{'providers':[{'type':'space','advances':{' ':4}},provider]})
