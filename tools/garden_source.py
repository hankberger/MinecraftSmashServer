"""Original Aetherbloom geometry, extracted from the authored generate_garden.py.
build_garden.py owns the server revision composition, validation and export.
The original map, command export and modded project are preserved separately.
"""
from __future__ import annotations

import json
import math
from pathlib import Path
import numpy as np


ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'mythical_garden'
CFG = json.loads((ROOT / 'garden_config.json').read_text())
ORIGIN = np.array(CFG['origin'], dtype=int)
LO = np.array([-112, -42, -112])
HI = np.array([112, 132, 104])
SHAPE = tuple(HI - LO + 1)
GRID = np.zeros(SHAPE, dtype=np.uint8)
PART = np.zeros(SHAPE, dtype=np.uint8)
SECTIONS = ['Empty space']
CURRENT = 0
RNG = np.random.default_rng(CFG['seed'])
PALETTE = []
IDS = {}
CHECKPOINTS = {}
WATER_CELLS = []


def material(name, color):
    i = len(PALETTE)
    PALETTE.append((name, color))
    IDS[name] = i
    return i


AIR = material('air', '#091724')
ROCK = material('stone', '#778888')
DEEP = material('deepslate', '#384a54')
TUFF = material('tuff', '#607971')
DIRT = material('dirt', '#735345')
GRASS = material('grass_block', '#659956')
MOSS = material('moss_block', '#6b9955')
WHITE = material('smooth_quartz', '#ede6d3')
PILLAR = material('quartz_pillar[axis=y]', '#e0ddcd')
GOLD = material('gold_block', '#e8bb58')
PRISM = material('dark_prismarine', '#315f63')
TEAL = material('waxed_oxidized_copper', '#51a493')
AMETHYST = material('amethyst_block', '#9870c8')
CALCITE = material('calcite', '#dce0d6')
PINK = material('pink_concrete', '#e49aba')
MAGENTA = material('magenta_concrete', '#bd579b')
PURPLE = material('purple_stained_glass', '#9c61d7')
CYAN = material('cyan_stained_glass', '#54cbd3')
GLASS = material('light_blue_stained_glass', '#8bdde4')
WATER = material('water[level=0]', '#377fbe')
SEA = material('sea_lantern', '#a7fff0')
PEARL = material('pearlescent_froglight', '#f5d2eb')
VERDANT = material('verdant_froglight', '#d4ef9d')
OCHRE = material('ochre_froglight', '#ffe5a1')
LOG = material('stripped_dark_oak_log[axis=y]', '#755141')
BARK = material('dark_oak_wood[axis=y]', '#493b39')
CHERRYLOG = material('cherry_wood[axis=y]', '#8c5d62')
CHERRY = material('cherry_leaves[persistent=true]', '#edacc7')
AZALEA = material('flowering_azalea_leaves[persistent=true]', '#789666')
OAK = material('oak_leaves[persistent=true]', '#709755')
YELLOW = material('yellow_terracotta', '#dca449')
HONEY = material('honeycomb_block', '#e8ac3c')
WARPED = material('warped_wart_block', '#368f8b')
STEM = material('stripped_warped_stem[axis=y]', '#4a8980')
SHROOM = material('shroomlight', '#ffbe86')
CHERRYWOOD = material('cherry_planks', '#dda2a0')
SPRUCE = material('spruce_planks', '#947255')
CHAIN = material('iron_chain[axis=y]', '#767980')
LANTERN = material('lantern[hanging=true]', '#ffdba0')
SOULLANTERN = material('soul_lantern[hanging=true]', '#79e8e2')
ROD = material('end_rod[facing=up]', '#ffffdc')
BOOKS = material('bookshelf', '#b29d6b')
TABLE = material('enchanting_table', '#54426b')
LADDER = material('ladder[facing=north]', '#ad8a5c')
ALLIUM = material('allium', '#b988d7')
PINKTULIP = material('pink_tulip', '#f0b7d4')
CORNFLOWER = material('cornflower', '#6e9ee4')
DAISY = material('oxeye_daisy', '#f4ebc2')
LILY = material('lily_of_the_valley', '#e8f2e4')
POPPY = material('poppy', '#f27885')
FERN = material('fern', '#568965')
PLANTS = {ALLIUM, PINKTULIP, CORNFLOWER, DAISY, LILY, POPPY, FERN}
LIGHTS = {SEA, PEARL, VERDANT, OCHRE, SHROOM, ROD, LANTERN, SOULLANTERN}


def section(name):
    global CURRENT
    SECTIONS.append(name)
    CURRENT = len(SECTIONS)-1
    print('Sculpting:', name, flush=True)


