"""Launch this server and a checksum-verified Mojang client, with zero client mods.

Only uses this project's disposable runtime and sibling build caches. The local
offline test identity never accesses launcher accounts or authentication tokens.
"""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import threading
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
CACHE = ROOT.parent / 'smash_arena/.gradle-user-home'
RUNTIME = ROOT / 'runtime'
JAVA_HOME = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Java\jdk-25'))
PORT = 25576


@contextmanager
def launcher_lock(runtime):
    """OS-owned lock: a crashed launcher never leaves a stale PID lock behind."""
    runtime.mkdir(parents=True, exist_ok=True)
    with (runtime / '.launcher.lock').open('a+b') as lock:
        lock.seek(0, os.SEEK_END)
        if lock.tell() == 0:
            lock.write(b'0'); lock.flush()
        lock.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise RuntimeError('Smash is already starting or running. Close its Minecraft window before opening PLAY.cmd again.') from error
        try:
            yield
        finally:
            lock.seek(0)
            if os.name == 'nt':
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def require_free_port(port):
    with socket.socket() as check:
        check.settimeout(1)
        if check.connect_ex(('127.0.0.1', port)) == 0:
            raise RuntimeError(f'A local server is already running on port {port}. Close the earlier Minecraft window before opening PLAY.cmd again.')


def sha1(path):
    return hashlib.sha1(path.read_bytes()).hexdigest()


def official_client(name='VanillaProbe', version='26.2'):
    info = json.loads((CACHE / 'caches/fabric-loom/26.2/mojang_minecraft_info.json').read_text(encoding='utf-8'))
    assets = CACHE / 'caches/fabric-loom/assets'
    # Loom prefixes its index with the Minecraft version; Mojang's launcher uses
    # the bare index number. Use the actual verified filename in this cache.
    asset_index = next((p for p in (assets / 'indexes').glob('*.json') if sha1(p) == info['assetIndex']['sha1']), None)
    if asset_index is None:
        raise RuntimeError('Official assets are missing. Run Gradle prepareLocalRuntime first.')
    game = CACHE / 'caches/fabric-loom/26.2/minecraft-client.jar'
    if version != '26.2':
        from deploy.client_release import prepare
        info, game, assets, asset_index = prepare(version, RUNTIME, CACHE)
    assert sha1(game) == info['downloads']['client']['sha1'], 'Official client checksum mismatch'
    paths = [game]
    manifest = [{'path': str(game), 'sha1': sha1(game)}]
    for lib in info['libraries']:
        allowed = not lib.get('rules')
        for rule in lib.get('rules', []):
            system = rule.get('os', {})
            if system.get('name', 'windows') == 'windows' and system.get('arch', 'x86_64') in ('x86_64', 'amd64'):
                allowed = rule['action'] == 'allow'
        if not allowed:
            continue
        artifact = lib.get('downloads', {}).get('artifact')
        if not artifact:
            continue
        group, artifact_name, version, *_ = lib['name'].split(':')
        filename = Path(artifact['path']).name
        candidates = list((CACHE / 'caches/modules-2/files-2.1' / group / artifact_name / version).glob('*/' + filename))
        found = next((p for p in candidates if sha1(p) == artifact['sha1']), None)
        if found is None:
            found = RUNTIME / 'official-libraries' / artifact['path']
            if not found.exists() or sha1(found) != artifact['sha1']:
                found.parent.mkdir(parents=True, exist_ok=True)
                print('Fetching official library', filename, flush=True)
                request = urllib.request.Request(artifact['url'], headers={'User-Agent': 'SmashVanillaLocalProbe/0.1'})
                found.write_bytes(urllib.request.urlopen(request, timeout=45).read())
            assert sha1(found) == artifact['sha1'], 'Library checksum mismatch: ' + filename
        paths.append(found)
        manifest.append({'path': str(found), 'sha1': artifact['sha1']})
    (RUNTIME / 'vanilla-client-manifest.json').write_text(json.dumps({'version': info['id'], 'mainClass': info['mainClass'], 'classpath': manifest}, indent=2), encoding='utf-8')
    client = RUNTIME / ('vanilla-client' if name == 'VanillaProbe' else name)
    client.mkdir(parents=True, exist_ok=True)
    options = client / 'options.txt'
    if not options.exists():
        options.write_text('fov:0.0\nguiScale:0\nrenderDistance:6\nsimulationDistance:5\nmaxFps:120\njoinedFirstServer:true\nonboardAccessibility:false\ntutorialStep:none\nautoJump:false\n', encoding='utf-8')
    args = ['-Xmx2G', '--sun-misc-unsafe-memory-access=allow', '--enable-native-access=ALL-UNNAMED',
            '-Djava.library.path=' + str(ROOT / '.gradle/loom-cache/natives' / version),
            '-cp', os.pathsep.join(map(str, paths)), info['mainClass'],
            '--offlineDeveloperMode', '--username', name, '--version', info['id'],
            '--gameDir', str(client), '--assetsDir', str(assets),
            '--assetIndex', asset_index.stem, '--uuid', uuid.uuid3(uuid.NAMESPACE_DNS, name).hex,
            '--accessToken', '0', '--versionType', 'release', '--width', '1280', '--height', '720',
            '--quickPlayMultiplayer', f'127.0.0.1:{PORT}']
    return args


