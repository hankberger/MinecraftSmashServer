"""Drain and roll one arena without interrupting matches on other workers.

Run on the Docker host, or access the admin API over an SSH tunnel. Python 3.11+.
"""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


class Admin:
    def __init__(self, url='http://127.0.0.1:18080', secret=ROOT / 'deploy/secrets/admin'):
        self.url = url.rstrip('/')
        self.token = Path(secret).read_text().strip()

    def call(self, path='/status', data=None):
        request = urllib.request.Request(self.url + path,
            data=None if data is None else json.dumps(data).encode(),
            headers={'Authorization': 'Bearer ' + self.token, 'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.load(response)


def wait_for(admin, predicate, timeout, description):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            status = admin.call()
            if predicate(status):
                return status
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise TimeoutError(description)


def docker(args, env=None, capture=False, timeout=600):
    return subprocess.run(['docker', *args], cwd=ROOT, check=True, text=True,
        env={**os.environ, **(env or {})}, stdout=subprocess.PIPE if capture else None, timeout=timeout).stdout


def persist_image(worker, image):
    # Preserve other settings and secrets; the image is validated before reaching here.
    key = worker.upper().replace('-', '_') + '_IMAGE'
    path = ROOT / '.env'
    lines = path.read_text().splitlines() if path.exists() else []
    lines = [line for line in lines if not re.match(r'\s*(?:export\s+)?' + key + r'\s*=', line)]
    lines.append(key + '=' + image)
    temporary = path.with_suffix('.env.tmp')
    temporary.write_text('\n'.join(lines) + '\n')
    temporary.replace(path)


@contextmanager
def deployment_lock():
    lock = ROOT / 'deploy/.rollout-lock'
    try:
        lock.mkdir()
    except FileExistsError:
        raise RuntimeError('Another deployment holds deploy/.rollout-lock. If it crashed, verify no deployment is running before removing the lock.') from None
    try:
        yield
    finally:
        lock.rmdir()


def rollout(*args, **kwargs):
    with deployment_lock():
        return _rollout(*args, **kwargs)


def _rollout(admin, worker, image, project='smash-network', extra_compose=(), timeout=660,
            startup_timeout=180, local_image=False, persist=True):
    if worker not in ('arena-a', 'arena-b'):
        raise ValueError('Rollouts support arena-a and arena-b; proxy/lobby updates require maintenance.')
    if not re.fullmatch(r'[a-zA-Z0-9_./:@-]+', image):
        raise ValueError('Invalid image reference')
    if not local_image and not re.search(r'@sha256:[0-9a-f]{64}$', image):
        raise ValueError('Use an immutable image@sha256:digest (or --local-image for a local development image).')
    compose = ['compose', '-p', project, '-f', 'compose.yaml']
    for path in extra_compose:
        compose += ['-f', str(path)]
    if not local_image:
        docker(['pull', image])
    docker(['image', 'inspect', image], capture=True)  # Download/validate before draining.
    container = docker([*compose, 'ps', '-q', worker], capture=True).strip()
    if not container:
        raise RuntimeError('Worker is not running; start the network before a rolling update.')
    old = json.loads(docker(['inspect', container], capture=True))[0]
    old_image = old['Image']  # Exact local content ID, including when a development tag changed.
    before = admin.call()['nodes'][worker]
    if not before['healthy']:
        raise RuntimeError('Worker is unhealthy; inspect it before attempting a rolling update.')
    boot = before['status']['boot']
    key = worker.upper().replace('-', '_') + '_IMAGE'
    print(f'Draining {worker}; existing matches may finish (up to {timeout}s).', flush=True)
    admin.call('/drain', {'node': worker})
    wait_for(admin, lambda s: s['nodes'][worker]['healthy'] and s['nodes'][worker]['status'].get('drained'),
             timeout, 'Drain timed out. Worker remains drained; no container was stopped. Use resume to cancel.')

    def replace(ref):
        docker([*compose, 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', '--force-recreate', worker], {key: ref})

    def healthy_new(s, previous_boot):
        n = s['nodes'][worker]
        return (worker in s.get('draining', []) and n['healthy'] and n['status'].get('boot') != previous_boot
                and n['status'].get('protocol') == s.get('protocol') and n['status'].get('phase') == 'IDLE'
                and not n['status'].get('players') and not n['status'].get('reservation'))

    try:
        print(f'Replacing {worker} with {image}.', flush=True)
        replace(image)
        after = wait_for(admin, lambda s: healthy_new(s, boot), startup_timeout, 'Replacement failed readiness.')
        # Persist before admitting new matches. A persistence failure can still roll back safely.
        if persist:
            persist_image(worker, image)
    except Exception:
        print(f'Update failed; restoring the previous image on {worker}.', flush=True)
        current = admin.call()['nodes'][worker]['status'].get('boot')
        replace(old_image)
        wait_for(admin, lambda s: healthy_new(s, current), startup_timeout, 'Rollback failed readiness; worker remains drained.')
        admin.call('/resume', {'node': worker})
        raise
    admin.call('/resume', {'node': worker})
    print(f'{worker} is healthy and accepting matches. Other containers were not restarted.', flush=True)
    return {'worker': worker, 'previousBoot': boot, 'boot': after['nodes'][worker]['status']['boot'],
            'previousImage': old_image, 'image': image}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:18080')
    parser.add_argument('--secret', type=Path, default=ROOT / 'deploy/secrets/admin')
    parser.add_argument('--project', default='smash-network')
    parser.add_argument('--compose', action='append', default=[], help='Additional Compose override; repeatable')
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('status')
    for command in ('drain', 'resume'):
        sub.add_parser(command).add_argument('worker', choices=['arena-a', 'arena-b'])
    roll = sub.add_parser('roll')
    roll.add_argument('worker', choices=['arena-a', 'arena-b'])
    roll.add_argument('--image', required=True)
    roll.add_argument('--timeout', type=int, default=660)
    roll.add_argument('--startup-timeout', type=int, default=180)
    roll.add_argument('--local-image', action='store_true')
    roll.add_argument('--no-persist', action='store_true', help='Do not save the successful image in .env (tests only)')
    args = parser.parse_args()
    admin = Admin(args.url, args.secret)
    if args.command == 'status':
        print(json.dumps(admin.call(), indent=2))
    elif args.command in ('drain', 'resume'):
        print(admin.call('/' + args.command, {'node': args.worker})['message'])
    else:
        rollout(admin, args.worker, args.image, args.project, args.compose, args.timeout,
                args.startup_timeout, args.local_image, not args.no_persist)


if __name__ == '__main__':
    main()
