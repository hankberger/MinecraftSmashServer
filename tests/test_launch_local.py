from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import launch_local as launcher


class LauncherTests(unittest.TestCase):
    def test_occupied_port_rejects_before_building_or_touching_runtime(self):
        with tempfile.TemporaryDirectory() as tmp, socket.socket() as server:
            server.bind(('127.0.0.1', 0)); server.listen()
            runtime = Path(tmp) / 'not-created'
            args = ['launch_local.py', '--port', str(server.getsockname()[1]), '--runtime-dir', str(runtime)]
            with patch.object(sys, 'argv', args), patch.object(launcher, 'RUNTIME', runtime), \
                    patch.object(launcher, 'PORT', 25576), patch.object(launcher.subprocess, 'run') as build:
                with self.assertRaisesRegex(RuntimeError, 'already running'):
                    launcher.main()
                build.assert_not_called()
            self.assertFalse(runtime.exists())

    def test_second_process_cannot_enter_same_runtime(self):
        with tempfile.TemporaryDirectory() as tmp:
            runtime = Path(tmp)
            code = 'import sys; from pathlib import Path; from launch_local import launcher_lock\nwith launcher_lock(Path(sys.argv[1])): pass'
            with launcher.launcher_lock(runtime):
                result = subprocess.run([sys.executable, '-c', code, str(runtime)], cwd=launcher.ROOT,
                                        text=True, capture_output=True, timeout=10)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('already starting or running', result.stderr)
            with launcher.launcher_lock(runtime):
                pass

    def test_exception_releases_lock_for_next_launch(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, 'test failure'):
                with launcher.launcher_lock(Path(tmp)):
                    raise ValueError('test failure')
            with launcher.launcher_lock(Path(tmp)):
                pass

    def test_crashed_launcher_does_not_leave_a_stale_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            code = ('import os, sys; from pathlib import Path; from launch_local import launcher_lock\n'
                    'with launcher_lock(Path(sys.argv[1])): os._exit(17)')
            result = subprocess.run([sys.executable, '-c', code, tmp], cwd=launcher.ROOT, timeout=10)
            self.assertEqual(result.returncode, 17)
            with launcher.launcher_lock(Path(tmp)):
                pass


if __name__ == '__main__':
    unittest.main()
