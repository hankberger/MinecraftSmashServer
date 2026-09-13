"""Mac testing host: deploy successful main releases, including lobby and proxy.

Run `check` periodically with launchd. No GitHub credentials or inbound webhook.
Releases use separate Git worktrees and resolved Compose snapshots for rollback.
"""
import argparse
from datetime import datetime, timezone
import json
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import re
import subprocess
import sys

from manage import Admin, ROOT, deployment_lock, wait_for
from release import REPOSITORY, ReleaseNotReady, release

PROJECT = 'smash-network'
SERVICES = ('proxy', 'lobby', 'arena-a', 'arena-b')
WORKERS = ('arena-a', 'arena-b')
STATE = ROOT / 'deploy/auto-state.json'
PAUSED = ROOT / 'deploy/.auto-paused'
RELEASES = ROOT / 'build/deploy-releases'
LOG = logging.getLogger('smash-deploy')


def configure_docker():
    if sys.platform != 'darwin':
        return
    # Public GHCR images need no credentials. An explicit empty registry entry
    # also prevents Docker from selecting a default macOS Keychain helper.
    directory = ROOT / 'build/auto-docker'
    directory.mkdir(parents=True, exist_ok=True)
    (directory / 'config.json').write_text(json.dumps({'auths': {'ghcr.io': {}},
        'cliPluginsExtraDirs': [str(Path.home() / '.docker/cli-plugins')]}))
    os.environ['DOCKER_CONFIG'] = str(directory)
    os.environ['DOCKER_HOST'] = 'unix://' + str(Path.home() / '.docker/run/docker.sock')
    os.environ.pop('DOCKER_CONTEXT', None)


def now():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def read_state():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save_state(state, **changes):
    previous = (state.get('phase'), state.get('message'), state.get('target'))
    state.update(changes)
    temporary = STATE.with_suffix('.tmp')
    temporary.write_text(json.dumps(state, indent=2) + '\n')
    temporary.replace(STATE)
    if previous != (state.get('phase'), state.get('message'), state.get('target')):
        LOG.info('%s [%s] %s', state.get('phase'), str(state.get('target', ''))[:12], state.get('message', ''))


def run(args, *, cwd=ROOT, env=None, timeout=180):
    result = subprocess.run(args, cwd=cwd, env={**os.environ, **(env or {})}, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f'{args[0]} failed ({result.returncode}): {result.stderr[-3000:]} {result.stdout[-1000:]}')
    return result.stdout


def compose(config, *args, env=None):
    return run(['docker', 'compose', '-p', PROJECT, '-f', str(config), *args], env=env, timeout=300)


def head():
    value = run(['git', 'ls-remote', 'https://github.com/' + REPOSITORY + '.git', 'refs/heads/main'], timeout=45).split()
    if len(value) != 2 or value[1] != 'refs/heads/main' or not re.fullmatch('[0-9a-f]{40}', value[0]):
        raise RuntimeError('GitHub did not return a valid main commit.')
    return value[0]


def healthy(status):
    nodes = status.get('nodes', {})
    return (status.get('ready') and not status.get('draining') and set(nodes) == {'lobby', *WORKERS}
        and nodes['lobby']['status'].get('ready')
        and all(n.get('healthy') and n.get('status', {}).get('protocol') == 1
                and not n['status'].get('draining') for n in nodes.values()))


def image_env(selected):
    return {'PROXY_IMAGE': selected['proxy'], 'BACKEND_IMAGE': selected['backend'],
            'ARENA_A_IMAGE': selected['backend'], 'ARENA_B_IMAGE': selected['backend']}


def write_images(selected):
    path = ROOT / '.env'
    values = image_env(selected)
    lines = path.read_text().splitlines()
    lines = [line for line in lines if not any(re.match(r'\s*(?:export\s+)?' + key + r'\s*=', line) for key in values)]
    temporary = ROOT / '.env.tmp'
    temporary.write_text('\n'.join(lines + [key + '=' + value for key, value in values.items()]) + '\n')
    temporary.replace(path)


