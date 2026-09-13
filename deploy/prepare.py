"""Download checksum-pinned server dependencies and create local Docker secrets."""
import argparse
import hashlib
import json
from pathlib import Path
import secrets
import urllib.request

ROOT = Path(__file__).resolve().parent.parent

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--secrets', action='store_true', help='Create missing local secrets; existing values are preserved')
    args = parser.parse_args()
    vendor = ROOT / 'deploy/vendor'
    vendor.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / 'deploy/runtime-lock.json').read_text())
    for entry in manifest.values():
        target = vendor / entry['file']
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == entry['sha256']:
            continue
        req = urllib.request.Request(entry['url'], headers={'User-Agent': 'SmashArenaBuild/0.3.0 (local development)'})
        data = urllib.request.urlopen(req, timeout=60).read()
        if hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise RuntimeError('Dependency checksum mismatch: ' + entry['file'])
        target.write_bytes(data)
        print('Verified ' + entry['file'])
    if args.secrets:
        directory = ROOT / 'deploy/secrets'
        directory.mkdir(parents=True, exist_ok=True)
        directory.chmod(0o700)
        for name in ['control', 'admin', 'forwarding']:
            path = directory / name
            if not path.exists():
                with path.open('x') as out:
                    out.write(secrets.token_hex(32))
            # The host directory is private. Docker bind-mounts individual files;
            # the backend's unprivileged UID must be able to read those mounts.
            path.chmod(0o644)
        print('Local secrets ready (values not printed).')

if __name__ == '__main__':
    main()
