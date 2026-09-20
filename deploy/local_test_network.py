"""Run compatibility smoke tests on an isolated Windows loopback network, without Docker.

Prepare with Gradle prepareLocalRuntime -PsmashRuntimeDir=evidence/compatibility/runtime
and build containerArtifacts first. Uses the same proxy, forwarding and application jars
as the container images. Generates its own disposable credentials; no live credentials.
"""
import argparse
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from launch_local import ROOT, JAVA_HOME, require_free_port
from deploy.manage import Admin, wait_for


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--menu-seconds',type=int,default=0)
    args=parser.parse_args()
    base=ROOT/'evidence/compatibility/runtime'
    for port in (25577,25601,25602,25603,18080,18081,18082,18083,18084):require_free_port(port)
    credentials=base/'secrets';credentials.mkdir(parents=True,exist_ok=True)
    for name in ('control','admin','forwarding'):
        path=credentials/name
        if not path.exists():path.write_text(secrets.token_hex(32))
    pack_hash=json.loads((ROOT/'src/main/resources/ui/index.json').read_text(encoding='utf-8'))['sha1']
    shared={**os.environ,'SMASH_CONTROL_SECRET_FILE':str(credentials/'control'),
        'FABRIC_PROXY_SECRET_FILE':str(credentials/'forwarding'),'FABRIC_PROXY_HACK_ONLINE_MODE':'false',
        'FABRIC_PROXY_HACK_MESSAGE_CHAIN':'true','SMASH_TEST_CONTROL':'true'}
    java=str(JAVA_HOME/'bin/java.exe');processes=[];outputs=[];topology=[]
    try:
        for name,role,port,control in [('lobby','LOBBY',25601,18083),('arena-a','ARENA',25602,18081),('arena-b','ARENA',25603,18082)]:
            folder=base/name;(folder/'mods').mkdir(parents=True,exist_ok=True)
            shutil.copyfile(ROOT/'deploy/vendor/FabricProxy-Lite-2.12.0.jar',folder/'mods/forwarder.jar')
            (folder/'eula.txt').write_text('eula=true\n')
            (folder/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\n'
                'level-name=compatibility\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:the_void"}\n'
                'gamemode=adventure\ndifficulty=normal\nview-distance=5\nsimulation-distance=5\nspawn-protection=0\nallow-flight=true\npause-when-empty-seconds=0\n')
            output=(folder/'console.log').open('w',encoding='utf-8');outputs.append(output)
            command=[java,'-Xmx1536M','--sun-misc-unsafe-memory-access=allow','--enable-native-access=ALL-UNNAMED',
                '-Dfabric.development=true','-Dfabric.defaultModDistributionNamespace=official','-Dfabric.defaultMixinRemapType=static',
                '-cp',(base/'server-classpath.txt').read_text(),'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
            env={**shared,'SMASH_ROLE':role,'SMASH_NODE_ID':name,'SMASH_CONTROL_PORT':str(control),
                'SMASH_RESOURCE_PACK_URL':f'http://127.0.0.1:18084/{pack_hash}.zip'}
            processes.append(subprocess.Popen(command,cwd=folder,env=env,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW))
            topology.append({'id':name,'role':role,'host':'127.0.0.1','port':port,'controlUrl':f'http://127.0.0.1:{control}'})
        proxy=base/'proxy';(proxy/'plugins/viaversion').mkdir(parents=True,exist_ok=True)
        for source,target in [(ROOT/'build/container/smash-proxy.jar',proxy/'plugins/smash-proxy.jar'),
                              (ROOT/'deploy/vendor/viaversion.jar',proxy/'plugins/viaversion.jar'),
                              (ROOT/'deploy/viaversion.yml',proxy/'plugins/viaversion/config.yml')]:shutil.copyfile(source,target)
        config=(ROOT/'deploy/velocity.toml').read_text().replace('0.0.0.0:25565','127.0.0.1:25577').replace('/run/secrets/forwarding',(credentials/'forwarding').as_posix()).replace('lobby:25565','127.0.0.1:25601')
        for key,value in [('online-mode','false'),('force-key-authentication','false'),('login-ratelimit','0')]:
            config,count=re.subn(r'(?m)^'+key+r'\s*=.*$',key+' = '+value,config)
            assert count==1, 'Missing isolated-test option '+key
        (proxy/'velocity.toml').write_text(config)
        (proxy/'network.json').write_text(json.dumps(topology))
        output=(proxy/'console.log').open('w',encoding='utf-8');outputs.append(output)
        processes.append(subprocess.Popen([java,'-Xmx512M','-jar',str(ROOT/'deploy/vendor/velocity.jar')],cwd=proxy,
            env={**shared,'SMASH_ADMIN_SECRET_FILE':str(credentials/'admin'),'SMASH_ADMIN_PORT':'18080','SMASH_TOPOLOGY_FILE':str(proxy/'network.json')},
            stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW))
        admin=Admin('http://127.0.0.1:18080',credentials/'admin')
        wait_for(admin,lambda s:s.get('ready') and all(n['healthy'] and n['status']['ready'] for n in s['nodes'].values()),240,'Local test network failed startup')
        print('LOCAL_TEST_NETWORK_READY',flush=True)
        subprocess.run([sys.executable,str(ROOT/'deploy/compatibility_smoke.py'),'--secrets',str(credentials),'--menu-seconds',str(args.menu_seconds)],check=True)
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                try:process.stdin.write(b'stop\n');process.stdin.flush();process.wait(timeout=25)
                except (OSError,subprocess.TimeoutExpired):process.terminate();process.wait(timeout=15)
        for output in outputs:output.close()


if __name__=='__main__':main()
