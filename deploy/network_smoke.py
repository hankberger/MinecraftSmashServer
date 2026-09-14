"""Eight stock clients, two simultaneous matches, drain, worker replacement and return.

Requires the explicitly isolated compose.smoke.yaml stack and Windows client cache.
Never enables offline mode on the production configuration.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from launch_local import official_client, JAVA_HOME, ROOT, RUNTIME
from deploy.manage import Admin, wait_for, rollout, docker


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', default='smash-network-test')
    args = parser.parse_args()
    if not args.project.startswith('smash-network-test'):
        parser.error('Use a separate smash-network-test project.')
    evidence = ROOT / 'evidence/network-0.3.0'
    evidence.mkdir(parents=True, exist_ok=True)
    admin = Admin()
    worker_a = Admin('http://127.0.0.1:18081', ROOT / 'deploy/secrets/control')
    worker_b = Admin('http://127.0.0.1:18082', ROOT / 'deploy/secrets/control')
    clients, outputs = [], []
    compose = ['compose', '-p', args.project, '-f', 'compose.yaml', '-f', 'deploy/compose.smoke.yaml']
    result = {'checks': []}
    since = datetime.now(timezone.utc).isoformat()

    def check(message):
        result['checks'].append(message)
        print('PASS ' + message, flush=True)

    def save(name, data):
        (evidence / (name + '.json')).write_text(json.dumps(data, indent=2))

    try:
        start = wait_for(admin, lambda s: len(s.get('nodes', {})) == 3 and all(n['healthy'] and n['status']['ready'] for n in s['nodes'].values()),
                         240, 'Network failed initial readiness')
        assert not start['matches'] and all(not n['status']['players'] for n in start['nodes'].values()), 'Test stack must be empty'
        for url in ('http://127.0.0.1:18080/status', 'http://127.0.0.1:18081/status'):
            try:
                urllib.request.urlopen(url, timeout=5)
                raise AssertionError('Unauthenticated control request accepted')
            except urllib.error.HTTPError as error:
                assert error.code == 401
        check('Admin and worker controls require authentication')
        # Drain both so direct protocol checks cannot race the matchmaker.
        for node in ('arena-a', 'arena-b'):
            admin.call('/drain', {'node': node})
        wait_for(admin, lambda s: all(s['nodes'][n]['status']['drained'] for n in ('arena-a', 'arena-b')), 20, 'Drain failed')
        selection = str(uuid.uuid4())
        reservation = {'id': str(uuid.uuid4()), 'roster': [{'player': str(uuid.uuid4()), 'fighter': 'STEVE', 'mode': 'PRACTICE', 'selection': selection, 'group': selection, 'groupSize': 1}]}
        try:
            worker_a.call('/reserve', reservation)
            raise AssertionError('Drained arena accepted reservation')
        except urllib.error.HTTPError as error:
            assert error.code == 409
        check('Drained arena refuses new reservations')
        for node in ('arena-a', 'arena-b'):
            admin.call('/resume', {'node': node})
        suffix = uuid.uuid4().hex[:5]
        for index in range(8):
            name = f'Fleet{suffix}{index}'
            command = official_client(name)
            for option, value in (('--quickPlayMultiplayer', '127.0.0.1:25577'), ('--width', '640'), ('--height', '360')):
                command[command.index(option) + 1] = value
            command[0] = '-Xmx640M'
            command[1:1] = ['-Xms128M', '-XX:ActiveProcessorCount=2', '-XX:MaxDirectMemorySize=128M']
            game_dir = Path(command[command.index('--gameDir') + 1])
            (game_dir / 'options.txt').write_text('fov:0.0\nguiScale:2\nrenderDistance:2\nsimulationDistance:5\nmaxFps:10\nenableVsync:false\ngraphicsMode:0\nmipmapLevels:0\nparticles:2\nrenderClouds:false\nentityDistanceScaling:0.5\njoinedFirstServer:true\nonboardAccessibility:false\ntutorialStep:none\nautoJump:false\n')
            output = (evidence / f'client-{index}.log').open('w', encoding='utf-8')
            outputs.append(output)
            clients.append(subprocess.Popen([str(JAVA_HOME / 'bin/java.exe'), *command], cwd=game_dir, stdout=output, stderr=subprocess.STDOUT))
            time.sleep(5)
        def two_matches(s):
            assert all(p.poll() is None for p in clients), 'A stock client crashed; inspect client logs'
            return len(s.get('matches', [])) == 2 and all(m['running'] for m in s['matches']) and all(len(s['nodes'][n]['status']['players']) == 4 for n in ('arena-a', 'arena-b'))

        running = wait_for(admin, two_matches,
                           180, 'Eight clients did not form two matches')
        save('two-matches', running)
        assert all(p.poll() is None for p in clients), 'A client crashed'
        roster = [t['player'] for m in running['matches'] for t in m['roster']]
        assert len(roster) == len(set(roster)) == 8
        check('Eight official vanilla clients placed into two disjoint four-player matches')
        # Allow countdown and arena cameras to become active before the rolling update.
        time.sleep(8)
        before_b = running['nodes']['arena-b']['status']
        admin.call('/drain', {'node': 'arena-a'})
        draining = wait_for(admin, lambda s: s['nodes']['arena-a']['status']['draining'] and 'arena-a' in s.get('draining', []), 15, 'Drain not acknowledged')
        assert not draining['nodes']['arena-a']['status']['ready']
        assert draining['nodes']['arena-a']['status']['reservation'] == running['nodes']['arena-a']['status']['reservation']
        check('Drain preserves the current match and removes its worker from allocation')

        def finish_later():
            time.sleep(5)
            worker_a.call('/test/finish', {})

        finisher = threading.Thread(target=finish_later)
        finisher.start()
        rolled = rollout(admin, 'arena-a', 'smash-backend:dev', args.project, ['deploy/compose.smoke.yaml'],
                         timeout=60, startup_timeout=180, local_image=True, persist=False)
        finisher.join()
        save('rollout', rolled)
        after = admin.call()
        after_b = after['nodes']['arena-b']['status']
        assert before_b['boot'] == after_b['boot'] and before_b['reservation'] == after_b['reservation']
        assert after_b['phase'] == 'PLAYING' and len(after_b['players']) == 4
        assert len(after['nodes']['lobby']['status']['players']) == 4
        assert all(p.poll() is None for p in clients)
        check('Arena A replaced after returning its players; Arena B continued with the same match and server process')
        worker_b.call('/test/finish', {})
        returned = wait_for(admin, lambda s: not s['matches'] and len(s['nodes']['lobby']['status']['players']) == 8 and all(s['nodes'][n]['status']['ready'] for n in ('arena-a', 'arena-b')), 45, 'Clients failed to return to lobby')
        save('returned', returned)
        check('All eight clients returned to the lobby and both arenas became reusable')
        logs = docker([*compose, 'logs', '--no-color', '--since', since], capture=True)
        (evidence / 'containers.log').write_text(logs, encoding='utf-8')
        assert 'VANILLA_PROBE_ROUND_ACTIVE humans=4 actors=4 cameras=4' in logs
        assert logs.count('brand=vanilla') >= 8
        assert 'SMASH_TRANSFER_FAILED' not in logs and 'Matchmaker tick failed' not in logs
        check('Vanilla client brands, active server-side cameras and successful transfers confirmed in logs')
        result['passed'] = True
        save('result', result)
        (evidence / 'vanilla-client-manifest.json').write_bytes((RUNTIME / 'vanilla-client-manifest.json').read_bytes())
        print('NETWORK_SMOKE_PASSED', flush=True)
    finally:
        for client in clients:
            if client.poll() is None:
                client.terminate()
                client.wait(timeout=15)
        for output in outputs:
            output.close()
        save('result', result)


if __name__ == '__main__':
    main()
