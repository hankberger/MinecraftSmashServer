"""The deployed URL, server glyph index and bundled local pack must stay identical."""
import hashlib
import io
import json
from pathlib import Path
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class UiPackTest(unittest.TestCase):
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
            self.assertEqual({'assets/minecraft/shaders/core/gui.vsh','assets/minecraft/shaders/core/gui.fsh'},
                             {name for name in pack.namelist() if '/shaders/' in name})
            for glyph in ('dialog_party_top_0','dialog_party_row_0','dialog_party_bottom_0','dialog_queue_0','dialog_queue_1'):
                self.assertIn(glyph,index['glyphs'])
            self.assertEqual([], json.loads(pack.read('assets/minecraft/post_effect/blur.json'))['passes'])
            self.assertFalse(any(name.endswith(('.class','.jar')) for name in pack.namelist()))
            self.assertEqual([88,0],json.loads(pack.read('pack.mcmeta'))['pack']['max_format'])


if __name__ == '__main__': unittest.main()
