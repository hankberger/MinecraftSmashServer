"""Automatic deployment state transitions and rollback/admission behavior."""
from contextlib import nullcontext
from copy import deepcopy
import json
import os
from pathlib import Path, PurePosixPath
import tempfile
import unittest
from unittest.mock import patch, Mock

import auto_deploy as auto
from install_auto_deploy import agent
from release import ReleaseNotReady

SHA = 'a' * 40
OLD = 'b' * 40
SELECTED = {'commit': SHA, 'backend': 'backend@sha256:' + 'c' * 64,
            'proxy': 'proxy@sha256:' + 'd' * 64, 'workflow': 'https://github.com/example/run/1'}
HEALTHY = {'protocol': 1, 'ready': True, 'draining': [], 'nodes': {name: {'healthy': True,
    'status': {'ready': True, 'protocol': 1, 'tick': 100, 'draining': False}}
    for name in ('lobby', 'arena-a', 'arena-b')}}


class AutomaticTests(unittest.TestCase):
    def test_coordinated_protocol_upgrade_and_rollback(self):
        for version in (1, 2):
            status = deepcopy(HEALTHY)
            status['protocol'] = version
            for node in status['nodes'].values():
                node['status']['protocol'] = version
            self.assertTrue(auto.healthy(status))
            status['nodes']['arena-a']['status']['protocol'] = version + 1
            self.assertFalse(auto.healthy(status))
        status = deepcopy(HEALTHY)
        del status['protocol']
        self.assertFalse(auto.healthy(status))

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'deploy').mkdir()
        self.state = self.root / 'deploy/auto-state.json'
        self.paused = self.root / 'deploy/.auto-paused'
        for name, value in [('ROOT', self.root), ('STATE', self.state), ('PAUSED', self.paused)]:
            patcher = patch.object(auto, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        patcher = patch.object(auto, 'deployment_lock', return_value=nullcontext())
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_playing_worker_is_healthy_but_stalled_worker_is_not(self):
        status = deepcopy(HEALTHY)
        status['nodes']['arena-a']['status'].update(ready=False, phase='PLAYING')
        self.assertTrue(auto.healthy(status))
        status['nodes']['arena-a']['healthy'] = False
        self.assertFalse(auto.healthy(status))

    @patch.object(auto, 'head')
    def test_pause_skips_network_and_deployments(self, head):
        self.paused.touch()
        auto.check()
        head.assert_not_called()

    @patch.object(auto, 'release')
    @patch.object(auto, 'head', return_value=SHA)
    def test_deployed_commit_is_noop_without_github_api_calls(self, head, release):
        self.state.write_text(json.dumps({'deployed': SHA, 'phase': 'ready'}))
        auto.check()
        release.assert_not_called()

    @patch.object(auto, 'prepare')
    @patch.object(auto, 'release', side_effect=ReleaseNotReady('Pending'))
    @patch.object(auto, 'head', return_value=SHA)
    def test_failed_or_pending_ci_never_touches_containers(self, head, release, prepare):
        auto.check()
        prepare.assert_not_called()
        self.assertEqual('waiting-for-ci', json.loads(self.state.read_text())['phase'])

    @patch.object(auto, 'release')
    @patch.object(auto, 'head', return_value=SHA)
    def test_failed_deployment_is_not_repeated_on_each_timer(self, head, release):
        self.state.write_text(json.dumps({'failedCommit': SHA, 'phase': 'failed'}))
        auto.check()
        release.assert_not_called()

    @patch.object(auto, 'head')
    def test_interrupted_transaction_requires_operator_attention(self, head):
        self.state.write_text(json.dumps({'phase': 'checking'}))
        auto.check()
        head.assert_not_called()
        self.assertEqual('interrupted', json.loads(self.state.read_text())['phase'])

    def test_image_persistence_preserves_host_settings(self):
        (self.root / '.env').write_text('SMASH_PORT=25565\n# host setting\nPROXY_IMAGE=old\n')
        auto.write_images(SELECTED)
        contents = (self.root / '.env').read_text()
        self.assertIn('SMASH_PORT=25565\n# host setting\n', contents)
        self.assertNotIn('PROXY_IMAGE=old', contents)
        self.assertEqual(1, contents.count('PROXY_IMAGE='))
        for key, value in auto.image_env(SELECTED).items():
            self.assertIn(key + '=' + value, contents)

    def transaction_mocks(self):
        mocks = {}
        for name, value in [('Admin', Mock()), ('wait_for', HEALTHY), ('drain', None),
                            ('resume', None), ('head', SHA), ('compose', ''), ('ready', None),
                            ('write_images', None), ('run', '')]:
            patcher = patch.object(auto, name, return_value=value)
            mocks[name] = patcher.start()
            self.addCleanup(patcher.stop)
        return mocks

    def test_success_marks_exact_release_only_after_readiness(self):
        mocks = self.transaction_mocks()
        state = {'deployed': OLD, 'target': SHA}
        self.assertTrue(auto.deploy(SELECTED, state, self.root / 'new.json', self.root / 'old.json'))
        mocks['ready'].assert_called_once()
        self.assertEqual(SHA, state['deployed'])
        self.assertEqual('ready', state['phase'])
        self.assertIn('deployedAt', state)

    def test_failed_new_image_restores_previous_and_does_not_advance_commit(self):
        mocks = self.transaction_mocks()
        mocks['ready'].side_effect = [TimeoutError('New image failed'), None]
        (self.root / 'previous.env').write_text('PROXY_IMAGE=old\n')
        state = {'deployed': OLD, 'target': SHA}
        with self.assertRaisesRegex(RuntimeError, 'previous release restored'):
            auto.deploy(SELECTED, state, self.root / 'new.json', self.root / 'old.json')
        self.assertEqual(OLD, state['deployed'])
        self.assertEqual('PROXY_IMAGE=old\n', (self.root / '.env').read_text())
        mocks['write_images'].assert_not_called()
        self.assertEqual(2, mocks['ready'].call_count)
        self.assertEqual(str(self.root / 'old.json'), state['activeConfig'])

    def test_newer_push_while_draining_does_not_restart(self):
        mocks = self.transaction_mocks()
        mocks['head'].return_value = 'f' * 40
        state = {'deployed': OLD}
        self.assertFalse(auto.deploy(SELECTED, state, self.root / 'new.json', self.root / 'old.json'))
        mocks['compose'].assert_not_called()
        mocks['resume'].assert_called_once()
        self.assertEqual(OLD, state['deployed'])

    def test_drain_timeout_resumes_without_stopping_containers(self):
        mocks = self.transaction_mocks()
        mocks['drain'].side_effect = TimeoutError('Busy')
        with self.assertRaises(TimeoutError):
            auto.deploy(SELECTED, {}, self.root / 'new.json', self.root / 'old.json')
        mocks['compose'].assert_not_called()
        mocks['resume'].assert_called_once()

    def test_launch_agent_uses_arguments_and_private_local_logs(self):
        config = agent('/opt/homebrew/bin/python3', PurePosixPath('/Users/test/Smash Server'))
        self.assertEqual(120, config['StartInterval'])
        self.assertTrue(config['RunAtLoad'])
        self.assertEqual('/Users/test/Smash Server/deploy/auto_deploy.py', config['ProgramArguments'][1])
        self.assertEqual('check', config['ProgramArguments'][2])
        self.assertNotIn('KeepAlive', config)

    def test_background_docker_uses_public_auth_without_desktop_credentials(self):
        with patch.object(auto.sys, 'platform', 'darwin'), patch.dict(os.environ, {'DOCKER_CONTEXT': 'desktop-linux'}):
            auto.configure_docker()
            config = json.loads((self.root / 'build/auto-docker/config.json').read_text())
            self.assertEqual({'ghcr.io': {}}, config['auths'])
            self.assertNotIn('credsStore', config)
            self.assertNotIn('DOCKER_CONTEXT', os.environ)
            self.assertEqual(str(self.root / 'build/auto-docker'), os.environ['DOCKER_CONFIG'])


if __name__ == '__main__':
    unittest.main()
