"""Author the server's garden revision. Run with Python and NumPy.

garden_source.py preserves the original authored geometry. Only this revision
changes the composition; the archived modded world and original asset stay intact.
"""
from contextlib import contextmanager
import gzip
import hashlib
import json
from pathlib import Path
import struct
import numpy as np
import garden_source as g

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'src/main/resources/data/smash_vanilla/structures/mythical_garden_v2.bin.gz'


@contextmanager
def translated(z):
    # Every primitive and occupancy query addresses the same translated grid.
    lo, hi = g.LO.copy(), g.HI.copy()
    g.LO = lo - [0, 0, z]; g.HI = hi - [0, 0, z]
    try:
        yield
    finally:
        g.LO, g.HI = lo, hi


def interior():
    g.section('12 / Heartwood guildhall: library, stair, loft and lantern chandelier')
    def m(state, color='#a4774e'):
        return g.IDS.get(state) if state in g.IDS else g.material(state, color)
    stairs = m('spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
    chair = m('spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
    fence = m('spruce_fence')
    rail = m('spruce_fence[east=true,west=true,north=false,south=false,waterlogged=false]')
    rail_ns = m('spruce_fence[east=false,west=false,north=true,south=true,waterlogged=false]')
    trapdoor = m('spruce_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
    carpet = m('red_carpet', '#9e3e45')
    barrel = m('barrel[facing=up,open=false]')
    crafting = m('crafting_table')
    pot = m('potted_azalea_bush', '#689458')
    lectern = m('lectern[facing=north,has_book=false,powered=false]')
    # The original trunk twists: close its thin spots around the widened room.
    for y in range(1, 18):
        for x in range(-8, 9):
            for z in range(13, 30):
                if 36 < x*x + (z-21)**2 <= 64 and g.at(x, y, z) == g.AIR:
                    g.block(x, y, z, g.BARK)
    g.disk(0, 21, 7, 17, g.BARK)
    # More breathing room, with the trunk still enclosing the entire room.
    for y in range(1, 17):
        g.disk(0, 21, 6, y, g.AIR)
    g.disk(0, 21, 6, 0, g.SPRUCE)
    g.box(-2, 0, 10, 2, 0, 18, g.SPRUCE)
    g.box(-2, 1, 10, 2, 6, 17, g.AIR)
    for x in [-5, 5]:
        g.box(x, 1, 19, x, 4, 24, g.BOOKS)
        g.box(x, 5, 19, x, 5, 24, trapdoor)
    g.box(-3, 1, 26, 3, 4, 26, g.BOOKS)
    g.box(-2, 1, 19, 1, 1, 23, carpet)
    for z in [20, 23]:
        g.block(-3, 1, z, chair)
    g.block(-3, 1, 21, fence); g.block(-3, 2, 21, trapdoor)
    g.block(-3, 3, 21, pot)
    g.block(0, 1, 24, g.TABLE)
    g.block(-4, 1, 24, crafting); g.block(-4, 1, 25, barrel)
    # A complete one-block-wide staircase, with two blocks of headroom.
    g.box(2, 1, 16, 4, 12, 25, g.AIR)
    for step in range(1, 9):
        z = 16 + step
        g.box(4, 0, z, 4, step - 1, z, g.SPRUCE)
        g.block(3, step, z, stairs)
    # Storage beneath open treads, with the stringer against the outside wall.
    g.block(3, 1, 21, barrel); g.block(3, 1, 22, barrel); g.block(3, 2, 22, barrel)
    for x in range(-5, 6):
        for z in range(22, 27):
            if x*x + (z-21)**2 <= 36 and not (2 <= x <= 4 and z < 25):
                g.block(x, 8, z, g.SPRUCE)
    for x in range(-4, 2):
        g.block(x, 9, 22, rail)
    g.box(-4, 9, 24, -4, 11, 25, g.BOOKS)
    g.block(-2, 9, 25, lectern)
    g.block(0, 9, 25, chair); g.block(1, 9, 25, barrel)
    g.block(1, 10, 25, pot)
    # An open chandelier over the central aisle; nothing blocks the stairs.
    g.box(0, 12, 20, 0, 16, 20, g.CHAIN)
    g.box(-2, 12, 20, 2, 12, 20, rail)
    g.box(0, 12, 18, 0, 12, 22, rail_ns)
    for x, z in [(-2, 20), (2, 20), (0, 18), (0, 22)]:
        g.block(x, 11, z, g.LANTERN)
    # Warm entrance lanterns, framed by the existing root arch.
    for x in [-3, 3]:
        g.block(x, 5, 10, g.CHAIN); g.block(x, 4, 10, g.LANTERN)
    # Round bay window above the entry arch, visible across the open loft.
    window = m('yellow_stained_glass', '#e8cd81')
    for x in range(-3, 4):
        for dy in range(-3, 4):
            d = x*x + dy*dy
            if d <= 10:
                if d >= 6:
                    g.box(x, 13+dy, 12, x, 13+dy, 17, g.LOG)
                else:
                    g.box(x, 13+dy, 8, x, 13+dy, 17, g.AIR)
                    g.block(x, 13+dy, 12, window)


def build():
    g.terrain(); g.promenade(); g.formal_gardens(); g.sky_isles()
    g.water_temples(); g.threshold()
    with translated(69):
        g.lotus()
    with translated(-69):
        g.world_tree(); g.heartwood_library(); g.celestial_crown(); interior()
    g.final_details()
    # Restore the short, clear approach through the moved front roots.
    g.box(-2, 0, -82, 2, 0, -59, g.SPRUCE)
    g.box(-2, 1, -82, 2, 4, -59, g.AIR)
    validate()
    boxes = g.compress()
    palette = ['minecraft:' + state for state, _ in g.PALETTE]
    payload = bytearray(struct.pack('>IH', 0x47415231, len(palette)))
    for state in palette:
        encoded = state.encode('ascii')
        payload += struct.pack('>H', len(encoded)) + encoded
    payload += struct.pack('>I', len(boxes))
    replay = np.zeros_like(g.GRID)
    for a, b, material, _ in boxes:
        assert material != g.AIR
        payload += struct.pack('>6hH', a[0], a[1]+100, a[2], b[0], b[1]+100, b[2], material)
        sl = tuple(slice(a[i]-g.LO[i], b[i]-g.LO[i]+1) for i in range(3))
        assert not replay[sl].any(), 'Cuboids overlap'
        replay[sl] = material
    assert np.array_equal(replay, g.GRID), 'Binary replay differs from authored map'
    TARGET.write_bytes(gzip.compress(payload, mtime=0))
    info = dict(revision=2, blocks=int(np.count_nonzero(g.GRID)), cuboids=len(boxes),
                sha256=hashlib.sha256(TARGET.read_bytes()).hexdigest(),
                tree_center=[0, 100, -48], lotus_center=[0, 100, 21],
                spawn=[.549, 101, -105.631], library=[0, 101, -48], loft=[-1, 109, -45],
                bounds=[[-112, 58, -112], [112, 232, 104]])
    TARGET.with_suffix('').with_suffix('.json').write_text(json.dumps(info, indent=2)+'\n')
    print(json.dumps(info, indent=2))


def validate():
    assert g.at(0, 0, -106) == g.GOLD
    for z in range(-106, -50):
        assert g.at(0, 0, z) not in {g.AIR, g.WATER}, ('Approach floor', z)
        assert g.at(0, 1, z) == g.at(0, 2, z) == g.AIR, ('Approach headroom', z)
    for x in range(-1, 2):
        for z in range(-4, -1):
            assert g.at(x, 0, z) not in {g.AIR, g.WATER}
            assert g.at(x, 1, z) == g.at(x, 2, z) == g.AIR
    for step in range(1, 9):
        z = 16 + step - 69
        assert 'stairs' in g.PALETTE[g.at(3, step, z)][0]
        assert g.at(3, step+1, z) == g.at(3, step+2, z) == g.AIR, ('Stair headroom', step)
    assert g.at(0, 8, -44) == g.SPRUCE
    assert g.at(0, 3, 21) == g.GOLD
    assert g.at(0, 40, 21) == g.AIR, 'Old tree trunk remains'


if __name__ == '__main__':
    build()
