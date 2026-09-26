"""The deployed URL, server glyph index and bundled local pack must stay identical."""
import hashlib
import io
import json
from pathlib import Path
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class UiPackTest(unittest.TestCase):
    def test_store_cards_and_link_regions_fit_the_vanilla_font_atlas(self):
        import struct
        with zipfile.ZipFile(ROOT/'src/main/resources/ui/pack.zip') as pack:
            for state in ('guest','member'):
                widths=[]
                for part in range(2):
                    data=pack.read(f'assets/smash/textures/ui/dialog_store_panel_{state}_{part}.png')
                    width,height=struct.unpack('>II',data[16:24])
                    self.assertLessEqual(width,256)
                    self.assertEqual(height,144)
                    widths.append(width)
                self.assertEqual(sum(widths)-1,324)
            for name,width in [('credits',110),('plus',190),('member',190)]:
                for row in range(3):
                    data=pack.read(f'assets/smash/textures/ui/dialog_store_{name}_{row}.png')
                    self.assertEqual(struct.unpack('>II',data[16:24]),(width,9))

    def test_published_pack_matches_server_and_all_portraits_exist(self):
        index = json.loads((ROOT / 'src/main/resources/ui/index.json').read_text(encoding='utf-8'))
        data = (ROOT / 'src/main/resources/ui/pack.zip').read_bytes()
        self.assertEqual(index['sha1'], hashlib.sha1(data).hexdigest())
        self.assertEqual(data, (ROOT / 'resourcepacks' / (index['sha1'] + '.zip')).read_bytes())
        with zipfile.ZipFile(io.BytesIO(data)) as pack:
            self.assertIsNone(pack.testzip())
            providers = json.loads(pack.read('assets/smash/font/ui.json'))['providers']
            chars = {char for p in providers if p['type']=='bitmap' for row in p['chars'] for char in row}
            self.assertEqual(len(index['glyphs']),len(chars))
            for glyph in index['glyphs'].values(): self.assertIn(glyph['char'], chars)
            for fighter in ('steve','alex','zombie','skeleton','villager'):
                for row in range(4):
                    for suffix in ('','_on'): self.assertIn(f'dialog_card_{fighter}{suffix}_{row}', index['glyphs'])
            self.assertEqual({prefix+'assets/minecraft/shaders/core/gui.'+ext for prefix in ('','v26_3/') for ext in ('vsh','fsh')},
                             {name for name in pack.namelist() if '/shaders/' in name})
            for glyph in ('dialog_party_top_0','dialog_party_row_0','dialog_party_bottom_0','dialog_queue_0','dialog_queue_1'):
                self.assertIn(glyph,index['glyphs'])
            self.assertEqual([], json.loads(pack.read('assets/minecraft/post_effect/blur.json'))['passes'])
            self.assertFalse(any(name.endswith(('.class','.jar')) for name in pack.namelist()))
            meta=json.loads(pack.read('pack.mcmeta'))
            self.assertEqual([88,0],meta['pack']['min_format'])
            self.assertEqual([97,1],meta['pack']['max_format'])
            self.assertEqual([{'directory':'v26_3','min_format':[97,1],'max_format':[97,1]}],meta['overlays']['entries'])
            for ext in ('vsh','fsh'):
                old=pack.read(f'assets/minecraft/shaders/core/gui.{ext}').decode()
                new=pack.read(f'v26_3/assets/minecraft/shaders/core/gui.{ext}').decode()
                # Wrong uniform ordering renders the entire solid-color GUI incorrectly.
                self.assertGreater(old.index('mat4 TextureMat;'),old.index('vec3 ModelOffset;'))
                self.assertLess(new.index('mat4 TextureMat;'),new.index('vec4 ColorModulator;'))
                self.assertIn('layout(location = 3) flat ',new)
                if ext=='vsh':
                    self.assertIn('gl_VertexIndex',new)
                    self.assertNotIn('gl_VertexID',new)


if __name__ == '__main__': unittest.main()