def snapshot(config, directory):
    """Capture the current configuration and exact running image IDs before changes."""
    old = json.loads(compose(config, 'config', '--format', 'json'))
    if set(old['services']) != set(SERVICES):
        raise RuntimeError('Automatic deployment currently supports the four-service testing network.')
    for service in SERVICES:
        container = compose(config, 'ps', '-q', service).strip()
        if not container:
            raise RuntimeError(f'{service} is not running; restore host health before deploying.')
        actual = json.loads(run(['docker', 'inspect', container]))[0]
        old['services'][service]['image'] = actual['Image']
        old['services'][service].pop('build', None)
    path = directory / 'previous-compose.json'
    path.write_text(json.dumps(old, indent=2))
    (directory / 'previous.env').write_bytes((ROOT / '.env').read_bytes())
    return path, old


def prepare(selected, state):
    sha = selected['commit']
    directory = RELEASES / sha
    directory.mkdir(parents=True, exist_ok=True)
    source = directory / 'source'
    if not source.exists():
        run(['git', 'fetch', '--no-tags', 'https://github.com/' + REPOSITORY + '.git', sha])
        run(['git', 'worktree', 'add', '--detach', str(source), sha])
    if run(['git', 'rev-parse', 'HEAD'], cwd=source).strip() != sha:
        raise RuntimeError('Staged checkout does not match the verified release.')
    if run(['git', 'status', '--porcelain'], cwd=source).strip():
        raise RuntimeError('Staged checkout has local edits; refusing to deploy it.')
    # Render the release's own Compose/topology, using the host's private settings.
    rendered = compose(source / 'compose.yaml', '--env-file', str(ROOT / '.env'),
                       'config', '--format', 'json', env=image_env(selected))
    config = json.loads(rendered)
    if set(config['services']) != set(SERVICES):
        raise RuntimeError('Topology changes require a coordinated host migration.')
    for name in ('control', 'admin', 'forwarding'):
        config['secrets'][name]['file'] = str(ROOT / 'deploy/secrets' / name)
    for service in SERVICES:
        expected = selected['proxy' if service == 'proxy' else 'backend']
        if config['services'][service]['image'] != expected:
            raise RuntimeError('Compose did not select the verified release image for ' + service)
        config['services'][service].pop('build', None)
    previous, old = snapshot(Path(state.get('activeConfig', ROOT / 'compose.yaml')), directory)
    if config.get('volumes') != old.get('volumes'):
        raise RuntimeError('Persistent volume changes require a manual migration.')
    target = directory / 'compose.json'
    target.write_text(json.dumps(config, indent=2))
    compose(target, 'config', '--quiet')
    # Pull both images before touching players or stopping a container.
    run(['docker', 'pull', selected['backend']], timeout=600)
    run(['docker', 'pull', selected['proxy']], timeout=600)
    return target, previous


def resume(admin):
    for worker in WORKERS:
        admin.call('/resume', {'node': worker})
    wait_for(admin, healthy, 30, 'Network did not resume matchmaking.')


def drain(admin):
    for worker in WORKERS:
        admin.call('/drain', {'node': worker})
    wait_for(admin, lambda s: all(s['nodes'][w]['healthy'] and s['nodes'][w]['status'].get('drained')
        for w in WORKERS), 660, 'Active sessions did not drain; update postponed.')


def ready(config, admin):
    # Compose probes and application ticks must both pass before marking a release ready.
    compose(config, 'up', '-d', '--no-build', '--pull', 'never', '--wait', '--wait-timeout', '240')
    initial = wait_for(admin, healthy, 60, 'Network failed readiness after container startup.')
    wait_for(admin, lambda s: healthy(s) and all(s['nodes'][n]['status']['tick'] > initial['nodes'][n]['status']['tick']
        for n in ('lobby', *WORKERS)), 15, 'Game ticks did not advance.')


