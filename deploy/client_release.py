"""Verified official client assets for cross-version smoke tests (no client mods)."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import urllib.request

RELEASES = {
    '26.3': '96c00d95a31328714d3811cfade2804bb050e455',
}


def fetch(path, url, digest):
    if path.exists() and hashlib.sha1(path.read_bytes()).hexdigest() == digest:
        return path
    data = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'SmashCompatibilityTest/0.3'}), timeout=60).read()
    if hashlib.sha1(data).hexdigest() != digest:
        raise RuntimeError('Official client asset checksum mismatch: ' + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return path


def prepare(version, runtime, cache):
    digest = RELEASES[version]
    folder = runtime / 'official-versions' / version
    info = json.loads(fetch(folder / 'version.json',
        f'https://piston-meta.mojang.com/v1/packages/{digest}/{version}.json', digest).read_text())
    game = fetch(folder / 'client.jar', info['downloads']['client']['url'], info['downloads']['client']['sha1'])
    assets = cache / 'caches/fabric-loom/assets'
    index = info['assetIndex']
    index_path = fetch(assets / 'indexes' / (index['id'] + '.json'), index['url'], index['sha1'])
    hashes = {v['hash'] for v in json.loads(index_path.read_text())['objects'].values()}
    def asset(digest):
        fetch(assets / 'objects' / digest[:2] / digest,
              f'https://resources.download.minecraft.net/{digest[:2]}/{digest}', digest)
    with ThreadPoolExecutor(max_workers=12) as pool:
        list(pool.map(asset, hashes))
    return info, game, assets, index_path
