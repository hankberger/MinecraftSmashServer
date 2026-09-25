"""Four official clients: party readiness, duels, public FFA filling and lobby/arena handoff.

Requires compose.smoke.yaml + compose.matchmaking.yaml on the loopback-only test stack.
The private test endpoint drives server actions; native button input is covered by MatchmakingClientTest.
"""
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import uuid
import struct
import threading
from functools import partial
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from launch_local import official_client, JAVA_HOME, ROOT
from deploy.manage import Admin, wait_for, docker


def main():
    evidence = ROOT / 'evidence/packed-menu'
    evidence.mkdir(parents=True, exist_ok=True)
    admin = Admin()
    lobby = Admin('http://127.0.0.1:18083', ROOT / 'deploy/secrets/control')
    workers = {n: Admin(f'http://127.0.0.1:{port}', ROOT / 'deploy/secrets/control')
               for n, port in [('arena-a', 18081), ('arena-b', 18082)]}
    clients, outputs, checks = [], [], []
    names = ['Party' + uuid.uuid4().hex[:6] + str(i) for i in range(4)]
    since = datetime.now(timezone.utc).isoformat()
    compose = ['compose', '-p', 'smash-network-test', '-f', 'compose.yaml', '-f', 'deploy/compose.smoke.yaml', '-f', 'deploy/compose.matchmaking.yaml']
    pack_server=ThreadingHTTPServer(('127.0.0.1',18084),partial(SimpleHTTPRequestHandler,directory=str(ROOT/'resourcepacks')))
    threading.Thread(target=pack_server.serve_forever,daemon=True).start()

    def check(ok, message):
        assert ok, message
        checks.append(message)
        print('PASS ' + message, flush=True)

    def act(i, action='status', argument=''):
        for attempt in range(40):
            try:
                return lobby.call('/test/matchmaking', {'player': names[i], 'action': action, 'argument': argument})
            except urllib.error.HTTPError as error:
                if error.code != 409 or attempt == 39: raise
                time.sleep(.25)

    def home():
        status = wait_for(admin, lambda s: not s['matches'] and len(s['nodes']['lobby']['status']['players']) == 4
                          and not s['nodes']['lobby']['status']['selections'], 50, 'Players failed to return home')
        # A returning backend connection must confirm its cached pack before opening a menu.
        for _ in range(160):
            if all(act(i).get('packReady') for i in range(4)): break
            time.sleep(.25)
        else: raise AssertionError('Menu pack failed to load after lobby arrival')
        return status

    try:
        initial = wait_for(admin, lambda s: s.get('protocol') == 5 and s.get('ready') and len(s['nodes']) == 3
                           and all(n['healthy'] and ({'lobby': lobby, **workers}[name]).call()['boot'] == n['status']['boot']
                                   for name, n in s['nodes'].items()), 240, 'Network unavailable')
        check(not initial['matches'] and all(not n['status']['players'] for n in initial['nodes'].values()), 'Isolated network is empty')
        for worker in workers: admin.call('/resume', {'node': worker})
        for i, name in enumerate(names):
            command = official_client(name)
            for option, value in [('--quickPlayMultiplayer', '127.0.0.1:25577'), ('--width', '640'), ('--height', '360')]:
                command[command.index(option) + 1] = value
            command[0] = '-Xmx640M'
            command[1:1] = ['-Xms128M', '-XX:ActiveProcessorCount=2', '-XX:MaxDirectMemorySize=128M']
            directory = Path(command[command.index('--gameDir') + 1])
            # This disposable client accepts only the loopback test server's pack.
            # Encode Minecraft's uncompressed servers.dat NBT without extra libraries.
            def string(value):
                data=value.encode('utf-8');return struct.pack('>H',len(data))+data
            entry=b'\x08'+string('name')+string('Smash test')+b'\x08'+string('ip')+string('127.0.0.1:25577')+b'\x01'+string('acceptTextures')+b'\x01\x00'
            (directory/'servers.dat').write_bytes(b'\x0a\x00\x00\x09'+string('servers')+b'\x0a'+struct.pack('>i',1)+entry+b'\x00')
            (directory / 'options.txt').write_text('fov:0.0\nguiScale:2\nrenderDistance:2\nsimulationDistance:5\nmaxFps:10\nenableVsync:false\ngraphicsMode:0\nmipmapLevels:0\nparticles:2\nrenderClouds:false\njoinedFirstServer:true\nonboardAccessibility:false\ntutorialStep:none\nautoJump:false\n')
            output = (evidence / f'stock-client-{i}.log').open('w', encoding='utf-8'); outputs.append(output)
            clients.append(subprocess.Popen([str(JAVA_HOME / 'bin/java.exe'), *command], cwd=directory, stdout=output, stderr=subprocess.STDOUT))
            time.sleep(5)
        home(); time.sleep(3)
        check(all(p.poll() is None for p in clients), 'Four unmodified clients connected through Velocity')
        check(all(act(i)['packReady'] for i in range(4)), 'Four stock clients downloaded and applied the UI pack')
        act(0, 'create'); act(0, 'invite', names[1]); joined = act(1, 'accept', names[0])['party']
        check(len(joined['members']) == 2, 'Accepted party contains two friends')
        group = {m['id'] for m in joined['members']}
        act(0, 'select', 'DUEL'); time.sleep(2); one = act(0, 'ready', 'ZOMBIE')['party']
        time.sleep(2)
        check(sum(m['ready'] for m in one['members']) == 1 and not admin.call()['matches']
              and not admin.call()['nodes']['lobby']['status']['selections'], 'Half-ready party never reaches the proxy queue')
        act(1, 'ready', 'SKELETON')
        act(2, 'select', 'DUEL'); act(3, 'select', 'DUEL'); time.sleep(2)
        act(2, 'ready', 'STEVE'); act(3, 'ready', 'ALEX')
        duels = wait_for(admin, lambda s: len(s['matches']) == 2 and all(m['running'] for m in s['matches']), 60, 'Two duels failed to start')
        check(all(len(m['roster']) == 2 and all(t['mode'] == 'DUEL' for t in m['roster']) for m in duels['matches'])
              and any({t['player'] for t in m['roster']} == group for m in duels['matches']), 'Party duel and public duel run in separate containers')
        (evidence / 'network-duels.json').write_text(json.dumps(duels, indent=2))
        time.sleep(6)
        for worker in workers.values(): worker.call('/test/finish', {})
        returned = home()
        check(act(0)['party']['id'] == joined['id'] and len(act(1)['party']['members']) == 2
              and act(0)['party']['phase'] == 'IDLE', 'Party survives both arena transfers and resets readiness after results')
        for worker in workers: admin.call('/drain', {'node': worker})
        proxy_id = docker([*compose, 'ps', '-q', 'proxy'], capture=True).strip()
        boots = {w: returned['nodes'][w]['status']['boot'] for w in workers}
        docker([*compose, 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', '--force-recreate', 'arena-b', 'arena-a'],
               env={'BACKEND_IMAGE': 'smash-backend:dev', 'ARENA_A_IMAGE': 'smash-backend:dev', 'ARENA_B_IMAGE': 'smash-backend:dev'})
        wait_for(admin, lambda s: all(s['nodes'][w]['healthy'] and s['nodes'][w]['status']['boot'] != boots[w]
                                     and s['nodes'][w]['status']['phase'] == 'IDLE' and w in s['draining']
                                     for w in workers), 180, 'Replaced workers failed readiness')
        for worker in workers: admin.call('/drain', {'node': worker})
        check(docker([*compose, 'ps', '-q', 'proxy'], capture=True).strip() == proxy_id,
              'Arena containers replaced while the gateway and lobby clients stayed connected')
        act(0, 'select', 'MATCH'); time.sleep(2); act(0, 'ready', 'VILLAGER'); act(1, 'ready', 'SKELETON')
        act(1, 'change'); time.sleep(2)
        check(not admin.call()['nodes']['lobby']['status']['selections'], 'Changing a queued fighter withdraws the entire party')
        act(1, 'ready', 'ALEX')
        for i in (2, 3): act(i, 'select', 'MATCH')
        time.sleep(2)
        for i in (2, 3): act(i, 'ready', 'STEVE')
        queued = wait_for(admin, lambda s: len(s['queue']) == 4, 20, 'Ready FFA did not reach queue')
        check(not queued['matches'], 'Ready four-player roster waits while workers are drained')
        for worker in workers: admin.call('/resume', {'node': worker})
        ffa = wait_for(admin, lambda s: len(s['matches']) == 1 and s['matches'][0]['running'], 60, 'FFA failed to start')
        roster = ffa['matches'][0]['roster']
        check(len(roster) == 4 and {t['player'] for t in roster if t['groupSize'] == 2} == group
              and next(t for t in roster if t['player'] == joined['members'][1]['id'])['fighter'] == 'ALEX',
              'Party stays together, fills with two public opponents, and uses the changed class')
        (evidence / 'network-ffa.json').write_text(json.dumps(ffa, indent=2))
        time.sleep(6); workers[ffa['matches'][0]['worker']].call('/test/finish', {}); home()
        reports = [act(i) for i in range(4)]
        for attempt in range(40):
            if all(r.get('winnerReady') for r in reports): break
            time.sleep(.25); reports = [act(i) for i in range(4)]
        check(all(r.get('winnerStage') and r.get('winnerReady') for r in reports) and not admin.call()['matches'],
              'All four stock clients reach their victory stages while arena containers are free')
        check(all(r.get('result', {}).get('id') == ffa['matches'][0]['id'] for r in reports),
              'Immutable winner and stats arrive at the lobby for all four stock clients')
        for worker in workers: admin.call('/drain', {'node': worker})
        for i in range(3): act(i, 'rematch')
        time.sleep(1)
        check(not lobby.call()['selections'] and act(0)['votes'] == 3, 'Three rematch votes cannot queue a fourth player')
        act(3, 'rematch')
        rematch_queue = wait_for(admin, lambda s: len(s['queue']) == 4, 20, 'Unanimous rematch did not queue')
        check(all(t.get('rematch') == ffa['matches'][0]['id'] for t in rematch_queue['queue'])
              and {t['player'] for t in rematch_queue['queue'] if t['groupSize'] == 2} == group,
              'Rematch locks original opponents without merging their parties')
        act(3, 'cancel'); time.sleep(1)
        check(not lobby.call()['selections'] and all(act(i)['party']['phase'] == 'IDLE' for i in range(4)),
              'Cancelling a queued rematch withdraws every player and restores original parties')
        # Play another ordinary round, then exercise an actual cross-container rematch.
        act(0, 'select', 'MATCH'); time.sleep(2); act(0, 'ready', 'VILLAGER'); act(1, 'ready', 'ALEX')
        for i in (2,3): act(i, 'select', 'MATCH')
        time.sleep(2)
        for i in (2,3): act(i, 'ready', 'STEVE')
        for worker in workers: admin.call('/resume', {'node': worker})
        again = wait_for(admin, lambda s: len(s['matches']) == 1 and s['matches'][0]['running'], 60, 'Follow-up round failed')
        time.sleep(6); workers[again['matches'][0]['worker']].call('/test/finish', {}); home()
        for i in range(4): act(i, 'rematch')
        repeat = wait_for(admin, lambda s: len(s['matches']) == 1 and s['matches'][0]['running'], 60, 'Exact rematch failed')
        check({(t['player'],t['fighter']) for t in repeat['matches'][0]['roster']} == {(t['player'],t['fighter']) for t in again['matches'][0]['roster']}
              and all(t['rematch'] == again['matches'][0]['id'] for t in repeat['matches'][0]['roster']),
              'Accepted rematch runs with exactly the same four players and fighters')
        time.sleep(6); workers[repeat['matches'][0]['worker']].call('/test/finish', {}); home()
        act(0, 'replay'); time.sleep(1)
        check(act(0)['party']['phase'] == 'SELECTING' and sum(m['ready'] for m in act(0)['party']['members']) == 1
              and not lobby.call()['selections'], 'Play again preserves fighters and waits for party consent')
        act(1, 'ready-saved'); act(2, 'replay'); act(3, 'replay')
        public = wait_for(admin, lambda s: len(s['matches']) == 1 and s['matches'][0]['running'], 60, 'Play again failed')
        check(all(not t.get('rematch') for t in public['matches'][0]['roster']), 'Play again returns parties to ordinary public matchmaking')
        time.sleep(6); workers[public['matches'][0]['worker']].call('/test/finish', {}); home()
        check(all(p.poll() is None for p in clients), 'All four stock clients returned without reconnecting')
        logs = docker([*compose, 'logs', '--no-color', '--since', since], capture=True)
        (evidence / 'network-containers.log').write_text(logs, encoding='utf-8')
        check(logs.count('brand=vanilla') >= 4 and 'SMASH_TRANSFER_FAILED' not in logs and 'Matchmaker tick failed' not in logs,
              'Vanilla brands confirmed; no transfer or coordinator errors')
        (evidence / 'network-result.json').write_text(json.dumps({'passed': True, 'checks': checks}, indent=2))
        print('MATCHMAKING_STOCK_NETWORK_PASSED', flush=True)
    finally:
        try: (evidence / 'network-last-status.json').write_text(json.dumps(admin.call(), indent=2))
        except OSError: pass
        for p in clients:
            if p.poll() is None: p.terminate(); p.wait(timeout=15)
        for output in outputs: output.close()
        pack_server.shutdown();pack_server.server_close()


if __name__ == '__main__':
    main()
