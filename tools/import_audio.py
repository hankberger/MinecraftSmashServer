"""Import the supplied soundtrack once; pack builds use the checked-in Ogg files.

Usage: python tools/import_audio.py C:/Users/hanks/Downloads
Requires ffmpeg and ffprobe on PATH. Preserve the mix; no normalization or trim.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'queue_drum': 'QueueDrum', 'queue_choir': 'QueueChoir',
    'fight': 'FightReverb', 'game': 'GameReverb',
    **{f'fight_{i}': f'Fight{i}' for i in range(1, 6)},
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    dest = ROOT / 'tools/audio_assets'
    dest.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for name, source in SOURCES.items():
        original = args.source / f'{source}.mp3'
        output = dest / f'{name}.ogg'
        # Stereo prevents spatial attenuation as the side-view camera moves.
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(original),
                        '-map_metadata', '-1', '-vn', '-ac', '2', '-ar', '44100',
                        '-c:a', 'libvorbis', '-q:a', '4', str(output)], check=True)
        info = json.loads(subprocess.check_output(['ffprobe', '-v', 'error',
            '-show_entries', 'format=duration', '-of', 'json', str(output)]))
        manifest[name] = {
            'source': original.name,
            'source_sha256': hashlib.sha256(original.read_bytes()).hexdigest(),
            'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
            'duration_ms': round(float(info['format']['duration']) * 1000),
            'stream': name == 'queue_choir' or name.startswith('fight_'),
        }
        print(f'{output.name}: {manifest[name]["duration_ms"]} ms, {output.stat().st_size:,} bytes')
    (dest / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
