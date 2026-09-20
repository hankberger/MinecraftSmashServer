"""Deterministic vanilla UI assets. Run with Pillow and a cached official 26.2 client.

Portraits use Minecraft's own textures. Nine-pixel bitmap strips align with
vanilla dialog text mouse regions. A narrowly sized GUI focus-border filter
removes the dialog body's click flash; clients still need no mod.
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
queue=Image.new('RGBA',(162,18),'#193638')
ImageDraw.Draw(queue).line((0,0,0,17),fill='#b9e590',width=2)
strips('queue',queue)
for name in ('party_top','party_row','party_bottom'):
    panel=Image.new('RGBA',(88,9),'#193638'); draw=ImageDraw.Draw(panel)
    draw.line((0,0,0,8),fill='#789288');draw.line((86,0,86,8),fill='#789288')
    if name=='party_top':draw.line((0,0,86,0),fill='#d0aa6c')
    if name=='party_bottom':draw.line((0,8,86,8),fill='#d0aa6c')
    strips(name,panel)
# Narrow, full-height vanilla letters let all 16 username characters fit the
# separate sidebar without covering the fighter at GUI scale 3.
small_providers=[]; small_widths={}
for char,tile in letters.items():
    if char==' ':small_widths[char]=3;continue
    width=min(4,tile.width);small=tile.resize((width,8),Image.Resampling.BOX)
    # Keep thin stems (notably T and Y) when compressing five source columns
    # into four. Nearest-neighbor can drop the middle column completely.
    small.putalpha(small.getchannel('A').point(lambda a:255 if a else 0))
    path=f'font/small_{ord(char):04x}.png';png('assets/smash/textures/'+path,small)
    small_providers.append({'type':'bitmap','file':'smash:'+path,'height':8,'ascent':7,'chars':[char]})
    small_widths[char]=width+1
write_json('assets/smash/font/sidebar.json',{'providers':[{'type':'space','advances':{' ':3}}]+small_providers})
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
# A native four-row container supplies centered, resize-safe mouse regions. Its
# inventory artwork is replaced by the title canvas; all actual slots are empty.
png('assets/minecraft/textures/gui/container/generic_54.png',Image.new('RGBA',(256,256)))
for name in ('slot_highlight_back','slot_highlight_front'):
    png('assets/minecraft/textures/gui/sprites/container/'+name+'.png',Image.new('RGBA',(24,24)))
for path in z.namelist():
    if path.startswith('assets/minecraft/lang/') and path.endswith('.json'):
        write_json(path,{'container.inventory':''})

def canvas(name, im, y):
    im=im.copy();ImageDraw.Draw(im).line((im.width-1,0,im.width-1,im.height-1),fill=(0,0,0,0))
    key='dialog_menu_'+name;char=chr(0xe000+len(index));path='ui/'+key+'.png'
    png('assets/smash/textures/'+path,im)
    providers.append({'type':'bitmap','file':'smash:'+path,'height':im.height,'ascent':13-y,'chars':[char]})
    index[key]={'char':char,'x':0,'width':im.width}

panel=Image.new('RGBA',(176,186),'#193638');draw=ImageDraw.Draw(panel)
draw.rectangle((0,0,174,185),outline='#789288');draw.line((0,0,174,0),fill='#d0aa6c')
label(panel,'FIGHTERS',8,6);canvas('panel',panel,0)
party=Image.new('RGBA',(64,186),'#193638');draw=ImageDraw.Draw(party)
draw.rectangle((0,0,62,185),outline='#789288');draw.line((0,0,62,0),fill='#d0aa6c')
label(party,'PARTY',8,6);canvas('party',party,0)
for i in range(8):
    for name,card in cards.items():canvas('card_'+name+'_'+str(i),card,17+(i//4)*36)
for name,text in {**buttons,'back':'BACK'}.items():
    for variant in ('','_on','_disabled'):
        width=18 if name in ('previous','next') else 54
        im=Image.new('RGBA',(width,18),'#7b5a2b' if variant=='_on' else '#1b2e2d' if variant=='_disabled' else '#284d48')
        ImageDraw.Draw(im).rectangle((0,0,width-1,17),outline='#f1d294' if variant=='_on' else '#789288')
        label(im,text,width//2,5,color=(112,128,138,255) if variant=='_disabled' else (239,235,223,255),center=True)
        y=102 if name in ('duel','ffa','practice') else 17 if name=='previous' else 35 if name=='next' else 160
        canvas('button_'+name+variant,im,y)
queue=Image.new('RGBA',(162,32),'#223f3e');ImageDraw.Draw(queue).line((0,0,0,31),fill='#b9e590',width=2)
canvas('queue',queue,122)
for y in (92,125,140):
    provider=dict(ascii_provider);provider.update(height=8,ascent=13-y)
    write_json(f'assets/smash/font/picker_text_{y}.json',{'providers':[{'type':'space','advances':{' ':4}},provider]})
name_widths=small_widths
for y in [25+34*i+j for i in range(4) for j in (0,9,20)]:
    write_json(f'assets/smash/font/picker_name_{y}.json',{'providers':[{'type':'space','advances':{' ':3}}]+[dict(p,ascent=13-y) for p in small_providers]})
# The stock FocusableTextWidget paints its border with solid-color quads,
# not a replaceable sprite. Identify just the 344x170 picker body's four
# white edges by their quad dimensions. Ordinary controls retain their focus
# outlines; text/world shaders are untouched. Derivatives use GUI coordinates,
# so this remains independent of window resolution and GUI scale.
vsh=z.read('assets/minecraft/shaders/core/gui.vsh').decode()
vsh=vsh.replace('out vec4 vertexColor;', 'out vec4 vertexColor;\nout vec2 smashQuad;\nout vec2 smashPosition;\nflat out vec2 smashScreen;')
vsh=vsh.replace('vertexColor = Color;', '''vertexColor = Color;
    int corner = gl_VertexID & 3;
    smashQuad = vec2(corner >= 2 ? 1.0 : 0.0, (corner == 1 || corner == 2) ? 1.0 : 0.0);
    smashPosition = Position.xy;
    smashScreen = 2.0 / abs(vec2(ProjMat[0][0], ProjMat[1][1]));''')
fsh=z.read('assets/minecraft/shaders/core/gui.fsh').decode()
fsh=fsh.replace('in vec4 vertexColor;', 'in vec4 vertexColor;\nin vec2 smashQuad;\nin vec2 smashPosition;\nflat in vec2 smashScreen;')
fsh=fsh.replace('vec4 color = vertexColor;', '''vec4 color = vertexColor;
    // Staged vertex buffers may start a draw at any corner index. Derivative
    // lengths also handle that UV rotation, rather than assuming corner zero.
    vec2 extent = vec2(length(dFdx(smashPosition)), length(dFdy(smashPosition)))
        / max(vec2(length(dFdx(smashQuad)), length(dFdy(smashQuad))), vec2(0.000001));
    bool horizontal = abs(extent.x - 344.0) < 0.1 && abs(extent.y - 1.0) < 0.1;
    bool vertical = abs(extent.x - 1.0) < 0.1 && abs(extent.y - 168.0) < 0.1;
    if (all(greaterThan(color, vec4(0.999))) && (horizontal || vertical)) discard;
    // Screen.extractTransparentBackground's C0101010 -> D0101010 gradient.
    // Only the full-screen native dimmer is removed, not dark controls or text.
    bool fullscreen = all(lessThan(abs(extent - smashScreen), vec2(1.1)));
    bool dimmer = all(lessThan(abs(color.rgb - vec3(16.0 / 255.0)), vec3(0.001)))
        && color.a >= 191.5 / 255.0 && color.a <= 208.5 / 255.0;
    if (fullscreen && dimmer) discard;''')
files['assets/minecraft/shaders/core/gui.vsh']=vsh.encode()
files['assets/minecraft/shaders/core/gui.fsh']=fsh.encode()
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
(resources/'index.json').write_text(json.dumps({'sha1':sha,'glyphs':index,'widths':{c:4 if c==' ' else t.width+1 for c,t in letters.items()},'sidebarWidths':small_widths,'pickerNameWidths':name_widths},ensure_ascii=False),encoding='utf-8')
print(f'{dest.name}: {len(data)} bytes, {len(index)} glyphs')
