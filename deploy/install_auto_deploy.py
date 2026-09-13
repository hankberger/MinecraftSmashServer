"""Install the current user's Mac launch agent. No sudo or GitHub token required."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LABEL = 'dev.hanks.smash.autodeploy'


def agent(python, root, interval=120):
    return {'Label': LABEL, 'ProgramArguments': [str(python), str(root / 'deploy/auto_deploy.py'), 'check'],
        'WorkingDirectory': str(root), 'RunAtLoad': True, 'StartInterval': interval,
        'EnvironmentVariables': {'PATH': '/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin',
                                 'PYTHONUNBUFFERED': '1'},
        'ProcessType': 'Background', 'ExitTimeOut': 900,
        'StandardOutPath': str(root / 'logs/auto-deploy-launchd.log'),
        'StandardErrorPath': str(root / 'logs/auto-deploy-launchd.log')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--interval', type=int, default=120, help='Seconds between checks (minimum 120 for public GitHub rate limits)')
    args = parser.parse_args()
    if sys.platform != 'darwin':
        parser.error('Run this installer on the Mac hosting the Docker network.')
    if args.interval < 120:
        parser.error('Use an interval of at least 120 seconds.')
    if (ROOT / 'deploy/.rollout-lock').exists():
        parser.error('A deployment is running or left a lock. Inspect it before changing the agent.')
    directory = Path.home() / 'Library/LaunchAgents'
    directory.mkdir(parents=True, exist_ok=True)
    (ROOT / 'logs').mkdir(exist_ok=True)
    python = next((p for p in (Path('/opt/homebrew/bin/python3'), Path('/usr/local/bin/python3')) if p.exists()), Path(sys.executable))
    path = directory / (LABEL + '.plist')
    # Use macOS's native serializer; some Homebrew Python builds have a pyexpat
    # linkage mismatch with the system XML library used by Python's plistlib.
    contents = subprocess.run(['plutil', '-convert', 'xml1', '-o', '-', '--', '-'],
        input=json.dumps(agent(python, ROOT, args.interval)).encode(),
        stdout=subprocess.PIPE, check=True).stdout
    domain = f'gui/{os.getuid()}'
    loaded = subprocess.run(['launchctl', 'print', domain + '/' + LABEL], capture_output=True).returncode == 0
    if loaded and path.exists() and path.read_bytes() == contents:
        print('Automatic deployment is already installed.')
        return
    if loaded:
        subprocess.run(['launchctl', 'bootout', domain + '/' + LABEL], check=True)
    path.write_bytes(contents)
    path.chmod(0o644)
    subprocess.run(['plutil', '-lint', str(path)], check=True)
    subprocess.run(['launchctl', 'bootstrap', domain, str(path)], check=True)
    print(f'Automatic deployment installed: checks every {args.interval}s and after login.')
    print('Status: python3 deploy/auto_deploy.py status')


if __name__ == '__main__':
    main()
