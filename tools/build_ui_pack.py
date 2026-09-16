"""Deterministic vanilla UI assets. Run with Pillow and a cached official 26.2 client.

Portraits use Minecraft's own textures. Nine-pixel bitmap strips align with
vanilla dialog text mouse regions. No core shaders or client mod are required.
"""
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT.parent / 'smash_arena/.gradle-user-home/caches/fabric-loom/26.2/minecraft-client.jar'
z = zipfile.ZipFile(CLIENT)
files = {}
providers = []
index = {}
ascii_provider = next(p for p in json.loads(z.read('assets/minecraft/font/include/default.json'))['providers'] if p.get('file') == 'minecraft:font/ascii.png')
ascii_sheet = Image.open(io.BytesIO(z.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
letters = {}
for y, row in enumerate(ascii_provider['chars']):
    for x, char in enumerate(row):
        tile = ascii_sheet.crop((x*8,y*8,x*8+8,y*8+8))
        box = tile.getbbox()
        letters[char] = tile.crop((0,0,box[2] if box else 3,8))

def write_json(path, data):
    files[path] = json.dumps(data, ensure_ascii=False, separators=(',', ':')).encode('utf-8')

def png(path, im):
    out = io.BytesIO(); im.save(out, format='PNG'); files[path] = out.getvalue()

def label(im, text, x, y, color=(239,235,223,255), center=False):
    tiles = [letters.get(c, letters['?']) for c in text]
    if center: x -= sum(t.width+1 for t in tiles)//2
    for tile in tiles:
        ink = Image.new('RGBA',tile.size,color); ink.putalpha(tile.getchannel('A'))
        im.alpha_composite(ink,(x,y)); x += tile.width+1

skins = {'steve':'player/wide/steve','alex':'player/slim/alex','zombie':'zombie/zombie','skeleton':'skeleton/skeleton','villager':'villager/villager'}
cards = {}
for fighter,path in skins.items():
    skin = Image.open(io.BytesIO(z.read('assets/minecraft/textures/entity/'+path+'.png'))).convert('RGBA')
    face = skin.crop((8,10,16,18) if fighter=='villager' else (8,8,16,16)).resize((24,24),Image.Resampling.NEAREST)
    if fighter=='villager':
        ImageDraw.Draw(face).rectangle((9,10,14,22),fill='#a17d66',outline='#795c48')
    if fighter in ('steve','alex'):
        hat = skin.crop((40,8,48,16)).resize((24,24),Image.Resampling.NEAREST); face.alpha_composite(hat)
    for selected in (False,True):
        card = Image.new('RGBA',(36,36),'#385845' if selected else '#293a46')
        ImageDraw.Draw(card).rectangle((0,0,35,35),outline='#b9e590' if selected else '#4a606e')
        card.alpha_composite(face,(6,2))
        # Keep short names inside their clickable portrait tiles.
        name = fighter.capitalize(); text = Image.new('RGBA',(60,8)); label(text,name,0,0)
        used = text.getbbox(); text=text.crop((0,0,used[2],8))
        if text.width>34: text=text.resize((34,7),Image.Resampling.NEAREST)
        card.alpha_composite(text,((36-text.width)//2,28))
        cards[fighter+('_on' if selected else '')]=card
buttons = {'duel':'1v1','ffa':'4 Player','practice':'Practice','play':'PLAY','ready':'READY','unready':'UNREADY','cancel':'CANCEL','previous':'<','next':'>','waiting':'WAITING','results':'RESULTS'}
def strips(name, im):
    for row in range(im.height//9):
        part=im.crop((0,row*9,im.width,row*9+9))
        # Vanilla bitmap fonts add a one-pixel advance. Reserve that pixel in
        # the artwork so drawn bounds and native mouse regions agree exactly.
        ImageDraw.Draw(part).line((part.width-1,0,part.width-1,8),fill=(0,0,0,0))
        char=chr(0xe000+len(index)); key=f'dialog_{name}_{row}'
        path=f'ui/{key}.png'; png('assets/smash/textures/'+path,part)
        providers.append({'type':'bitmap','file':'smash:'+path,'height':9,'ascent':8,'chars':[char]})
        index[key]={'char':char,'x':0,'width':im.width}

for name,card in cards.items(): strips('card_'+name,card)
for name,width,height in (('gap',162,9),('edge',9,9),('empty',36,36)):
    strips(name,Image.new('RGBA',(width,height),'#193638'))
heading=Image.new('RGBA',(162,18),'#193638')
ImageDraw.Draw(heading).line((0,0,161,0),fill='#d0aa6c')
label(heading,'FIGHTERS',9,5); strips('heading',heading)
strips('heading_paged',heading.crop((0,0,126,18)))
for name,text in buttons.items():
    for variant in ('','_on','_disabled'):
        width=18 if name in ('previous','next') else 54
        im=Image.new('RGBA',(width,18),'#7b5a2b' if variant=='_on' else '#1b2e2d' if variant=='_disabled' else '#284d48')
        ImageDraw.Draw(im).rectangle((0,0,width-1,17),outline='#f1d294' if variant=='_on' else '#789288')
        label(im,text,width//2,5,color=(112,128,138,255) if variant=='_disabled' else (239,235,223,255),center=True)
        strips('button_'+name+variant,im)
write_json('assets/minecraft/post_effect/blur.json',{'targets':{},'passes':[]})
png('assets/minecraft/textures/gui/inworld_menu_background.png',Image.new('RGBA',(32,32)))
write_json('assets/smash/font/ui.json',{'providers':[{'type':'space','advances':{chr(0xf000+n+256):n for n in range(-256,769)}}]+providers})
write_json('pack.mcmeta',{'pack':{'description':'Smash · Fighter Select','min_format':[88,0],'max_format':[88,0]}})
buf = io.BytesIO()
with zipfile.ZipFile(buf,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as out:
    for name,data in sorted(files.items()):
        entry=zipfile.ZipInfo(name,(2026,1,1,0,0,0)); entry.compress_type=zipfile.ZIP_DEFLATED; out.writestr(entry,data)
data=buf.getvalue(); sha=hashlib.sha1(data).hexdigest()
dest=ROOT/'resourcepacks'/f'{sha}.zip'; dest.parent.mkdir(exist_ok=True); dest.write_bytes(data)
resources=ROOT/'src/main/resources/ui'; resources.mkdir(exist_ok=True)
(resources/'pack.zip').write_bytes(data)
(resources/'index.json').write_text(json.dumps({'sha1':sha,'glyphs':index,'widths':{c:4 if c==' ' else t.width+1 for c,t in letters.items()}},ensure_ascii=False),encoding='utf-8')
print(f'{dest.name}: {len(data)} bytes, {len(index)} glyphs')