def box(x1, y1, z1, x2, y2, z2, m):
    a = np.minimum([x1, y1, z1], [x2, y2, z2]).astype(int)
    b = np.maximum([x1, y1, z1], [x2, y2, z2]).astype(int)
    assert np.all(a >= LO) and np.all(b <= HI), ('bounds', a, b)
    a -= LO
    b -= LO
    sl = tuple(slice(a[i], b[i]+1) for i in range(3))
    GRID[sl] = m
    PART[sl] = CURRENT if m else 0


def block(x, y, z, m):
    box(x, y, z, x, y, z, m)


def at(x, y, z):
    p = np.array([x, y, z], dtype=int)-LO
    if np.any(p < 0) or np.any(p >= SHAPE):
        return AIR
    return int(GRID[tuple(p)])


def ellipsoid(cx, cy, cz, rx, ry, rz, m, only_air=False):
    a = np.floor([cx-rx, cy-ry, cz-rz]).astype(int)
    b = np.ceil([cx+rx, cy+ry, cz+rz]).astype(int)
    assert np.all(a >= LO) and np.all(b <= HI), ('ellipsoid', a, b)
    x, y, z = np.ogrid[a[0]:b[0]+1, a[1]:b[1]+1, a[2]:b[2]+1]
    mask = ((x-cx)/max(rx, .5))**2 + ((y-cy)/max(ry, .5))**2 + ((z-cz)/max(rz, .5))**2 <= 1
    sl = tuple(slice(a[i]-LO[i], b[i]-LO[i]+1) for i in range(3))
    target, part = GRID[sl], PART[sl]
    if only_air:
        mask &= target == AIR
    target[mask] = m
    part[mask] = CURRENT if m else 0


def disk(cx, cz, r, y, m, inner=-1, height=1):
    for z in range(math.ceil(cz-r), math.floor(cz+r)+1):
        dx = math.floor(math.sqrt(max(0, r*r-(z-cz)**2)))
        if inner >= 0 and abs(z-cz) <= inner:
            cut = math.floor(math.sqrt(max(0, inner*inner-(z-cz)**2)))
            if dx > cut:
                box(cx-dx, y, z, cx-cut-1, y+height-1, z, m)
                box(cx+cut+1, y, z, cx+dx, y+height-1, z, m)
        else:
            box(cx-dx, y, z, cx+dx, y+height-1, z, m)


def line(a, b, m, radius=0):
    n = int(max(abs(np.array(b)-a))) * 2 + 1
    for p in np.linspace(a, b, max(1, n)).round().astype(int):
        if radius:
            ellipsoid(*p, radius, radius, radius, m)
        else:
            block(*p, m)


def curve(points, m, r0=1, r1=None):
    """Bezier branch/tendril, with a smoothly tapered circular section."""
    points = np.array(points)
    n = len(points)-1
    steps = int(np.sum(np.linalg.norm(np.diff(points, axis=0), axis=1))*2)+1
    for t in np.linspace(0, 1, steps):
        p = sum(math.comb(n, i)*(1-t)**(n-i)*t**i*points[i] for i in range(n+1))
        r = r0 if r1 is None else r0*(1-t)+r1*t
        ellipsoid(*p, max(r, .6), max(r, .6), max(r, .6), m)


def ring(center, u, v, radius, m, thickness=1, start=0, end=2*math.pi):
    for t in np.linspace(start, end, int(radius*abs(end-start)*3)+1):
        p = np.array(center)+radius*(np.array(u)*math.cos(t)+np.array(v)*math.sin(t))
        if thickness:
            ellipsoid(*p, thickness, thickness, thickness, m)
        else:
            block(*p.round().astype(int), m)


def checkpoint(name, x, y, z):
    CHECKPOINTS[name] = (x, y, z)


def island(cx, cz, radius, top, depth, satellite=False):
    for x in range(cx-radius-6, cx+radius+7):
        for z in range(cz-radius-6, cz+radius+7):
            angle = math.atan2(z-cz, x-cx)
            edge = radius + (1.4 if satellite else 3.2)*math.sin(5*angle) + 1.4*math.cos(9*angle)
            t = math.hypot(x-cx, z-cz)/edge
            if t > 1:
                continue
            bottom = top - max(3, round(depth*(1-t*t)**.8 + 2*math.sin(x*.22)*math.cos(z*.19)))
            box(x, bottom, z, x, top-4, z, DEEP if t < .65 else ROCK)
            if (x*13+z*7)%17 < 4:
                box(x, bottom, z, x, min(top-4, bottom+3), z, TUFF)
            box(x, top-3, z, x, top-1, z, DIRT)
            block(x, top, z, GRASS)


