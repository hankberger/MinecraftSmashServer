"""Headless network startup check for CI; needs no Minecraft client."""
import argparse
import json
from pathlib import Path
import urllib.error
import urllib.request
from manage import Admin, wait_for
from status_ping import check_versions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--timeout', type=int, default=240)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    admin = Admin()
    initial = wait_for(admin, lambda s: s.get('ready') and len(s.get('nodes', {})) == 3
        and all(n['healthy'] and n['status']['ready'] for n in s['nodes'].values()), args.timeout, 'Network startup timed out')
    check_versions()
    try:
        urllib.request.urlopen(admin.url + '/status', timeout=5)
        raise AssertionError('Admin accepted unauthenticated request')
    except urllib.error.HTTPError as error:
        assert error.code == 401
    for worker in ('arena-a', 'arena-b'):
        admin.call('/drain', {'node': worker})
        admin.call('/drain', {'node': worker})
        drained = wait_for(admin, lambda s: worker in s.get('draining', []) and s['nodes'][worker]['status']['drained'], 20, 'Drain failed')
        assert not drained['nodes'][worker]['status']['ready']
        admin.call('/resume', {'node': worker})
        wait_for(admin, lambda s: worker not in s.get('draining', []) and s['nodes'][worker]['status']['ready'], 20, 'Resume failed')
    for worker in ('arena-a', 'arena-b', 'lobby'):
        assert admin.call()['nodes'][worker]['status']['tick'] > initial['nodes'][worker]['status']['tick']
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(admin.call(), indent=2))
    print('NETWORK_CHECK_PASSED: all backends ticking, authenticated API, drain/resume acknowledged.')


if __name__ == '__main__':
    main()
