"""Deployment admission checks: never roll a pending/failed or unrelated release."""
import unittest
from unittest.mock import patch

import release

SHA = 'a' * 40
DIGEST = 'sha256:' + 'b' * 64
RUN = {'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'status': 'completed',
       'conclusion': 'success', 'html_url': 'https://github.com/' + release.REPOSITORY + '/actions/runs/1'}


class ReleaseTests(unittest.TestCase):
    @patch('release.docker', return_value='"' + DIGEST + '"')
    @patch('release.github', side_effect=[{'sha': SHA}, {'workflow_runs': [RUN]}])
    def test_resolves_successful_main_to_immutable_images(self, github, docker):
        result = release.release()
        self.assertEqual(SHA, result['commit'])
        self.assertEqual(release.REGISTRY + '-backend@' + DIGEST, result['backend'])
        self.assertEqual(release.REGISTRY + '-proxy@' + DIGEST, result['proxy'])
        self.assertIn('head_sha=' + SHA, github.call_args.args[0])

    @patch('release.docker')
    @patch('release.github', return_value={'workflow_runs': []})
    def test_unreleased_commit_stops_before_registry_or_rollout(self, github, docker):
        with self.assertRaisesRegex(RuntimeError, 'no successful main release'):
            release.release(SHA)
        docker.assert_not_called()

    @patch('release.docker')
    @patch('release.github')
    def test_rejects_incomplete_failed_or_unrelated_runs(self, github, docker):
        for field, value in [('head_sha', 'c' * 40), ('head_branch', 'feature'),
                             ('event', 'pull_request'), ('status', 'in_progress'), ('conclusion', 'failure')]:
            with self.subTest(field=field):
                github.return_value = {'workflow_runs': [{**RUN, field: value}]}
                with self.assertRaises(RuntimeError):
                    release.release(SHA)
        docker.assert_not_called()

    @patch('release.github')
    @patch('release.docker')
    def test_rejects_invalid_commit_before_external_calls(self, docker, github):
        with self.assertRaises(ValueError):
            release.release('main; touch unsafe')
        github.assert_not_called()
        docker.assert_not_called()

    @patch('release.docker', return_value='"invalid-digest"')
    @patch('release.github', return_value={'workflow_runs': [RUN]})
    def test_rejects_invalid_digest(self, github, docker):
        with self.assertRaisesRegex(RuntimeError, 'invalid image digest'):
            release.release(SHA)

    @patch('release._rollout', side_effect=RuntimeError('Replacement and rollback failed'))
    @patch('release.deployment_lock')
    @patch('release.Admin')
    @patch('release.release', return_value={'backend': release.REGISTRY + '-backend@' + DIGEST})
    def test_failed_first_arena_does_not_replace_second(self, selected, admin, lock, rollout):
        with patch('sys.argv', ['release.py', 'roll']):
            with self.assertRaises(RuntimeError):
                release.main()
        self.assertEqual(1, rollout.call_count)
        self.assertEqual('arena-a', rollout.call_args.args[1])


if __name__ == '__main__':
    unittest.main()