def crystal(x, y, z, height, radius=3, inverted=False, color=AMETHYST):
    sign = -1 if inverted else 1
    for dy in range(height+1):
        r = max(0, round(radius*(1-(dy/height)**2)))
        for dx in range(-r, r+1):
            for dz in range(-r, r+1):
                if abs(dx)+abs(dz) <= r+1:
                    block(x+dx, y+dy*sign, z+dz, SEA if dx == 0 and dz == -r else color)
    block(x, y+height*sign, z, PEARL if color == AMETHYST else SEA)


def terrain():
    section('01 / The floating continent and four sky islands')
    island(0, 0, 81, 0, 34)
    for x, z, top in [(-77,-62,12),(77,-62,12),(-77,59,18),(77,59,18)]:
        island(x, z, 19, top, 19, satellite=True)
    for x, z, d in [(-38,-26,11),(28,-37,13),(-30,40,11),(41,22,13),(0,0,8)]:
        # Start at the island underside; the root crystal hangs into the void.
        ys = [y for y in range(-40, 0) if at(x,y,z) != AIR]
        crystal(x, min(ys)+2, z, d, 4, inverted=True, color=CYAN)
    # Approach cantilever with a patterned marble carpet.
    box(-7,-3,-108,7,-1,-69,PRISM)
    box(-6,0,-108,6,0,-69,WHITE)
    box(-4,0,-108,4,0,-69,CHERRYWOOD)
    for z in range(-106,-70,6):
        box(-1,0,z,1,0,z+1,GOLD)
    for x in [-7,7]:
        box(x,1,-108,x,1,-82,WHITE)
        for z in range(-106,-82,6):
            block(x,2,z,OCHRE)
    checkpoint('arrival bridge',0,1,-103)


def lantern_post(x, y, z, cool=False):
    block(x,y,z,WHITE)
    box(x,y+1,z,x,y+3,z,TEAL)
    block(x,y+4,z,GOLD)
    for dx in [-1,1]:
        block(x+dx,y+4,z,TEAL)
        block(x+dx,y+3,z,SOULLANTERN if cool else LANTERN)


def pool(cx, cz, r, y=0):
    disk(cx,cz,r+1,y-2,PRISM,height=3)
    disk(cx,cz,r,y-1,SEA)
    disk(cx,cz,r,y,WATER)
    disk(cx,cz,r+1,y,WHITE,inner=r)


def promenade():
    section('02 / Eightfold promenade, pools, and illuminated waterways')
    disk(0,0,52,0,GOLD,inner=51)
    disk(0,0,51,0,WHITE,inner=44)
    disk(0,0,48,0,PRISM,inner=47)
    disk(0,0,44,0,GOLD,inner=43)
    for x in range(-79,80):
        for z in range(-79,80):
            r = math.hypot(x,z)
            if r > 79 or at(x,0,z) == AIR:
                continue
            # Four principal walks and diagonal routes across the parterres.
            d = min(abs(x),abs(z),abs(x-z)/math.sqrt(2),abs(x+z)/math.sqrt(2))
            if d <= 2 and r > 14:
                block(x,0,z,WHITE if d > 1 else CHERRYWOOD)
    # Two gentle crescent-shaped reflecting pools.
    for s in [-1,1]:
        pool(s*28,-23,10)
        for t in np.linspace(0,2*math.pi,10,endpoint=False):
            x,z=round(s*28+13*math.cos(t)),round(-23+13*math.sin(t))
            if abs(x) > 9:
                block(x,0,z,PEARL)
    for t in np.linspace(0,2*math.pi,20,endpoint=False):
        x,z = round(54*math.cos(t)),round(54*math.sin(t))
        if abs(x) < 7 or abs(z) < 7:
            continue
        lantern_post(x,0,z,cool=x>0)
    # The star mosaic is walkable, with its center at the tree entrance.
    disk(0,-3,11,0,PRISM)
    for t in np.linspace(0,2*math.pi,8,endpoint=False):
        line((0,0,-3),(round(10*math.cos(t)),0,round(-3+10*math.sin(t))),GOLD)
    disk(0,-3,3,0,PEARL)
    for name,x,z in [('west promenade',-48,0),('east promenade',48,0),('north promenade',0,48)]:
        checkpoint(name,x,1,z)


