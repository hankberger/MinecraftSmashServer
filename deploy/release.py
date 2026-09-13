"""Inspect or roll a successful GitHub release from the Docker host (including macOS).

Uses public GitHub/registry metadata; no GitHub token or inbound CI SSH is needed.
The default is the current main commit. An unfinished or failed build blocks deployment.
"""
import argparse
import json
import re
import urllib.parse
import urllib.request

from manage import Admin, deployment_lock, docker, _rollout

REPOSITORY = 'hankberger/MinecraftSmashServer'
REGISTRY = 'ghcr.io/' + REPOSITORY.lower()


def github(path):
    request = urllib.request.Request('https://api.github.com/repos/' + REPOSITORY + '/' + path,
        headers={'Accept': 'application/vnd.github+json', 'User-Agent': 'MinecraftSmashServer-deploy'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def release(commit=None):
    commit = commit or github('commits/main')['sha']
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Use the full 40-character commit SHA from GitHub.')
    query = urllib.parse.urlencode({'head_sha': commit, 'branch': 'main', 'event': 'push', 'status': 'success'})
    runs = github('actions/workflows/network.yml/runs?' + query)['workflow_runs']
    run = next((r for r in runs if r['head_sha'] == commit and r['head_branch'] == 'main'
        and r['event'] == 'push' and r['status'] == 'completed' and r['conclusion'] == 'success'), None)
    if run is None:
        raise RuntimeError('This commit has no successful main release. Wait for all build, publish and release jobs to pass.')
    result = {'commit': commit, 'workflow': run['html_url']}
    for service in ('backend', 'proxy'):
        repository = REGISTRY + '-' + service
        digest = json.loads(docker(['buildx', 'imagetools', 'inspect', repository + ':' + commit,
            '--format', '{{json .Manifest.Digest}}'], capture=True))
        if not re.fullmatch(r'sha256:[0-9a-f]{64}', digest):
            raise RuntimeError('Registry returned an invalid image digest.')
        result[service] = repository + '@' + digest
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['show', 'roll'])
    parser.add_argument('--commit', help='A specific successful main commit; defaults to current main')
    args = parser.parse_args()
    selected = release(args.commit)
    print(json.dumps(selected, indent=2), flush=True)
    if args.command == 'roll':
        admin = Admin()
        # Keep the existing deployment lock for the entire two-worker operation.
        with deployment_lock():
            for worker in ('arena-a', 'arena-b'):
                _rollout(admin, worker, selected['backend'])
        print('Both arenas updated. The lobby and proxy keep their current images; update those during maintenance.')


if __name__ == '__main__':
    main()
