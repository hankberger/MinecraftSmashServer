"""Deterministic vanilla UI assets. Run with Pillow and a cached official 26.2 client.

Portraits use the checked-in BrawlParty artwork. Nine-pixel bitmap strips align with
vanilla dialog text mouse regions. A narrowly sized GUI focus-border filter
removes the dialog body's click flash; clients still need no mod.
"""
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw
import brand_ui_assets as brand

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

from player_skin_assets import add_player_skins
add_player_skins(z, png)

def label(im, text, x, y, color=(239,235,223,255), center=False):
    tiles = [letters.get(c, letters['?']) for c in text]
    if center: x -= sum(t.width+1 for t in tiles)//2
    for tile in tiles:
        ink = Image.new('RGBA',tile.size,color); ink.putalpha(tile.getchannel('A'))
        im.alpha_composite(ink,(x,y)); x += tile.width+1

skins = {'steve':'player/wide/steve','alex':'player/slim/alex','zombie':'zombie/zombie','skeleton':'skeleton/skeleton','villager':'villager/villager','enderman':'enderman/enderman','drowned':'zombie/drowned','iron_golem':'iron_golem/iron_golem'}
cards = {}
for fighter in skins:
    for selected in (False,True):
        cards[fighter+('_on' if selected else '')]=brand.fighter_card(fighter,selected,label,size=36,density=1)
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
from store_ui_assets import add_store_ui
add_store_ui(z, cards, label, png, write_json, strips, index, providers, ascii_provider)
write_json('assets/minecraft/post_effect/blur.json',{'targets':{},'passes':[]})
png('assets/minecraft/textures/gui/inworld_menu_background.png',Image.new('RGBA',(32,32)))
# A native six-row container supplies centered, resize-safe mouse regions. Its
# inventory artwork is replaced by the title canvas; all actual slots are empty.
png('assets/minecraft/textures/gui/container/generic_54.png',Image.new('RGBA',(256,256)))
for name in ('slot_highlight_back','slot_highlight_front'):
    png('assets/minecraft/textures/gui/sprites/container/'+name+'.png',Image.new('RGBA',(24,24)))
for path in z.namelist():
    if path.startswith('assets/minecraft/lang/') and path.endswith('.json'):
        write_json(path,{'container.inventory':''})