def lotus():
    section('03 / The First Star: a colossal lotus in its mirror lake')
    pool(0,-48,19)
    # Walk around the lotus on a white-and-gold circular boardwalk.
    disk(0,-48,23,0,WHITE,inner=20)
    disk(0,-48,23,0,GOLD,inner=22)
    for a in np.linspace(0,2*math.pi,12,endpoint=False):
        dx,dz=math.cos(a),math.sin(a)
        # Swept petal sections grow broad, then narrow at the upturned tip.
        for t in np.linspace(0,1,24):
            r=3+12*t
            x,z=r*dx,-48+r*dz
            y=2+8*t*t
            width=1.0+3.5*math.sin(math.pi*t)
            for w in np.arange(-width,width+.1,.65):
                xx,zz=round(x-w*dz),round(z+w*dx)
                yy=round(y+.13*w*w)
                block(xx,yy,zz,PINK if abs(w)>width-.9 else MAGENTA)
                if abs(w) > width-1.0:
                    block(xx,yy,zz,PINK)
    for a in np.linspace(0,2*math.pi,8,endpoint=False):
        dx,dz=math.cos(a+.2),math.sin(a+.2)
        curve([(dx*2,2,-48+dz*2),(dx*8,4,-48+dz*8),(dx*5,12,-48+dz*5)],PINK,1.8,.8)
    disk(0,-48,4,3,GOLD,height=2)
    ellipsoid(0,8,-48,3,3,3,OCHRE)
    for a in np.linspace(0,2*math.pi,6,endpoint=False):
        x,z=round(5*math.cos(a)),round(-48+5*math.sin(a))
        box(x,4,z,x,8,z,ROD)
    # The blossom is deliberately a sculpture above real contained water.
    checkpoint('lotus viewing walk',0,1,-70)


def world_tree():
    section('04 / The Elder Bloom: roots, spiral trunk, and flowering crown')
    # Nine wandering roots leave routes between them.
    for i,a in enumerate(np.linspace(0,2*math.pi,9,endpoint=False)):
        dx,dz=math.cos(a),math.sin(a)
        curve([(dx*5,3,21+dz*5),(dx*14,5,21+dz*14),(dx*25,0,21+dz*24),(dx*34,-1,21+dz*30)],BARK,3.8,.8)
    curve([(0,3,21),(-5,25,24),(7,50,20),(0,70,24)],BARK,9,3)
    for a in np.linspace(0,2*math.pi,5,endpoint=False):
        pts=[]
        for y in np.linspace(2,66,7):
            angle=a+y*.065
            r=7-y*.045
            pts.append((r*math.cos(angle),y,22+r*math.sin(angle)))
        for p,q in zip(pts,pts[1:]):
            line(p,q,LOG,1)
    crowns=[]
    for i,a in enumerate(np.linspace(0,2*math.pi,11,endpoint=False)):
        reach=27+(i%3)*4
        y=49+(i%4)*5
        x,z=reach*math.cos(a),22+reach*.81*math.sin(a)
        curve([(0,28,22),(x*.30,42,z*.35+14),(x*.76,y-3,z),(x,y,z)],BARK,3.5,1.1)
        crowns.append((x,y+5,z,11+(i%2)*2,7,10))
        # Outer foliage lobes, irregular enough to read as a living tree.
        crowns.append((x*1.15,y+2,22+(z-22)*1.2,8,5,8))
    crowns += [(-12,69,20,15,8,13),(10,72,28,16,9,14),(0,79,21,12,7,12)]
    for i,(x,y,z,rx,ry,rz) in enumerate(crowns):
        ellipsoid(x,y,z,rx,ry,rz,CHERRY,only_air=True)
        ellipsoid(x-3,y-4,z+1,rx*.55,ry*.55,rz*.55,AZALEA,only_air=True)
        if i%2 == 0:
            xx,zz=round(x),round(z)
            yy=round(y-ry)
            # Hanging starfruit is attached to the canopy above it.
            while at(xx,yy,zz)==AIR:
                yy+=1
            box(xx,yy-5,zz,xx,yy-1,zz,CHAIN)
            ellipsoid(xx,yy-7,zz,1.8,2.4,1.8,PEARL)
            block(xx,yy-10,zz,ROD)
    # Tiny luminous veins on the ancient trunk.
    for y in range(13,53,5):
        x=round(7*math.cos(y*.11))
        z=round(22+7*math.sin(y*.11))
        block(x,y,z,AMETHYST)


def heartwood_library():
    section('05 / Heartwood library, reading loft, and a secret stair')
    # This hollow is carved after the trunk, preserving an unbroken roof.
    for y in range(1,17):
        disk(0,21,5,y,AIR)
    disk(0,21,6,0,SPRUCE)
    disk(0,21,4,0,WHITE)
    box(-2,1,10,2,7,21,AIR)
    box(-2,0,8,2,0,21,WHITE)
    for x in [-3,3]:
        box(x,1,11,x,7,11,LOG)
    curve([(-3,7,11),(0,12,11),(3,7,11)],LOG,1,1)
    for x in [-4,4]:
        box(x,1,19,x,4,24,BOOKS)
    box(-3,1,25,3,4,25,BOOKS)
    block(0,1,22,TABLE)
    # Loft deck along the back of the hollow, with an attached ladder.
    box(-4,8,22,4,8,25,SPRUCE)
    box(3,1,25,3,10,25,LOG)
    box(3,1,24,3,9,24,LADDER)
    block(0,14,21,OCHRE)
    box(0,15,21,0,16,21,CHAIN)
    block(-3,9,23,OCHRE)
    block(-3,10,23,ROD)
    # Restore the walkway through the frontmost roots.
    box(-2,1,-6,2,3,10,AIR)
    box(-2,0,-6,2,0,10,WHITE)
    checkpoint('heartwood library',0,1,18)
    checkpoint('heartwood reading loft',2,9,23)