def deploy(selected, state, target, previous):
    admin = Admin()
    wait_for(admin, healthy, 30, 'Current network is not healthy; no deployment started.')
    save_state(state, phase='draining', message='Waiting for active matches to finish.')
    try:
        drain(admin)
    except Exception:
        resume(admin)
        raise
    # A newer push supersedes this release while waiting for a long match.
    try:
        if head() != selected['commit']:
            resume(admin)
            save_state(state, phase='waiting', message='A newer main commit superseded this release.')
            return False
    except Exception:
        resume(admin)
        raise
    save_state(state, phase='restarting', rollbackConfig=str(previous), message='Restarting the testing network.')
    try:
        compose(previous, 'stop')
        compose(target, 'up', '-d', '--no-build', '--pull', 'never', '--force-recreate')
        save_state(state, phase='checking', message='Checking all four containers and game ticks.')
        ready(target, admin)
        write_images(selected)
    except Exception as error:
        save_state(state, phase='rolling-back', message='New release failed; restoring the previous containers.')
        try:
            compose(target, 'stop')
            compose(previous, 'up', '-d', '--no-build', '--pull', 'never', '--force-recreate')
            ready(previous, admin)
            (ROOT / '.env').write_bytes((previous.parent / 'previous.env').read_bytes())
            save_state(state, activeConfig=str(previous), message='Previous release restored and healthy.')
        except Exception as rollback_error:
            raise RuntimeError(f'Deployment failed: {error}; rollback also failed: {rollback_error}') from rollback_error
        raise RuntimeError(f'Deployment failed; previous release restored: {error}') from error
    save_state(state, phase='ready', deployed=selected['commit'], deployedAt=now(),
        activeConfig=str(target), failedCommit=None, rollbackConfig=None,
        workflow=selected['workflow'], message='Release is ready to test at mini.local:25565.')
    # The next scheduled invocation uses the successful release's host scripts.
    # Running containers use immutable staged paths, so updating this clean checkout
    # cannot change their configuration or the previous rollback snapshot.
    try:
        if run(['git', 'status', '--porcelain']).strip():
            raise RuntimeError('Host checkout has local edits; leaving them intact.')
        run(['git', 'merge', '--ff-only', selected['commit']])
    except Exception as error:
        LOG.warning('Release is running, but host scripts were not updated: %s', error)
        save_state(state, message='Release is ready. Host scripts need attention: ' + str(error))
    return True


def check(retry=False):
    if PAUSED.exists():
        return
    with deployment_lock():
        state = read_state()
        # Never silently continue a transaction interrupted by a crash or shutdown.
        if state.get('phase') in ('restarting', 'checking', 'rolling-back', 'draining'):
            save_state(state, phase='interrupted', message='Deployment was interrupted. Inspect containers before retrying.')
        if state.get('phase') == 'interrupted' and not retry:
            return
        try:
            candidate = head()
            save_state(state, checkedAt=now(), target=candidate)
            if candidate == state.get('deployed') and not retry:
                return
            if candidate == state.get('failedCommit') and not retry:
                return
            selected = release(candidate)
        except ReleaseNotReady:
            save_state(state, phase='waiting-for-ci', message='Waiting for the complete GitHub release to succeed.')
            return
        except Exception as error:
            save_state(state, phase='waiting', message=str(error))
            return
        try:
            # Fail before any mutation when Docker Desktop is stopped.
            run(['docker', 'info', '--format', '{{.ServerVersion}}'], timeout=30)
        except Exception as error:
            save_state(state, phase='waiting-for-docker', message=str(error))
            return
        try:
            save_state(state, phase='preparing', message='Validating the release and downloading images.')
            target, previous = prepare(selected, state)
            deploy(selected, state, target, previous)
        except Exception as error:
            save_state(state, phase='failed', failedCommit=candidate, message=str(error))
            LOG.exception('Deployment stopped. This commit will not be retried automatically.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['check', 'status', 'pause', 'resume'])
    parser.add_argument('--retry', action='store_true', help='Explicitly retry the current main release after inspecting a failure')
    args = parser.parse_args()
    if args.command == 'status':
        print(json.dumps({**read_state(), 'paused': PAUSED.exists()}, indent=2))
        return
    if args.command == 'pause':
        PAUSED.touch()
        print('Automatic deployments paused. Any deployment already running will finish.')
        return
    if args.command == 'resume':
        PAUSED.unlink(missing_ok=True)
        print('Automatic deployments enabled; the next scheduled check will run them.')
        return
    log = ROOT / 'logs/auto-deploy.log'
    log.parent.mkdir(exist_ok=True)
    handler = RotatingFileHandler(log, maxBytes=2_000_000, backupCount=3)
    handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s %(message)s'))
    LOG.addHandler(handler)
    LOG.setLevel(logging.INFO)
    try:
        configure_docker()
        check(args.retry)
    except RuntimeError as error:
        LOG.warning('%s', error)  # Another manual/automatic deployment holds the shared lock.


if __name__ == '__main__':
    main()
