import unittest
import sqlite3
import tempfile
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch
import server


class DeltaTest(unittest.TestCase):
    def setUp(self):
        server.versions.clear()
        server.replacement_revisions.clear()
        server.versions.update({1: {'0649a8bb'}, 2: {'cb1ad2a7'}})
        server.draft.clear()
        server.draft.update(server.versions[2])
        server.requests.clear()

    def test_full_diff_and_up_to_date(self):
        self.assertEqual(server.delta(0), {'insert': ['cb1ad2a7'], 'delete': [], 'replace': True, 'revision': 2, 'checksum': '43f72de2'})
        self.assertEqual(server.delta(1), {'insert': ['cb1ad2a7'], 'delete': ['0649a8bb'], 'replace': False, 'revision': 2, 'checksum': '43f72de2'})
        self.assertEqual(server.delta(2), {'insert': [], 'delete': [], 'replace': False, 'revision': 2, 'checksum': '43f72de2'})

    def test_checksum_matches_sample_and_empty_dataset(self):
        self.assertEqual(server.checksum({'cb1ad2a7', '0a13c480', '0a130329', '2fee55bc'}), 'e2ab92ec')
        self.assertEqual(server.checksum(set()), 'e3b0c442')

    def test_old_diffs_and_fallback(self):
        server.versions.clear()
        server.versions.update({i: {str(i)} for i in range(1, 1201)})
        self.assertEqual(server.delta(700)['revision'], 1200)
        self.assertEqual(server.delta(699)['revision'], 1199)
        self.assertEqual(server.delta(699)['checksum'], server.checksum({'1199'}))
        self.assertTrue(server.delta(199)['replace'])

    def test_video_urls_and_ids(self):
        for value in ('mockVideo01', 'https://www.youtube.com/watch?v=mockVideo01&t=10',
                      'https://youtu.be/mockVideo01?t=10', 'https://youtube.com/shorts/mockVideo01'):
            self.assertEqual(server.video_id(value), 'mockVideo01')
        for value in ('https://example.com/watch?v=mockVideo01', 'https://youtube.com/watch', 'bad id'):
            with self.assertRaises(ValueError):
                server.video_id(value)

    def test_draft_changes_only_take_effect_when_saved(self):
        server.update_draft({'video': ['https://youtu.be/mockVideo01']})
        server.update_draft({'video': ['anotherVideo']})
        server.update_draft({'remove': ['cb1ad2a7']})
        self.assertEqual(server.versions[2], {'cb1ad2a7'})
        self.assertEqual(len(server.versions), 2)
        server.update_draft({'save': ['1'], 'revision': ['4']})
        self.assertEqual(server.versions[4], {'0649a8bb', server.prefix('anotherVideo')})
        self.assertEqual(server.delta(2)['revision'], 4)
        self.assertTrue(server.delta(3)['replace'])
        with self.assertRaises(ValueError):
            server.update_draft({'save': ['1'], 'revision': ['4']})
        self.assertEqual(len(server.versions), 3)

    def test_sparse_revision_gap_falls_back_to_full_list(self):
        server.update_draft({'video': ['anotherVideo']})
        server.update_draft({'save': ['1'], 'revision': ['1000']})
        self.assertEqual(server.delta(2)['revision'], 1000)
        self.assertTrue(server.delta(2)['replace'])

    def test_cannot_save_unchanged_draft(self):
        with self.assertRaisesRegex(ValueError, 'no draft changes'):
            server.update_draft({'save': ['1'], 'revision': ['3']})

    def test_force_replace_applies_to_clients_crossing_revision(self):
        server.update_draft({'video': ['anotherVideo']})
        server.update_draft({'save': ['1'], 'revision': ['3'], 'replace': ['1']})
        server.update_draft({'remove': ['cb1ad2a7']})
        server.update_draft({'save': ['1'], 'revision': ['4']})
        self.assertEqual(server.delta(2), {
            'insert': [server.prefix('anotherVideo')], 'delete': [], 'replace': True, 'revision': 4,
            'checksum': server.checksum({server.prefix('anotherVideo')}),
        })
        self.assertFalse(server.delta(3)['replace'])
        self.assertFalse(server.delta(4)['replace'])

    def test_request_log_excludes_preview_and_contains_responses(self):
        handler = object.__new__(server.Handler)
        handler.json = lambda value: None
        handler.send = lambda code, body, content_type: None
        handler.path = '/preview?revision=1'
        handler.do_GET()
        self.assertFalse(server.requests)
        handler.path = server.BASE + '/hashPrefix?revision=1'
        handler.do_GET()
        self.assertEqual({k: v for k, v in server.requests[-1].items() if k != 'time'}, {
            'path': handler.path,
            'status': 200,
            'response': server.delta(1),
        })
        self.assertIsNotNone(server.datetime.fromisoformat(server.requests[-1]['time']).tzinfo)
        handler.path = server.BASE + '/revision'
        handler.do_GET()
        self.assertEqual(server.requests[-1]['response'], {'revision': 2})
        handler.path = server.BASE + '/hashPrefix?revision=bad'
        handler.do_GET()
        self.assertEqual(server.requests[-1]['status'], 400)

    def test_initialize_dataset_from_device(self):
        server.initialize_from_device({'revision': 4, 'prefixes': ['0649a8bb', 'cb1ad2a7']})
        self.assertEqual(server.versions, {4: {'0649a8bb', 'cb1ad2a7'}})
        self.assertEqual(server.draft, server.versions[4])
        server.update_draft({'video': ['anotherVideo']})
        server.update_draft({'save': ['1'], 'revision': ['5']})
        self.assertEqual(server.delta(4)['insert'], [server.prefix('anotherVideo')])

    def test_two_step_scenario_starts_from_device_dataset(self):
        server.setup_two_step_scenario({'revision': 2, 'prefixes': ['cb1ad2a7']})
        first = server.delta(2)
        second = server.delta(first['revision'])
        self.assertEqual(first['revision'], 502)
        self.assertEqual(second['revision'], 503)
        self.assertFalse(first['replace'])
        self.assertFalse(second['replace'])
        self.assertEqual(len(first['insert']), 1)
        self.assertEqual(len(second['insert']), 1)
        self.assertEqual(server.draft, server.versions[503])

    def test_two_step_scenario_preserves_unsaved_draft(self):
        server.update_draft({'video': ['anotherVideo']})
        before = server.draft.copy()
        with self.assertRaisesRegex(ValueError, 'draft changes'):
            server.setup_two_step_scenario({'revision': 2, 'prefixes': ['cb1ad2a7']})
        self.assertEqual(server.draft, before)
        self.assertEqual(max(server.versions), 2)

    def test_reads_device_revision_and_prefixes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'source.db'
            with sqlite3.connect(path) as connection:
                connection.execute('CREATE TABLE sponsor_revision (id INTEGER, revision INTEGER)')
                connection.execute('CREATE TABLE sponsor_prefixes (prefix TEXT)')
                connection.execute('INSERT INTO sponsor_revision VALUES (0, 2)')
                connection.executemany('INSERT INTO sponsor_prefixes VALUES (?)', [('cb1ad2a7',), ('0649a8bb',)])
            with patch.dict(server.os.environ, {'SERIAL': 'test-device'}), patch.object(server.subprocess, 'run') as run:
                run.side_effect = [CompletedProcess([], 0, path.read_bytes(), b''), CompletedProcess([], 1, b'', b'')]
                self.assertEqual(server.device_dataset(), {'revision': 2, 'prefixes': ['0649a8bb', 'cb1ad2a7']})
                self.assertEqual(run.call_args_list[0].args[0][:3], ['adb', '-s', 'test-device'])

    def test_trigger_sync_uses_selected_device_and_receiver(self):
        with patch.dict(server.os.environ, {'SERIAL': 'test-device'}), patch.object(server.subprocess, 'run') as run:
            run.return_value = CompletedProcess([], 0, b'Broadcast completed', b'')
            server.trigger_sync()
            self.assertEqual(run.call_args.args[0], ['adb', '-s', 'test-device', 'shell', 'am', 'broadcast', '-n', server.RECEIVER])
            run.return_value = CompletedProcess([], 1, b'', b'device offline')
            with self.assertRaisesRegex(RuntimeError, 'device offline'):
                server.trigger_sync()


if __name__ == '__main__':
    unittest.main()