def celestial_crown():
    section('06 / The celestial crown, crescent moon, and orbiting stars')
    # A 45-block-high astrolabe suspended above the living canopy.
    ring((0,108,24),(1,0,0),(0,1,0),21,GOLD,.85)
    ring((0,108,24),(1,0,0),(0,1,0),18,WHITE,.6,start=.25,end=2*math.pi-.25)
    ring((0,108,24),(1,0,0),(0,.35,.94),25,TEAL,.7)
    # A filled crescent, cut analytically instead of erasing the surrounding ring.
    for x in range(-13,14):
        for y in range(95,122):
            if x*x+(y-108)**2 <= 12**2 and (x-6)**2+(y-111)**2 > 11**2:
                box(x,y,23,x,y,25,CALCITE)
    for a in np.linspace(0,2*math.pi,8,endpoint=False):
        x,y=round(21*math.cos(a)),round(108+21*math.sin(a))
        ellipsoid(x,y,24,1.7,1.7,1.7,OCHRE)
    ellipsoid(8,111,24,2,2,2,PEARL)
    for x,y,z in [(-34,90,16),(31,96,29),(22,89,6),(-17,98,8)]:
        line((x-2,y,z),(x+2,y,z),GOLD)
        line((x,y-3,z),(x,y+3,z),OCHRE)
    # Split sculptural comets echo the rings beside the tree, clear of walking areas.
    for s in [-1,1]:
        curve([(s*61,29,25),(s*73,63,34),(s*45,92,26),(s*28,91,21)],CYAN,1.2,.6)
        curve([(s*62,31,26),(s*68,60,37),(s*44,87,30)],GOLD,.7,.6)


def little_tree(x, y, z, kind='spring', scale=1):
    h=round(9*scale)
    trunk=CHERRYLOG if kind=='spring' else LOG
    foliage=CHERRY if kind=='spring' else OAK
    curve([(x,y+1,z),(x-1,y+h*.55,z),(x+1,y+h,z)],trunk,1.2*scale,.7)
    for i,a in enumerate([.25,2.35,4.45]):
        xx,zz=x+math.cos(a)*4*scale,z+math.sin(a)*4*scale
        curve([(x,y+h*.45,z),(xx,y+h-1,zz),(xx,y+h+2,zz)],trunk,.8,.65)
        if kind=='autumn':
            ellipsoid(xx,y+h+1,zz,4*scale,3*scale,4*scale,HONEY,only_air=True)
            ellipsoid(xx-1,y+h+3,zz,3*scale,2*scale,3*scale,YELLOW,only_air=True)
        else:
            ellipsoid(xx,y+h+1,zz,4.5*scale,3*scale,4.5*scale,foliage,only_air=True)
    ellipsoid(x,y+h+3,z,4*scale,3*scale,4*scale,foliage if kind=='spring' else HONEY,only_air=True)
    block(x,y+h+1,z-3,PEARL if kind=='spring' else OCHRE)


def mushroom(x, y, z, h, r, color=WARPED):
    curve([(x,y+1,z),(x+2,y+h*.5,z),(x,y+h,z)],STEM,1.2,.8)
    for dy in range(0,5):
        rad=max(1,round(r*math.sqrt(max(0,1-(dy/5)**2))))
        disk(x,z,rad,y+h+dy,color)
    disk(x,z,r-1,y+h,SHROOM,inner=max(1,r-3))
    for a in np.linspace(0,2*math.pi,6,endpoint=False):
        xx,zz=round(x+(r-2)*math.cos(a)),round(z+(r-2)*math.sin(a))
        block(xx,y+h+3,zz,PEARL if color==AMETHYST else SEA)


