"""Party cards use the same artwork and real nine-pixel mouse rows as the picker."""
from PIL import Image, ImageDraw
import brand_ui_assets as brand


def add_party_ui(png, providers, index, fighters):
    def glyph(name, im, density=1, offset=0):
        im = im.copy()
        d = ImageDraw.Draw(im)
        d.rectangle((im.width-density, 0, im.width-1, im.height-1), fill=(0, 0, 0, 0))
        im.putpixel((im.width-density-1, im.height-1), (255, 255, 255, 1))
        key = 'dialog_party_' + name
        char = chr(0xe000+len(index))
        path = 'ui/'+key+'.png'
        png('assets/smash/textures/'+path, im)
        providers.append({'type':'bitmap','file':'smash:'+path,'height':im.height//density,'ascent':8-offset,'chars':[char]})
        index[key] = {'char':char,'x':0,'width':im.width//density}

    def panel(w, h, kind='base'):
        im=Image.new('RGBA',(w*3,h*3));d=ImageDraw.Draw(im)
        if kind=='backdrop':
            d.rectangle((0,0,(w-2)*3,h*3-1),fill='#172a22',outline='#78946a',width=6)
            d.line((9,9,(w-5)*3,9),fill='#aec797',width=3)
            for x in (0,(w-6)*3):
                for y in (0,(h-6)*3):
                    d.rectangle((x,y,x+12,y+12),fill='#91ad78')
        if kind=='header':
            d.line((6*3,(h-2)*3,(w-8)*3,(h-2)*3),fill='#415e4d',width=2)
        if kind in ('tab','tab_on'):
            d.line((0,(h-1)*3,(w-2)*3,(h-1)*3),fill='#415e4d',width=2)
            if kind=='tab_on':
                d.line((4*3,(h-2)*3,(w-6)*3,(h-2)*3),fill='#a7ddbc',width=5)
        if kind=='empty':
            for x in range(6,w-9,6):
                for y in (3,h-4):d.line((x*3,y*3,(x+3)*3,y*3),fill='#486453',width=2)
            for y in range(3,h-6,6):
                for x in (6,w-9):d.line((x*3,y*3,x*3,(y+3)*3),fill='#486453',width=2)
        if kind in ('card','action','primary','selected','field','footer'):
            fill='#ff6552' if kind=='primary' else '#233c2e' if kind=='selected' else '#1c3027'
            border='#ff9b83' if kind=='primary' else '#91b79d' if kind in ('field','selected') else '#3b5948'
            d.rounded_rectangle((3*3,1*3,(w-5)*3,(h-2)*3),radius=4,fill=fill,outline=border,width=3)
            if kind=='selected':d.line((6*3,(h-3)*3,(w-8)*3,(h-3)*3),fill='#a7ddbc',width=4)
        if kind=='footer':d.line((0,(h-1)*3,(w-2)*3,(h-1)*3),fill='#a1b98a',width=4)
        return im.resize((w,h),Image.Resampling.LANCZOS)

    for width in (48,56,62,80,128,144,178,184,240):
        glyph('hit_'+str(width),Image.new('RGBA',(width,9)))
        for height in (9,18,27,36):
            for style in ('base','header','card','action','primary','selected','field','footer','tab','tab_on','empty'):
                glyph(f'{width}_{height}_{style}',panel(width,height,style))
    for width in (144,240):glyph('backdrop_'+str(width),panel(width,234,'backdrop'))
    crown=Image.new('RGBA',(8,26));ImageDraw.Draw(crown).polygon([(0,18),(2,20),(3,18),(5,20),(6,18),(6,23),(0,23)],fill='#efc863')
    glyph('crown',crown)
    for fighter in fighters:
        im=Image.new('RGBA',(30*3,36*3));im.alpha_composite(brand.portrait(fighter,(28*3,28*3)),(0,4*3))
        ImageDraw.Draw(im).rounded_rectangle((0,4*3,28*3-1,32*3-1),radius=4,outline='#719781',width=2)
        glyph('head_'+fighter,im,3)
    # Small, branded native controls for the focused text-entry dialog only.
    # The artwork completely covers its own 20px button; normal Minecraft buttons are unchanged.
    for style in ('primary','base'):
        im=panel(150,20,'primary' if style=='primary' else 'action')
        glyph('native_'+style,im,offset=-6)