def main():
    global RUNTIME, PORT
    parser = argparse.ArgumentParser()
    parser.add_argument('--smoke-seconds', type=int, default=0, help='Automatically close the client after verifying a vanilla connection')
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--players', type=int, choices=[1, 4], default=1, help='Four stock clients for a bounded connection/queue smoke test')
    parser.add_argument('--runtime-dir', type=Path, default=RUNTIME, help='Separate local world, clients and logs for isolated testing')
    parser.add_argument('--port', type=int, default=PORT, help='Loopback server port for isolated testing')
    options = parser.parse_args()
    if not 1 <= options.port <= 65535:
        parser.error('--port must be between 1 and 65535')
    if options.players == 4 and not options.smoke_seconds:
        parser.error('--players 4 requires --smoke-seconds so the extra test clients close automatically')
    RUNTIME = options.runtime_dir.resolve(); PORT = options.port
    # Check before Gradle can touch files held by an older launcher without a lock.
    require_free_port(PORT)
    with launcher_lock(RUNTIME):
        launch(options)


def launch(options):
    os.chdir(ROOT)
    require_free_port(PORT)
    if not options.skip_build:
        subprocess.run([str(ROOT / 'gradlew.bat'), '--gradle-user-home', str(CACHE), '-PlocalRuntime',
                        '-PsmashRuntimeDir=' + str(RUNTIME), 'prepareLocalRuntime'], check=True,
                       env={**os.environ, 'JAVA_HOME': str(JAVA_HOME)})
    require_free_port(PORT)
    server_dir = RUNTIME / 'server'
    server_dir.mkdir(parents=True, exist_ok=True)
    (server_dir / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    (server_dir / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nenforce-secure-profile=false\n'
        'level-name=smash-vanilla-mvp\nlevel-type=minecraft:flat\n'
        'generator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:the_void"}\n'
        'gamemode=adventure\ndifficulty=normal\nview-distance=6\nsimulation-distance=5\n'
        'spawn-protection=0\nallow-flight=true\npause-when-empty-seconds=0\nmax-players=20\n'
        'motd=Smash Vanilla MVP\n', encoding='utf-8')
    identities = ['VanillaProbe'] if options.players == 1 else ['VanillaOne', 'VanillaTwo', 'VanillaThree', 'VanillaFour']
    launch_args = [official_client(name) for name in identities]
    java = str(JAVA_HOME / 'bin/java.exe')
    server_args = [java, '-Xmx2G', '--sun-misc-unsafe-memory-access=allow', '--enable-native-access=ALL-UNNAMED',
                   '-Dfabric.development=true', '-Dfabric.defaultModDistributionNamespace=official',
                   '-Dfabric.defaultMixinRemapType=static', '-cp', (RUNTIME / 'server-classpath.txt').read_text(encoding='utf-8'),
                   'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']
    if options.smoke_seconds:
        server_args.insert(1, '-Dsmash_vanilla.' + ('autoQueue' if options.players == 4 else 'autoPractice') + '=true')
    ready = threading.Event()
    verified = threading.Event()
    entered = threading.Event()
    brands = set()
    match_started = threading.Event()
    round_active = threading.Event()
    server_log = open(RUNTIME / 'server-console.log', 'w', encoding='utf-8')
    # CREATE_NO_WINDOW only hides the background server's console, never the game.
    server = subprocess.Popen(server_args, cwd=server_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace',
                              creationflags=subprocess.CREATE_NO_WINDOW)
    def read_server():
        for line in server.stdout:
            server_log.write(line); server_log.flush()
            if 'VANILLA_PROBE_READY' in line: ready.set()
            if 'VANILLA_PROBE_BRAND' in line and 'brand=vanilla' in line:
                brands.add(line.split('player=')[1].split()[0]); verified.set()
            if 'VANILLA_PROBE_ENTER' in line: entered.set()
            if 'VANILLA_PROBE_MATCH_STARTED players=4' in line: match_started.set()
            if 'VANILLA_PROBE_ROUND_ACTIVE humans=4 actors=4 cameras=4' in line: round_active.set()
            if 'VANILLA_PROBE' in line: print(line.strip(), flush=True)
    threading.Thread(target=read_server, daemon=True).start()
    clients = []
    client_logs = []
    try:
        deadline = time.monotonic() + 90
        while not ready.wait(.25):
            if server.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError(f'Server did not start. See {RUNTIME / "server-console.log"}.')
        print('Launching Smash Vanilla MVP — normal Minecraft 26.2 client.', flush=True)
        print('In Mythical Garden, use Play or Practice from your hotbar. Keep F5 in first person during battle.', flush=True)
        for name, args in zip(identities, launch_args):
            out = open(RUNTIME / (name + '-console.log'), 'w', encoding='utf-8'); client_logs.append(out)
            game_dir = args[args.index('--gameDir') + 1]
            clients.append(subprocess.Popen([java, *args], cwd=game_dir, stdout=out, stderr=subprocess.STDOUT))
        if options.smoke_seconds:
            deadline = time.monotonic() + options.smoke_seconds
            while time.monotonic() < deadline and all(client.poll() is None for client in clients):
                time.sleep(.25)
            assert verified.is_set() and entered.is_set(), 'Stock client did not connect and enter arena; inspect runtime logs'
            assert len(brands) == options.players, 'Not all clients reported vanilla brand'
            assert all(client.poll() is None for client in clients), 'A stock client exited during the smoke test'
            if options.players == 4:
                assert match_started.is_set(), 'Four-player queue failed to start'
                assert round_active.is_set(), 'Four-player round failed to become active with all cameras intact'
            print(f'STOCK_CLIENT_SMOKE_PASSED: {options.players} official client(s), verified checksums, vanilla brands, arena entry.', flush=True)
        else:
            if clients[0].wait() != 0:
                raise RuntimeError(f'Minecraft exited with an error. See {RUNTIME / (identities[0] + "-console.log")}.')
    finally:
        for client in clients:
            if client.poll() is None:
                client.terminate(); client.wait(timeout=15)
        if server.poll() is None:
            server.stdin.write('stop\n'); server.stdin.flush()
            try: server.wait(timeout=30)
            except subprocess.TimeoutExpired: server.terminate(); server.wait(timeout=15)
        server_log.close()
        for out in client_logs: out.close()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f'\nCould not start Smash: {error}', file=sys.stderr, flush=True)
        sys.exit(1)