def formal_gardens():
    section('07 / Four seasons: rose orchard, glowshrooms, amber grove, crystal meadow')
    # Beds follow the radial walks, with low marble edging and luminous corners.
    beds=[(-28,-61,10,7),(-58,-28,8,10),(28,-61,10,7),(60,-27,8,10),
          (-28,60,10,8),(-60,26,8,10),(29,61,10,8),(61,25,8,10)]
    for cx,cz,rx,rz in beds:
        for x in range(cx-rx,cx+rx+1):
            for z in range(cz-rz,cz+rz+1):
                d=((x-cx)/rx)**2+((z-cz)/rz)**2
                if d>1 or at(x,0,z) in {WHITE,GOLD,CHERRYWOOD,PRISM}:
                    continue
                block(x,0,z,WHITE if d>.8 else MOSS)
                if d<.7 and RNG.random()<.4:
                    flower=RNG.choice([PINKTULIP,POPPY,ALLIUM] if cx<0 and cz<0 else
                                      [CORNFLOWER,LILY,FERN] if cx>0 and cz<0 else
                                      [DAISY,POPPY,FERN] if cx<0 else [ALLIUM,LILY,CORNFLOWER])
                    block(x,1,z,int(flower))
        for dx in [-rx,rx]:
            block(cx+dx,1,cz,PEARL)
    for x,z in [(-61,-44),(-44,-62),(-63,-13),(-25,-72)]:
        little_tree(x,0,z,'spring',1.05)
    for x,z,h,r in [(62,-43,12,8),(42,-65,8,6),(67,-11,10,7),(23,-70,6,5)]:
        mushroom(x,0,z,h,r)
    for x,z in [(-60,43),(-42,64),(-68,12),(-24,68)]:
        little_tree(x,0,z,'autumn',1.1)
    for x,z,h,r in [(62,40,16,4),(42,62,12,3),(70,9,14,4),(24,68,10,3)]:
        crystal(x,1,z,h,r)
        crystal(x-5,1,z-2,max(5,h-6),2,color=CYAN)
        crystal(x+3,1,z+5,6,2,color=PURPLE)
    # Low roaming flowers only use open, soil-supported positions away from walks.
    for _ in range(1350):
        x,z=map(int,RNG.integers([-77,-77],[78,78]))
        if at(x,0,z) in {GRASS,MOSS} and at(x,1,z)==AIR and 20 < math.hypot(x,z) < 78:
            block(x,1,z,int(RNG.choice([ALLIUM,PINKTULIP,DAISY,LILY,FERN])))


def sky_bridge(start, end, top):
    a,b=np.array(start,dtype=float),np.array(end,dtype=float)
    vec=b-a
    length=np.linalg.norm(vec)
    d=vec/length
    for x in range(math.floor(min(a[0],b[0])-5),math.ceil(max(a[0],b[0])+5)+1):
        for z in range(math.floor(min(a[1],b[1])-5),math.ceil(max(a[1],b[1])+5)+1):
            p=np.array([x,z])-a
            along=float(p@d)
            side=abs(p[0]*d[1]-p[1]*d[0])
            if not (-2<=along<=length+2 and side<=3.4):
                continue
            # Meet the front edge of the island, twenty blocks before its center.
            # A ramp that only reaches full height at the center hits a cliff.
            y=round(top*max(0,min(1,(along-3)/(length-23))))
            # Full-block stairs with at most a one-block rise per horizontal step.
            box(x,y-2,z,x,y-1,z,PRISM)
            block(x,y,z,GOLD if side>2.6 else WHITE)
            box(x,y+1,z,x,y+4,z,AIR)
            if side>2.6:
                block(x,y+1,z,TEAL)
    # Sculptural underside arches accent the bridge span.
    for sign in [-1,1]:
        normal=np.array([-d[1],d[0]])*sign*2
        pts=[]
        for t in [0,.33,.66,1]:
            p=a+(b-a)*t+normal
            y=top*t-3-7*math.sin(math.pi*t)
            pts.append((p[0],y,p[1]))
        curve(pts,WHITE,1,1)


