"""CI entry point. Secrets stay in environment/files; commands are shell-quoted."""
import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile


def main():
    def required(name):
        value = os.environ.get(name, '')
        if not value:
            raise ValueError('Set the production secret ' + name)
        return value

    host = required('SMASH_DEPLOY_HOST')
    user = required('SMASH_DEPLOY_USER')
    path = required('SMASH_DEPLOY_PATH')
    image = required('SMASH_RELEASE_IMAGE')
    if not re.fullmatch(r'[a-zA-Z0-9.-]+', host) or not re.fullmatch(r'[a-zA-Z0-9_-]+', user):
        raise ValueError('Host must be a DNS name/IPv4 address; invalid host or SSH username')
    if not path.startswith('/') or '\n' in path:
        raise ValueError('Deployment path must be an absolute Unix path')
    if not re.fullmatch(r'ghcr\.io/[a-z0-9_./-]+@sha256:[0-9a-f]{64}', image):
        raise ValueError('Use a GHCR image with its immutable SHA-256 digest')
    with tempfile.TemporaryDirectory(prefix='smash-deploy-') as directory:
        key = Path(directory) / 'key'
        known = Path(directory) / 'known_hosts'
        key.write_text(required('SMASH_SSH_KEY').rstrip() + '\n'); key.chmod(0o600)
        known.write_text(required('SMASH_KNOWN_HOSTS').rstrip() + '\n')
        # Noninteractive macOS SSH sessions omit Docker Desktop and Homebrew.
        command = 'export PATH=/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin; cd ' + shlex.quote(path) + ' && ' + ' && '.join(
            shlex.join(['python3', 'deploy/manage.py', 'roll', worker, '--image', image])
            for worker in ('arena-a', 'arena-b'))
        subprocess.run(['ssh', '-i', str(key), '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
            '-o', 'UserKnownHostsFile=' + str(known), '-o', 'ConnectTimeout=15', user + '@' + host, command], check=True)


if __name__ == '__main__':
    main()
