"""Exercise remote shell commands locally; the fake ECS CLI deliberately returns 0 on remote failure."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'ecs-verification.sh'


class EcsVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        directory = Path(self.temp.name)
        self.write(directory / 'aws', '''#!/usr/bin/env python3
import os, shlex, subprocess, sys
args = sys.argv[1:]
if 'list-tasks' in args:
    print(os.environ.get('TEST_TASK', 'arn:test:task'))
elif 'describe-tasks' in args:
    print('arn:test:revision')
else:
    command = args[args.index('--command') + 1]
    subprocess.run(shlex.split(command), check=False)
    sys.exit(int(os.environ.get('TEST_CLI_STATUS', '0')))
''')
        self.write(directory / 'timeout', '#!/bin/sh\nshift 2\nexec "$@"\n')
        self.write(directory / 'java', '#!/bin/sh\n[ "${TEST_REMOTE_STATUS:-0}" = 0 ] && printf "GATEWAY_BOOTSTRAP_OK\\n"\n')
        self.write(directory / 'wget', '''#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
if any(a.startswith('--post-data=') for a in args):
    body = json.loads(next(a.split('=',1)[1] for a in args if a.startswith('--post-data=')))
    assert body['max_tokens'] == 16
    assert '--header=X-API-Key: ' + os.environ['GATEWAY_API_KEY'] in args
else:
    assert '--spider' in args
sys.exit(int(os.environ.get('TEST_REMOTE_STATUS', '0')))
''')
        self.env = dict(os.environ, PATH=str(directory) + os.pathsep + os.environ['PATH'],
                        ECS_CLUSTER='test', ECS_SERVICE='test', SMOKE_ATTEMPTS='1',
                        GATEWAY_API_KEY='test-fixture-key')

    @staticmethod
    def write(path, text):
        path.write_text(text)
        path.chmod(0o700)

    def run_mode(self, mode, **overrides):
        result = subprocess.run(['bash', str(SCRIPT), mode], env=dict(self.env, **overrides),
                                capture_output=True, text=True, timeout=10)
        self.assertNotIn(self.env['GATEWAY_API_KEY'], result.stdout + result.stderr)
        return result

    def test_each_remote_command_succeeds_with_explicit_marker(self):
        for mode in ['health', 'bootstrap', 'chat']:
            with self.subTest(mode=mode):
                result = self.run_mode(mode)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn('_OK', result.stdout)

    def test_remote_failure_is_not_hidden_by_successful_cli_exit(self):
        for mode in ['health', 'bootstrap', 'chat']:
            with self.subTest(mode=mode):
                self.assertNotEqual(self.run_mode(mode, TEST_REMOTE_STATUS='1').returncode, 0)

    def test_cli_failure_is_not_hidden_by_remote_success(self):
        self.assertNotEqual(self.run_mode('health', TEST_CLI_STATUS='1').returncode, 0)

    def test_no_running_task_fails(self):
        self.assertNotEqual(self.run_mode('health', TEST_TASK='None').returncode, 0)

    def test_wrong_task_revision_fails(self):
        self.assertNotEqual(self.run_mode('health', EXPECTED_TASK_DEFINITION='arn:wrong').returncode, 0)

    def test_expected_task_revision_succeeds(self):
        self.assertEqual(self.run_mode('health', EXPECTED_TASK_DEFINITION='arn:test:revision').returncode, 0)

    def test_retry_count_is_bounded(self):
        self.assertNotEqual(self.run_mode('health', SMOKE_ATTEMPTS='13').returncode, 0)


if __name__ == '__main__':
    unittest.main()