def pavilion(cx,cz,top,theme):
    disk(cx,cz,10,top,WHITE)
    disk(cx,cz,9,top,GOLD,inner=8)
    disk(cx,cz,4,top,PRISM)
    col=TEAL if theme in {'summer','winter'} else CHERRYWOOD
    for a in np.linspace(0,2*math.pi,8,endpoint=False):
        x,z=round(cx+8*math.cos(a)),round(cz+8*math.sin(a))
        box(x-1,top+1,z-1,x+1,top+1,z+1,WHITE)
        box(x,top+2,z,x,top+9,z,PILLAR)
        block(x,top+10,z,GOLD)
        # Ribs define an airy roof; the center remains open to a hanging light.
        curve([(x,top+10,z),(x,top+14,z),(cx,top+17,cz)],col,1,.6)
    ring((cx,top+10,cz),(1,0,0),(0,0,1),9,WHITE,.8)
    ellipsoid(cx,top+15,cz,2,2,2,PEARL if theme in {'spring','winter'} else OCHRE)
    block(cx,top+18,cz,GOLD)
    block(cx,top+19,cz,ROD)
    if theme=='spring':
        for dx,dz in [(-12,5),(10,10),(-3,14)]:
            little_tree(cx+dx,top,cz+dz,'spring',.7)
    elif theme=='summer':
        for dx,dz,h,r in [(-11,6,6,5),(10,10,8,5),(0,14,5,4)]:
            mushroom(cx+dx,top,cz+dz,h,r)
    elif theme=='autumn':
        for dx,dz in [(-12,-6),(10,9),(0,13)]:
            little_tree(cx+dx,top,cz+dz,'autumn',.7)
    else:
        for dx,dz,h in [(-13,0,12),(12,5,15),(0,14,11)]:
            crystal(cx+dx,top+1,cz+dz,h,3,color=AMETHYST)
    # Seats face inward, with a broad open center for the destination checkpoint.
    box(cx-5,top+1,cz+3,cx-3,top+1,cz+3,CHERRYWOOD)
    box(cx+3,top+1,cz+3,cx+5,top+1,cz+3,CHERRYWOOD)
    checkpoint(theme+' sky sanctuary',cx,top+1,cz)


def sky_isles():
    section('08 / Four ascending sky bridges and open seasonal pavilions')
    for cx,cz,top,theme in [(-77,-62,12,'spring'),(77,-62,12,'summer'),
                           (-77,59,18,'autumn'),(77,59,18,'winter')]:
        sx,sz=1 if cx>0 else -1,1 if cz>0 else -1
        sky_bridge((sx*34,sz*34),(cx,cz),top)
        pavilion(cx,cz,top,theme)


def waterfall(cx,cz,top):
    # Two-block-wide glass aqueducts retain every source; no flow escapes the
    # authored volume. The side view shows the entire turquoise falling ribbon.
    bottom=-13
    box(cx-2,bottom-1,cz-2,cx+2,bottom,cz+2,PRISM)
    box(cx-2,bottom+1,cz-2,cx+2,top+1,cz+2,GLASS)
    box(cx-1,bottom+1,cz-1,cx+1,top,cz+1,WATER)
    box(cx-2,bottom,cz-2,cx+2,bottom,cz+2,SEA)
    # Closed quartz crown and cuffs carry hanging jewel tassels.
    for y in [bottom+1,top-1]:
        disk(cx,cz,4,y,WHITE,inner=2)
        for dx,dz in [(-4,0),(4,0),(0,-4),(0,4)]:
            block(cx+dx,y+1,cz+dz,SEA)
    block(cx,top+2,cz,OCHRE)
    crystal(cx,bottom-1,cz,6,2,inverted=True,color=CYAN)


