"""Exercise an actual failed image rollout on the empty, isolated test network."""
import json
from pathlib import Path
import subprocess
from manage import Admin, ROOT, rollout, wait_for


def main():
    admin = Admin()
    before = admin.call()
    assert not before['matches'] and all(not n['status'].get('players') for n in before['nodes'].values()), 'Test network must be empty'
    # No game process starts and no volume data is modified by this bad image.
    subprocess.run(['docker', 'build', '-t', 'smash-backend:rollback-test', '-'],
        input='FROM smash-backend:dev\nENTRYPOINT ["/bin/sh", "-c", "exit 42"]\n', text=True, check=True, cwd=ROOT)
    try:
        rollout(admin, 'arena-a', 'smash-backend:rollback-test', 'smash-network-test', ['deploy/compose.smoke.yaml'],
                timeout=30, startup_timeout=40, local_image=True, persist=False)
        raise AssertionError('Broken image unexpectedly became ready')
    except TimeoutError as error:
        assert str(error) == 'Replacement failed readiness.', 'Rollback itself failed: ' + str(error)
    after = wait_for(admin, lambda s: s['nodes']['arena-a']['healthy'] and s['nodes']['arena-a']['status']['ready'] and 'arena-a' not in s['draining'], 20, 'Rollback did not resume worker')
    assert after['nodes']['arena-a']['status']['boot'] != before['nodes']['arena-a']['status']['boot']
    assert after['nodes']['arena-b']['status']['boot'] == before['nodes']['arena-b']['status']['boot']
    path = ROOT / 'evidence/network-0.3.0/rollback.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({'passed': True, 'before': before, 'after': after}, indent=2))
    print('ROLLBACK_SMOKE_PASSED: broken image rejected, prior image restored, arena B untouched.')


if __name__ == '__main__':
    main()
