"""The deployed URL, server glyph index and bundled local pack must stay identical."""
import hashlib
import io
import json
from pathlib import Path
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class UiPackTest(unittest.TestCase):
    def test_audio_is_complete_stereo_streamed_and_timed_from_actual_files(self):
        import struct
        manifest=json.loads((ROOT/'tools/audio_assets/manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(manifest,json.loads((ROOT/'src/main/resources/ui/audio.json').read_text(encoding='utf-8')))
        self.assertEqual(set(manifest),{'queue_drum','queue_choir','fight','game',*[f'fight_{i}' for i in range(1,6)]})
        with zipfile.ZipFile(ROOT/'src/main/resources/ui/pack.zip') as pack:
            events=json.loads(pack.read('assets/smash/sounds.json'))
            for name,track in manifest.items():
                data=pack.read(f'assets/smash/sounds/{name}.ogg')
                self.assertEqual(track['sha256'],hashlib.sha256(data).hexdigest())
                self.assertEqual((ROOT/f'tools/audio_assets/{name}.ogg').read_bytes(),data)
                self.assertTrue(data.startswith(b'OggS'))
                header=data.index(b'\x01vorbis')
                self.assertEqual(data[header+11],2,'Stereo audio must not attenuate at the side camera')
                rate=struct.unpack_from('<I',data,header+12)[0]
                end=data.rfind(b'OggS');samples=struct.unpack_from('<Q',data,end+6)[0]
                self.assertAlmostEqual(track['duration_ms'],1000*samples/rate,delta=1)
                self.assertEqual(events['audio.'+name]['sounds'],[{'name':'smash:'+name,'stream':track['stream']}])
                self.assertEqual(track['stream'],name=='queue_choir' or name.startswith('fight_'))
            quiet=json.loads(pack.read('assets/minecraft/sounds.json'))
            self.assertIn('music.game',quiet);self.assertIn('music.overworld.jungle',quiet)
            self.assertTrue(all(key.startswith('music.') and value=={'replace':True,'sounds':[]} for key,value in quiet.items()))

    def test_all_bitmap_fonts_have_valid_baselines(self):
        with zipfile.ZipFile(ROOT/'src/main/resources/ui/pack.zip') as pack:
            for path in pack.namelist():
                if path.startswith('assets/smash/font/') and path.endswith('.json'):
                    for provider in json.loads(pack.read(path))['providers']:
                        if provider['type']=='bitmap':
                            self.assertLessEqual(provider['ascent'],provider.get('height',8),path)

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
            for name,size in [('credit',42),('plus',34)]:
                data=pack.read(f'assets/smash/textures/ui/dialog_store_art_{name}.png')
                self.assertEqual(struct.unpack('>II',data[16:24]),((size+1)*3,size*3))
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
            for fighter in ('steve','alex','zombie','skeleton','villager','enderman','drowned','iron_golem'):
                for row in range(4):
                    for suffix in ('','_on'): self.assertIn(f'dialog_card_{fighter}{suffix}_{row}', index['glyphs'])
                # All pages, selection states, results and HUD must ship together.
                for slot in range(8):
                    for suffix in ('','_on'):
                        key=f'dialog_menu_picker_card_{fighter}{suffix}_{slot}'
                        self.assertEqual(36,index['glyphs'][key]['width'])
                        provider=next(p for p in providers if p.get('chars')==[index['glyphs'][key]['char']])
                        self.assertEqual(36,provider['height'])
                        import struct
                        image=pack.read('assets/smash/textures/'+provider['file'].split(':',1)[1])
                        self.assertEqual((108,108),struct.unpack('>II',image[16:24]))
                for row in range(4): self.assertIn(f'dialog_menu_results_head_{fighter}_{row}',index['glyphs'])
                self.assertEqual(18,index['glyphs'][f'dialog_menu_hud_head_{fighter}']['width'])
            self.assertIn('dialog_menu_results_victory',index['glyphs'])
            self.assertIn('dialog_menu_hud_ko',index['glyphs'])
            self.assertLess(len(data),10*1024*1024,'Keep portraits and the six streaming tracks under 10 MiB')
            self.assertEqual((ROOT/'server-icon.png').read_bytes(),pack.read('pack.png'))
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