def water_temples():
    section('09 / Encased skyfalls and the rear Sanctuary of Rain')
    # Four vertical water ribbons hang on the outer face of the sky islands.
    for cx,cz,top in [(-89,-57,13),(89,-57,13),(-89,54,19),(89,54,19)]:
        waterfall(cx,cz,top)
    pool(0,68,11)
    disk(0,68,15,0,WHITE,inner=12)
    for x in [-13,13]:
        box(x,1,64,x,17,64,PILLAR)
        box(x,1,72,x,21,72,PILLAR)
        block(x,18,64,OCHRE)
        block(x,22,72,PEARL)
    curve([(-13,17,64),(-8,30,68),(0,34,68),(13,21,72)],WHITE,1.2,1.2)
    curve([(-13,21,72),(-5,31,68),(8,30,68),(13,17,64)],GOLD,.7,.7)
    # Open roof with a luminous instrument of seven suspended notes.
    for x in range(-6,7,2):
        box(x,17+abs(x)//2,69,x,26,69,CHAIN)
        block(x,16+abs(x)//2,69,PEARL if x%4 else OCHRE)
    box(-2,0,48,2,0,81,WHITE)
    # Clear the actual crossing through the reflecting pool; its water cells
    # are replaced by a solid causeway, retaining sealed liquid on each side.
    box(-2,-1,57,2,0,79,PRISM)
    box(-2,0,54,2,0,81,WHITE)
    checkpoint('Sanctuary of Rain',0,1,78)


def stag(cx,cz,mirror):
    # Sculpted guardian: four separate legs, swept neck, muzzle and branching
    # gold antlers. Full blocks keep it stable without entities or physics tricks.
    ellipsoid(cx,8,cz,3,3,5,CALCITE)
    for dx in [-2,2]:
        for dz in [-3,3]:
            line((cx+dx,6,cz+dz),(cx+dx,1,cz+dz+1),CALCITE,1)
            block(cx+dx,1,cz+dz+1,GOLD)
    curve([(cx,8,cz-3),(cx,13,cz-4),(cx,16,cz-6)],CALCITE,1.8,1.3)
    ellipsoid(cx,16,cz-6,2,2,3,CALCITE)
    box(cx-1,15,cz-10,cx+1,16,cz-7,CALCITE)
    block(cx-2,17,cz-7,SEA)
    block(cx+2,17,cz-7,SEA)
    for side in [-1,1]:
        curve([(cx+side,18,cz-5),(cx+side*4,23,cz-3),(cx+side*6,28,cz-2)],GOLD,1,.6)
        for k in range(3):
            a=(cx+side*(2+k),20+k*2,cz-4+k)
            b=(cx+side*(5+k),23+k*2,cz-7+k)
            line(a,b,GOLD)
            block(*b,OCHRE)
    line((cx,10,cz+4),(cx+mirror*2,12,cz+7),CALCITE,1)


def threshold():
    section('10 / Antlered guardians and the Gate of First Light')
    # Platforms connect back to the main island at its southern edge.
    for s in [-1,1]:
        box(s*16-6,-2,-91,s*16+6,-1,-69,PRISM)
        box(s*16-6,0,-91,s*16+6,0,-69,WHITE)
        disk(s*16,-78,5,0,GOLD,inner=4)
        stag(s*16,-78,s)
    # Pointed 17-wide entry arch: its opening is tall enough for the approach.
    for s in [-1,1]:
        box(s*8,1,-90,s*8,15,-88,PILLAR)
        curve([(s*8,14,-89),(s*8,24,-89),(s*2,27,-89),(0,31,-89)],WHITE,1.2,.8)
        curve([(s*9,16,-89),(s*12,25,-89),(s*5,32,-89)],GOLD,.7,.7)
        block(s*8,10,-91,PEARL)
    ellipsoid(0,29,-89,2,2,1,OCHRE)
    for z in range(-104,-70,8):
        lantern_post(-6,0,z)
        lantern_post(6,0,z)
    box(-3,0,-83,3,0,-71,WHITE)
    box(-2,1,-108,2,4,-71,AIR)


def final_details():
    section('11 / Moon benches, meadow jewels, and luminous borders')
    for cx,cz in [(-42,9),(42,9),(-14,-17),(14,-17)]:
        for dx in [-2,-1,0,1,2]:
            block(cx+dx,1,cz,CHERRYWOOD)
            block(cx+dx,2,cz+1,CHERRYWOOD)
        block(cx-3,1,cz,WHITE)
        block(cx+3,1,cz,WHITE)
    for x,z in [(-33,38),(34,38),(-20,45),(21,45)]:
        if at(x,0,z) != AIR:
            crystal(x,1,z,5,2,color=CYAN)
    # Remove ornamental plants that a later structure would leave unsupported.
    # Keep support-sensitive blocks out of the solid construction pass.
    for p in np.argwhere(np.isin(GRID,list(PLANTS))):
        x,y,z=map(int,p+LO)
        if at(x,y-1,z) not in {GRASS,MOSS,DIRT}:
            block(x,y,z,AIR)


def construct():
    terrain()
    promenade()
    lotus()
    world_tree()
    heartwood_library()
    celestial_crown()
    formal_gardens()
    sky_isles()
    water_temples()
    threshold()
    final_details()


def compress():
    """Lossless material-and-section cuboids, constrained to vanilla fill size."""
    remaining = GRID.astype(np.uint16) + PART.astype(np.uint16)*256
    boxes=[]
    for y in range(SHAPE[1]):
        for z in range(SHAPE[2]):
            x=0
            while x<SHAPE[0]:
                key=int(remaining[x,y,z])
                if not key:
                    x+=1
                    continue
                x2=x+1
                while x2<SHAPE[0] and remaining[x2,y,z]==key:
                    x2+=1
                z2=z+1
                while z2<SHAPE[2] and (x2-x)*(z2+1-z)<=32768 and np.all(remaining[x:x2,y,z2]==key):
                    z2+=1
                y2=y+1
                while y2<SHAPE[1] and (x2-x)*(z2-z)*(y2+1-y)<=32768 and np.all(remaining[x:x2,y2,z:z2]==key):
                    y2+=1
                remaining[x:x2,y:y2,z:z2]=0
                a=tuple(map(int,np.array([x,y,z])+LO))
                b=tuple(map(int,np.array([x2-1,y2-1,z2-1])+LO))
                boxes.append((a,b,key%256,key//256))
                x=x2
    def priority(item):
        a,b,m,part=item
        phase=3 if m==WATER else 2 if m in PLANTS else 1 if m in {LADDER,LANTERN,SOULLANTERN,ROD} else 0
        return phase,part,a[1],a[2],a[0]
    return sorted(boxes,key=priority)
