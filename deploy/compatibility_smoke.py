"""Two unmodified official clients on the isolated matchmaking test network.

Use compose.smoke.yaml + compose.matchmaking.yaml, never the live network.
Tests translation, pack application, mixed-version duels and backend transfers.
Optional menu pause gives time to check real mouse input and rendering visually.
"""
import argparse
from functools import partial
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
import json
from pathlib import Path
import struct
import subprocess
import sys
import threading
import time
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from launch_local import official_client, JAVA_HOME, ROOT
from deploy.manage import Admin, wait_for
from deploy.status_ping import check_versions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--secrets', type=Path, default=ROOT / 'deploy/secrets')
    parser.add_argument('--menu-seconds', type=int, default=0)
    args = parser.parse_args()
    if not 0 <= args.menu_seconds <= 600:
        parser.error('Menu pause must be between 0 and 600 seconds')
    evidence = ROOT / 'evidence/compatibility'
    evidence.mkdir(parents=True, exist_ok=True)
    admin = Admin('http://127.0.0.1:18080', args.secrets / 'admin')
    lobby = Admin('http://127.0.0.1:18083', args.secrets / 'control')
    workers = [Admin(f'http://127.0.0.1:{port}', args.secrets / 'control') for port in (18081,18082)]
    names = ['Compat262', 'Compat263']
    clients, outputs, checks = [], [], []
    passed = False
    pack_server = ThreadingHTTPServer(('127.0.0.1',18084), partial(SimpleHTTPRequestHandler,directory=str(ROOT/'resourcepacks')))
    threading.Thread(target=pack_server.serve_forever,daemon=True).start()

    def check(message):
        checks.append(message)
        print('PASS ' + message,flush=True)
    def act(index, action='status', argument=''):
        # Lobby arrival precedes completion of the server's guarded handoff.
        for attempt in range(40):
            try:
                return lobby.call('/test/matchmaking', {'player': names[index], 'action': action, 'argument': argument})
            except urllib.error.HTTPError as error:
                if error.code != 409 or attempt == 39:raise
                time.sleep(.25)
    def home(status):
        assert all(p.poll() is None for p in clients), 'Stock client crashed; inspect client logs'
        return not status['matches'] and len(status['nodes']['lobby']['status']['players']) == 2
    try:
        start = wait_for(admin,lambda s:s.get('ready') and len(s['nodes'])==3 and all(n['healthy'] and n['status']['ready'] for n in s['nodes'].values()),240,'Test network not ready')
        assert not start['matches'] and all(not n['status']['players'] for n in start['nodes'].values()), 'Use an empty isolated network'
        check_versions()
        check('Server list accurately advertises both supported versions and rejects older protocols')
        for i,version in enumerate(('26.2','26.3')):
            command = official_client(names[i], version)
            command[command.index('--quickPlayMultiplayer')+1]='127.0.0.1:25577'
            command[0]='-Xmx1G'
            directory=Path(command[command.index('--gameDir')+1])
            def string(value):
                data=value.encode();return struct.pack('>H',len(data))+data
            entry=b'\x08'+string('name')+string('Compatibility test')+b'\x08'+string('ip')+string('127.0.0.1:25577')+b'\x01'+string('acceptTextures')+b'\x01\x00'
            (directory/'servers.dat').write_bytes(b'\x0a\x00\x00\x09'+string('servers')+b'\x0a'+struct.pack('>i',1)+entry+b'\x00')
            (directory/'options.txt').write_text('fov:0.0\nguiScale:2\nrenderDistance:4\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\njoinedFirstServer:true\nonboardAccessibility:false\ntutorialStep:none\nautoJump:false\n')
            output=(evidence/f'client-{version}.log').open('w',encoding='utf-8');outputs.append(output)
            clients.append(subprocess.Popen([str(JAVA_HOME/'bin/java.exe'),*command],cwd=directory,stdout=output,stderr=subprocess.STDOUT))
        wait_for(admin,home,120,'Stock clients failed to reach lobby')
        for _ in range(120):
            if all(act(i).get('packReady') for i in range(2)):break
            time.sleep(.5)
        else:raise AssertionError('Resource pack failed to apply')
        check('26.2 and 26.3 stock clients connected and successfully loaded the same UI pack')
        for i in range(2):act(i,'select','DUEL')
        print('MENUS_OPEN',flush=True)
        time.sleep(max(2,args.menu_seconds))
        for i,fighter in enumerate(('SKELETON','ZOMBIE')):act(i,'ready',fighter)
        running=wait_for(admin,lambda s:len(s['matches'])==1 and s['matches'][0]['running'],60,'Mixed-version duel failed to start')
        assert len(running['matches'][0]['roster'])==2
        check('26.2 and 26.3 players selected fighters and entered the same duel')
        time.sleep(45)
        current=admin.call()
        assert len(current['matches'])==1 and all(p.poll() is None for p in clients)
        node=current['matches'][0]['worker']
        assert current['nodes'][node]['status']['phase']=='PLAYING'
        assert len(current['nodes'][node]['status']['players'])==2
        # Finish only this isolated test's reservation through its guarded test API.
        workers[0 if node=='arena-a' else 1].call('/test/finish',{})
        wait_for(admin,home,60,'Mixed-version players did not return to lobby')
        for _ in range(120):
            if all(act(i).get('packReady') for i in range(2)):break
            time.sleep(.5)
        else:raise AssertionError('Cached pack failed to confirm after transfer')
        check('Both versions stayed connected through battle, results and return; cached pack confirmed')
        passed = True
        print('COMPATIBILITY_SMOKE_PASSED',flush=True)
    finally:
        for client in clients:
            if client.poll() is None:client.terminate();client.wait(timeout=15)
        for output in outputs:output.close()
        pack_server.shutdown();pack_server.server_close()
        (evidence/'result.json').write_text(json.dumps({'passed':passed,'checks':checks},indent=2))


if __name__=='__main__':main()
