"""Exercise full-network rollback with real containers in the disposable CI stack."""
import argparse
from copy import deepcopy
import json
from pathlib import Path
import tempfile
from unittest.mock import patch

import auto_deploy as auto
from manage import Admin, ROOT, wait_for


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    args = parser.parse_args()
    if args.project != 'smash-ci':
        parser.error('This destructive regression check only runs in the disposable smash-ci project.')
    admin = Admin()
    before = wait_for(admin, auto.healthy, 60, 'CI network not healthy')
    if any(n['status']['players'] for n in before['nodes'].values()):
        raise RuntimeError('CI network must contain no players.')
    with tempfile.TemporaryDirectory(prefix='smash-auto-check-') as temporary:
        root = Path(temporary)
        (root / 'deploy').mkdir()
        (root / '.env').write_text('SMASH_PORT=25577\n')
        config = json.loads(auto.run(['docker', 'compose', '-p', 'smash-ci', '-f', 'compose.yaml',
            '-f', 'deploy/compose.ci.yaml', 'config', '--format', 'json']))
        current = root / 'current.json'
        current.write_text(json.dumps(config))
        with patch.multiple(auto, ROOT=root, STATE=root / 'deploy/auto-state.json', PROJECT='smash-ci'):
            previous, old = auto.snapshot(current, root)
            broken = deepcopy(old)
            broken['services']['proxy']['entrypoint'] = ['sh', '-c', 'exit 42']
            broken['services']['proxy']['restart'] = 'no'
            target = root / 'broken.json'
            target.write_text(json.dumps(broken))
            state = {'deployed': 'previous-release', 'target': 'candidate'}
            with patch.object(auto, 'Admin', return_value=admin), patch.object(auto, 'head', return_value='candidate'):
                try:
                    auto.deploy({'commit': 'candidate'}, state, target, previous)
                    raise AssertionError('An exiting proxy was accepted as a healthy deployment')
                except RuntimeError as error:
                    assert 'previous release restored' in str(error), str(error)
            assert state['deployed'] == 'previous-release'
            after = wait_for(admin, auto.healthy, 30, 'Rollback did not restore health')
            for service in auto.SERVICES:
                container = auto.compose(previous, 'ps', '-q', service).strip()
                actual = json.loads(auto.run(['docker', 'inspect', container]))[0]
                assert actual['Image'] == old['services'][service]['image'], service + ' changed image during rollback'
            assert (root / '.env').read_text() == 'SMASH_PORT=25577\n'
            assert all(after['nodes'][node]['status']['boot'] != before['nodes'][node]['status']['boot'] for node in after['nodes'])
    (ROOT / 'build/auto-deploy-check.json').write_text(json.dumps({'passed': True, 'checks': [
        'An exiting proxy failed deployment readiness', 'Previous images restored on all four containers',
        'All backend boot IDs changed and game ticks resumed', 'Deployed commit and host settings preserved']}, indent=2))
    print('AUTO_DEPLOY_CHECK_PASSED: failed release restored all four previous containers and healthy game ticks.')


if __name__ == '__main__':
    main()