def canvas(name, im, y, density=1):
    padded=Image.new('RGBA',(((im.width+density-1)//density)*density,((im.height+density-1)//density)*density))
    padded.alpha_composite(im);im=padded
    ImageDraw.Draw(im).rectangle((im.width-density,0,im.width-1,im.height-1),fill=(0,0,0,0))
    # Transparent portraits need a stable advance too. The font scans alpha to
    # find glyph width; an invisible marker pins it without adding a visible box.
    if not im.getchannel('A').crop((im.width-density-1,0,im.width-density,im.height)).getbbox():
        im.putpixel((im.width-density-1,im.height-1),(255,255,255,1))
    width,height=im.width//density,im.height//density
    key='dialog_menu_'+name;char=chr(0xe000+len(index));path='ui/'+key+'.png'
    png('assets/smash/textures/'+path,im)
    providers.append({'type':'bitmap','file':'smash:'+path,'height':height,'ascent':13-y,'chars':[char]})
    index[key]={'char':char,'x':0,'width':width}

from picker_ui_assets import add_picker
add_picker(canvas, label, skins)
from wide_picker_assets import add_wide_picker
add_wide_picker(png, write_json, providers, index, label, skins, ascii_provider)
from party_ui_assets import add_party_ui
add_party_ui(png, providers, index, skins)
from rankings_ui_assets import add_rankings_ui
add_rankings_ui(png, providers, index, skins)
from level_ui_assets import add_level_ui
add_level_ui(png, providers, index)
# Results use the left five columns of the native canvas. The transparent right
# side keeps the winner visible even at large GUI scales.
result_panel=brand.panel('results');draw=ImageDraw.Draw(result_panel)
draw.line((8,42,94,42),fill='#38544d');draw.line((8,125,94,125),fill='#38544d')
canvas('results_panel',result_panel,0)
for row,y in [(0,138),(1,156),(2,174),('lobby',196)]:
    button=brand.button(90,selected=row==0)
    canvas('results_button_'+str(row),button,y)
for row in range(4):
    for fighter in skins:
        face=brand.portrait(fighter,(48,48))
        canvas(f'results_head_{fighter}_{row}',face,44+18*row,density=3)
canvas('results_victory',brand.art('victory',(252,51),trim=True),2,density=3)
for fighter in skins:
    canvas('hud_head_'+fighter,brand.portrait(fighter,(54,54)),5,density=3)
canvas('hud_ko',brand.art('ko',(96,96)),5,density=3)
for y in sorted(set((5,8,22,34,44,53,62,71,80,89,98,107,116,128,143,161,162,176,179,201))):
    provider=dict(ascii_provider);provider.update(height=8,ascent=13-y)
    write_json(f'assets/smash/font/picker_text_{y}.json',{'providers':[{'type':'space','advances':{' ':4}},provider]})
name_widths=small_widths
for y in sorted({25+34*i+j for i in range(4) for j in (0,9,20)} | {5,22,44,53,62,71,80,89,98,107,128,143,161,176,179,201}):
    write_json(f'assets/smash/font/picker_name_{y}.json',{'providers':[{'type':'space','advances':{' ':3}}]+[dict(p,ascent=13-y) for p in small_providers]})
# Compact party names reserve two lines, including sixteen wide characters.
party_widths={' ':3};party_providers=[]
for char,tile in letters.items():
    if char==' ':continue
    width=min(3,tile.width)
    im=tile.resize((tile.width*3,24),Image.Resampling.NEAREST).resize((width*3,24),Image.Resampling.LANCZOS)
    path=f'ui/party_letter_{ord(char):04x}.png';png('assets/smash/textures/'+path,im)
    party_widths[char]=width+1
    party_providers.append({'type':'bitmap','file':'smash:'+path,'height':8,'chars':[char]})
for y in (26,34):
    write_json(f'assets/smash/font/party_name_{y}.json',{'providers':[{'type':'space','advances':{' ':3}}]+[dict(p,ascent=13-y) for p in party_providers]})
# The stock FocusableTextWidget paints its border with solid-color quads,
# not a replaceable sprite. Identify the legacy and wide picker bodies'
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
    bool horizontal = (abs(extent.x - 344.0) < 0.1 || abs(extent.x - 324.0) < 0.1 || abs(extent.x - 400.0) < 0.1 || abs(extent.x - 392.0) < 0.1) && abs(extent.y - 1.0) < 0.1;
    bool vertical = abs(extent.x - 1.0) < 0.1 && (abs(extent.y - 168.0) < 0.1 || abs(extent.y - 177.0) < 0.1 || abs(extent.y - 240.0) < 0.1 || abs(extent.y - 258.0) < 0.1);
    if (all(greaterThan(color, vec4(0.999))) && (horizontal || vertical)) discard;
    // Screen.extractTransparentBackground's C0101010 -> D0101010 gradient.
    // Only the full-screen native dimmer is removed, not dark controls or text.
    bool fullscreen = all(lessThan(abs(extent - smashScreen), vec2(1.1)));
    bool dimmer = all(lessThan(abs(color.rgb - vec3(16.0 / 255.0)), vec3(0.001)))
        && color.a >= 191.5 / 255.0 && color.a <= 208.5 / 255.0;
    if (fullscreen && dimmer) discard;''')
files['assets/minecraft/shaders/core/gui.vsh']=vsh.encode()
files['assets/minecraft/shaders/core/gui.fsh']=fsh.encode()
# 26.3 (format 97.1) reordered DynamicTransforms and requires explicit shader
# interface locations. Keep the original shader for 26.2 via a pack overlay.
# Derived from Mojang's official 26.3 gui.vsh/gui.fsh; no world shader overrides.
for ext, source in [('vsh',vsh),('fsh',fsh)]:
    source=source.replace('#version 330', '#version 330\n#extension GL_ARB_separate_shader_objects : require')
    source=source.replace('    mat4 ModelViewMat;\n', '    mat4 ModelViewMat;\n    mat4 TextureMat;\n')
    source=source.replace('    vec3 ModelOffset;\n    mat4 TextureMat;', '    vec3 ModelOffset;')
    # 26.3 compiles GLSL to SPIR-V on OpenGL too, using Vulkan built-in names.
    source=source.replace('gl_VertexID', 'gl_VertexIndex')
    declarations = ([('in vec3 Position;',0),('in vec4 Color;',1),('out vec4 vertexColor;',0),
                     ('out vec2 smashQuad;',1),('out vec2 smashPosition;',2),('flat out vec2 smashScreen;',3)]
                    if ext=='vsh' else [('in vec4 vertexColor;',0),('in vec2 smashQuad;',1),
                     ('in vec2 smashPosition;',2),('flat in vec2 smashScreen;',3),('out vec4 fragColor;',0)])
    for declaration,location in declarations:
        source=source.replace(declaration, f'layout(location = {location}) '+declaration)
    files[f'v26_3/assets/minecraft/shaders/core/gui.{ext}']=source.encode()
write_json('assets/smash/font/ui.json',{'providers':[{'type':'space','advances':{chr(0xf000+n+256):n for n in range(-256,769)}}]+providers})
audio_root=ROOT/'tools/audio_assets'
audio=json.loads((audio_root/'manifest.json').read_text(encoding='utf-8'))
sounds={}
for name,track in audio.items():
    data=(audio_root/f'{name}.ogg').read_bytes()
    if hashlib.sha256(data).hexdigest()!=track['sha256']: raise ValueError(f'Audio asset changed: {name}; reimport it')
    files[f'assets/smash/sounds/{name}.ogg']=data
    sounds['audio.'+name]={'sounds':[{'name':'smash:'+name,'stream':track['stream']}]}
write_json('assets/smash/sounds.json',sounds)
# The required server pack owns background music. Silence autonomous vanilla
# music (not discs or effects), which could otherwise start over a long match.
write_json('assets/minecraft/sounds.json',{key:{'replace':True,'sounds':[]}
    for key in json.loads((audio_root/'vanilla_music.json').read_text(encoding='utf-8'))})
write_json('pack.mcmeta',{
    'pack':{'description':'BrawlParty · Fighters, menus and battle music','min_format':[88,0],'max_format':[97,1]},
    'overlays':{'entries':[{'directory':'v26_3','min_format':[97,1],'max_format':[97,1]}]}})
files['pack.png']=(ROOT/'server-icon.png').read_bytes()
buf = io.BytesIO()
with zipfile.ZipFile(buf,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as out:
    for name,data in sorted(files.items()):
        entry=zipfile.ZipInfo(name,(2026,1,1,0,0,0)); entry.compress_type=zipfile.ZIP_DEFLATED; out.writestr(entry,data)
data=buf.getvalue(); sha=hashlib.sha1(data).hexdigest()
dest=ROOT/'resourcepacks'/f'{sha}.zip'; dest.parent.mkdir(exist_ok=True); dest.write_bytes(data)
resources=ROOT/'src/main/resources/ui'; resources.mkdir(exist_ok=True)
(resources/'pack.zip').write_bytes(data)
(resources/'audio.json').write_text(json.dumps(audio,indent=2)+'\n',encoding='utf-8')
(resources/'index.json').write_text(json.dumps({'sha1':sha,'glyphs':index,'widths':{c:4 if c==' ' else t.width+1 for c,t in letters.items()},'sidebarWidths':small_widths,'pickerNameWidths':name_widths,'partyNameWidths':party_widths},ensure_ascii=False),encoding='utf-8')
print(f'{dest.name}: {len(data)} bytes, {len(index)} glyphs')
