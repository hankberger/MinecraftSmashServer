"""Deterministic vanilla UI assets. Run with Pillow and a cached official 26.2 client.

Portraits use Minecraft's own textures. All controls keep vanilla hit locations;
this pack contains no shaders, executable client code, or gameplay changes.
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

def glyph(name, im, x, y):
    char = chr(0xe000+len(index))
    path = 'ui/'+name+'.png'
    png('assets/smash/textures/'+path, im)
    providers.append({'type':'bitmap','file':'smash:'+path,'height':im.height,'ascent':13-y,'chars':[char]})
    index[name] = {'char':char,'x':x,'width':im.width}

bg = Image.new('RGBA',(176,222))
d = ImageDraw.Draw(bg)
d.rectangle((0,0,86,130),fill='#17252e',outline='#69747b')
d.rectangle((0,134,175,221),fill='#17252e',outline='#69747b')
label(bg,'FIGHTERS',8,6)
glyph('background',bg,0,0)
for members in range(1,5):
    panel=bg.copy();ImageDraw.Draw(panel).rectangle((90,0,175,17+members*11),fill='#17252e',outline='#69747b')
    label(panel,'PARTY' if members>1 else 'SOLO',96,6)
    glyph(f'background_{members}',panel,0,0)
png('assets/minecraft/textures/gui/container/generic_54.png',Image.new('RGBA',(256,256)))
for part in ('back','front'):
    png(f'assets/minecraft/textures/gui/sprites/container/slot_highlight_{part}.png',Image.new('RGBA',(24,24)))
# The vanilla container label is not server-configurable. Remove it in all
# Minecraft languages while this server's pack is applied.
write_json('assets/minecraft/lang/en_us.json',{'container.inventory':''})
info=json.loads((CLIENT.parent/'mojang_minecraft_info.json').read_text(encoding='utf-8'))
asset_indices = [p for p in (ROOT.parent/'smash_arena/.gradle-user-home/caches/fabric-loom/assets/indexes').glob('*.json') if hashlib.sha1(p.read_bytes()).hexdigest()==info['assetIndex']['sha1']]
if not asset_indices: raise RuntimeError('Download the pinned Minecraft assets before building the pack')
for asset_index in asset_indices:
    for name in json.loads(asset_index.read_text(encoding='utf-8')).get('objects',{}):
        if name.startswith('minecraft/lang/') and name.endswith('.json'):
            write_json('assets/'+name,{'container.inventory':''})

skins = {'steve':'player/wide/steve','alex':'player/slim/alex','zombie':'zombie/zombie','skeleton':'skeleton/skeleton','villager':'villager/villager'}
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
        for slot in range(6):
            glyph(f'card_{slot}_{fighter}'+('_on' if selected else ''),card,8+(slot%2)*36,18+(slot//2)*36)

buttons = {'duel':'1v1','ffa':'FFA','practice':'Practice','play':'PLAY','ready':'READY','unready':'UNREADY','cancel':'CANCEL','back':'BACK','party':'PARTY','invite':'INVITE','previous':'<','next':'>','waiting':'WAITING','results':'RESULTS'}
for name,text in buttons.items():
    width = 54 if name in ('duel','ffa','practice','play','ready','unready','cancel','back','waiting','results') else 36
    for selected in (False,True):
        im = Image.new('RGBA',(width,18),'#4d6a3a' if selected else '#2e404b')
        ImageDraw.Draw(im).rectangle((0,0,width-1,17),outline='#b9e590' if selected else '#637681')
        label(im,text,width//2,5,center=True)
        # Position is chosen on the server; provider ascent identifies the row.
        for y in (140,158,194): glyph(f'{name}_{y}'+('_on' if selected else ''),im,0,y)
    if name in ('duel','ffa','practice'):
        im = Image.new('RGBA',(width,18),'#1e2d36')
        ImageDraw.Draw(im).rectangle((0,0,width-1,17),outline='#394953')
        label(im,text,width//2,5,color=(112,128,138,255),center=True)
        glyph(f'{name}_140_disabled',im,0,140)
write_json('assets/smash/font/ui.json',{'providers':[{'type':'space','advances':{chr(0xf000+n+256):n for n in range(-256,257)}}]+providers})
text_sheet=Image.new('RGBA',(ascii_sheet.width,len(ascii_provider['chars'])*48))
for row in range(len(ascii_provider['chars'])): text_sheet.alpha_composite(ascii_sheet.crop((0,row*8,ascii_sheet.width,row*8+8)),(0,row*48))
png('assets/smash/textures/ui/text.png',text_sheet)
for y in (17,28,39,50,128,178,214):
    p = dict(ascii_provider); p.update(file='smash:ui/text.png',height=48,ascent=13-y)
    write_json(f'assets/smash/font/text_{y}.json',{'providers':[{'type':'space','advances':{' ':4}},p]})
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
