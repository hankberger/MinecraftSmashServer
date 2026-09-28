"""One-page picker artwork fitted to stock chest/inventory mouse regions."""
from PIL import Image, ImageDraw
import brand_ui_assets as brand


def add_picker(canvas, label, fighters):
    def frame(width, height, fill=brand.FOREST, border='#52665b'):
        im=Image.new('RGBA',(width,height))
        ImageDraw.Draw(im).rounded_rectangle((0,0,width-2,height-1),radius=2,fill=fill,outline=border)
        return im

    def button(name,width,y,selected=False,primary=False,disabled=False):
        fill='#263b30' if selected else brand.CORAL if primary and not disabled else '#21332a'
        border=brand.CREAM if selected else '#ff967e' if primary and not disabled else '#415a4b'
        im=frame(width,18,fill,border)
        if selected:
            ImageDraw.Draw(im).line(((width-8,3),(width-6,5),(width-3,1)),fill=brand.CREAM,width=1)
        canvas('picker_'+name,im,y)

    canvas('picker_party',frame(158,44),0)
    main=frame(158,150);label(main,'Choose fighter',7,1,color=brand.INK)
    canvas('picker_main',main,44)
    wallet=frame(158,23);wallet.alpha_composite(brand.art('credit',(10,10)),(6,6))
    canvas('picker_wallet',wallet,196)
    for i in range(8):
        for fighter in fighters:
            for selected in (False,True):
                card=brand.fighter_card(fighter,False,label,size=36,density=3)
                if selected:
                    d=ImageDraw.Draw(card);d.rectangle((0,0,104,107),outline=brand.CREAM,width=3)
                    d.rectangle((78,6,98,26),fill=brand.FOREST)
                    d.line(((81,16),(87,22),(96,10)),fill=brand.CREAM,width=3)
                canvas(f'picker_card_{fighter}{"_on" if selected else ""}_{i}',card,53+(i//4)*36,density=3)
    for fighter in fighters:
        canvas('picker_party_head_'+fighter,brand.portrait(fighter,(39,39)),13,density=3)
    for name in ('own','other'):
        canvas('picker_member_'+name,frame(36,32,'#1c2e25',brand.CREAM if name=='own' else '#354b3e'),12)
    for name in ('previous','next','skin','skin_disabled'):
        button(name,18 if name in ('previous','next') else 54,138,disabled=name.endswith('disabled'))
    for name,width in [('duel',54),('ffa',54),('practice',36)]:
        for suffix in ('','_on','_disabled'):
            button(name+suffix,width,156,selected=suffix=='_on',disabled=suffix=='_disabled')
    for name in ('primary','primary_disabled'):
        button(name,108,174,primary=True,disabled=name.endswith('disabled'))
    button('back',36,174)
    button('store',36,196)
    button('results',36,196)
    button('invite',36,17)
    canvas('picker_queue',frame(144,36,'#243b2e','#839785'),138)
    crown=Image.new('RGBA',(8,6));d=ImageDraw.Draw(crown)
    d.polygon([(0,0),(2,2),(3,0),(5,2),(6,0),(6,5),(0,5)],fill='#efc863')
    canvas('picker_crown',crown,14)
    ready=Image.new('RGBA',(8,7));ImageDraw.Draw(ready).line(((0,3),(2,5),(6,0)),fill='#b9e590',width=2)
    canvas('picker_ready',ready,19)
    empty=Image.new('RGBA',(13,13));label(empty,'?',6,2,color=brand.MUTED,center=True)
    canvas('picker_unknown',empty,13)
