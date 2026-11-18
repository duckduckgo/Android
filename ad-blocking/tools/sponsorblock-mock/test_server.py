import unittest
import struct
import tempfile
import gzip
import json
import threading
from http.server import ThreadingHTTPServer
from subprocess import CompletedProcess
from unittest.mock import patch
from urllib.request import Request, urlopen
import server


class DeltaTest(unittest.TestCase):
    def setUp(self):
        server.versions.clear()
        server.replacement_revisions.clear()
        server.versions.update({1: {'0649a8bb'}, 2: {'cb1ad2a7'}})
        server.draft.clear()
        server.draft.update(server.versions[2])
        server.draft_labels.clear()
        server.requests.clear()

    def test_full_diff_and_up_to_date(self):
        self.assertEqual(server.delta(0), {'insert': ['cb1ad2a7'], 'delete': [], 'replace': True, 'revision': 2})
        self.assertEqual(server.delta(1), {'insert': ['cb1ad2a7'], 'delete': ['0649a8bb'], 'replace': False, 'revision': 2})
        self.assertEqual(server.delta(2), {'insert': [], 'delete': [], 'replace': False, 'revision': 2})

    def test_binary_full_and_diff(self):
        prefixes = {'0a13c480', '0a130329'}
        full = server.binary_full(prefixes, 3)
        self.assertEqual(struct.unpack_from('<4sBBHI4sIII', full)[:5], (b'SBPL', 1, 0, 0, 3))
        self.assertEqual(full[-4:], struct.pack('<HH', 0x0329, 0xc480))
        diff = server.binary_delta({'revision': 3, 'replace': False,
                                    'insert': list(prefixes), 'delete': ['0649a8bb']}, prefixes)
        self.assertEqual(diff[:6], b'SBPL\x01\x01')
        self.assertEqual(diff[12:16], full[12:16])
        self.assertEqual(diff[28:], struct.pack('<III', 0x0a130329, 0x0a13c480, 0x0649a8bb))

    def test_binary_preview_shows_actual_response_bytes_without_logging(self):
        handler = object.__new__(server.Handler)
        captured = []
        handler.send = lambda code, body, content_type: captured.append((code, body, content_type))
        handler.path = '/preview?revision=1'
        handler.do_GET()
        code, body, content_type = captured[0]
        expected = server.binary_delta(server.delta(1), server.versions[2])
        self.assertEqual(code, 200)
        self.assertEqual(content_type, 'text/plain; charset=utf-8')
        self.assertIn(b'Type: diff', body)
        self.assertIn(expected[:16].hex(' ').encode(), body)
        self.assertFalse(server.requests)
        full_preview = server.binary_preview(server.binary_full(server.versions[2], 2))
        self.assertIn('Type: full list', full_preview)
        self.assertIn('bytes omitted', full_preview)

    def test_old_diffs_and_fallback(self):
        server.versions.clear()
        server.versions.update({i: {str(i)} for i in range(1, 1201)})
        self.assertEqual(server.delta(700)['revision'], 1200)
        self.assertEqual(server.delta(699)['revision'], 1199)
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

    def test_draft_additions_are_labeled_and_removals_can_be_undone(self):
        server.update_draft({'video': ['https://youtu.be/mockVideo01']})
        self.assertEqual(server.draft_labels[server.prefix('mockVideo01')], 'mockVideo01')
        server.update_draft({'remove': ['cb1ad2a7']})
        server.update_draft({'undo_remove': ['cb1ad2a7']})
        self.assertIn('cb1ad2a7', server.draft)
        server.update_draft({'remove': [server.prefix('mockVideo01')]})
        self.assertEqual(server.draft, server.versions[2])
        self.assertNotIn(server.prefix('mockVideo01'), server.draft_labels)

    def test_dataset_pagination_and_new_revision(self):
        server.versions[2] = {f'{i:08x}' for i in range(205)}
        first = server.dataset_page(1)
        middle = server.dataset_page(2)
        last = server.dataset_page(999)
        self.assertEqual((first['page'], first['pages'], first['total']), (1, 3, 205))
        self.assertEqual(first['prefixes'][0], '00000000')
        self.assertEqual(middle['prefixes'][0], '00000064')
        self.assertEqual((last['page'], len(last['prefixes'])), (3, 5))
        server.versions[3] = {'ffffffff'}
        self.assertEqual(server.dataset_page(1)['prefixes'], ['ffffffff'])

    def test_remove_video_from_preloaded_dataset(self):
        server.versions[2].add('0649a8bb')
        server.draft.add('0649a8bb')
        server.update_draft({'remove_video': ['https://youtu.be/mockVideo01']})
        server.update_draft({'save': ['1'], 'revision': ['3']})
        self.assertEqual(server.delta(2)['delete'], ['0649a8bb'])

    def test_editable_http_server_serves_gzipped_binary_data(self):
        server.versions[2] = {f'{i:08x}' for i in range(1000)}
        server.draft.clear()
        server.draft.update(server.versions[2])
        http = ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        thread = threading.Thread(target=http.serve_forever, daemon=True)
        thread.start()
        try:
            base = f'http://127.0.0.1:{http.server_port}'
            with urlopen(base + '/sponsorblock/v1') as response:
                page = response.read()
                self.assertIn(b'Force device to request revision', page)
                self.assertIn(b'Update current revision', page)
                self.assertNotIn(b'Draft prefixes', page)
            with urlopen(base + '/dataset?page=2') as response:
                self.assertEqual(json.load(response)['prefixes'][0], '00000064')
            with urlopen(Request(base + server.BASE + '/hashPrefix?format=binary&version=1&revision=0', headers={'Accept-Encoding': 'gzip'})) as response:
                self.assertEqual(response.headers['Content-Encoding'], 'gzip')
                data = gzip.decompress(response.read())
                self.assertEqual(struct.unpack_from('<4sBBHI4sIII', data)[:5], (b'SBPL', 1, 0, 0, 2))
                self.assertEqual(struct.unpack_from('<I', data, 16)[0], 1000)
        finally:
            http.shutdown()
            http.server_close()
            thread.join()

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
        self.assertEqual(server.requests[-1]['response']['insert'], ['cb1ad2a7'])
        self.assertEqual(server.requests[-1]['response']['delete'], ['0649a8bb'])
        self.assertEqual({k: v for k, v in server.requests[-1].items() if k != 'time'}, {
            'path': handler.path,
            'status': 200,
            'response': dict(server.delta(1), checksum=server.binary_full(server.versions[2], 2)[12:16].hex(), entries=1, format='binary'),
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

    def test_reads_binary_device_dataset(self):
        data = server.binary_full({'0649a8bb', 'cb1ad2a7'}, 2)
        with patch.object(server.subprocess, 'run') as run:
            run.return_value = CompletedProcess([], 0, data, b'')
            self.assertEqual(server.device_dataset(limit=1), {'revision': 2, 'prefixes': ['0649a8bb'], 'count': 2})
            self.assertEqual(server.device_dataset()['prefixes'], ['0649a8bb', 'cb1ad2a7'])

    def test_missing_device_file_is_empty(self):
        with patch.object(server.subprocess, 'run') as run:
            run.return_value = CompletedProcess([], 1, b'', b'No such file or directory')
            self.assertEqual(server.device_dataset(), {'revision': None, 'prefixes': [], 'count': 0})

    def test_accepts_unsigned_revision_numbers(self):
        server.update_draft({'video': ['anotherVideo']})
        server.update_draft({'save': ['1'], 'revision': ['4294967295']})
        self.assertEqual(server.delta(0)['revision'], 4294967295)
        self.assertEqual(struct.unpack_from('<I', server.binary_full(server.versions[4294967295], 4294967295), 8)[0], 4294967295)

    def test_trigger_sync_uses_selected_device_and_receiver(self):
        with patch.dict(server.os.environ, {'SERIAL': 'test-device'}), patch.object(server.subprocess, 'run') as run:
            run.return_value = CompletedProcess([], 0, b'Broadcast completed', b'')
            server.trigger_sync()
            self.assertEqual(run.call_args.args[0], ['adb', '-s', 'test-device', 'shell', 'am', 'broadcast', '-n', server.RECEIVER])
            run.return_value = CompletedProcess([], 1, b'', b'device offline')
            with self.assertRaisesRegex(RuntimeError, 'device offline'):
                server.trigger_sync()


    def test_loads_sorted_prefixes(self):
        with tempfile.NamedTemporaryFile(mode='w', encoding='ascii') as dataset:
            dataset.write('00000001\n00000002\nffff0000\n')
            dataset.flush()
            self.assertEqual(server.load_dataset(dataset.name), {'00000001', '00000002', 'ffff0000'})

    def test_rejects_unsorted_or_invalid_input(self):
        for content in ('00000002\n00000001\n', '00000001\n00000001\n', 'invalid\n'):
            with self.subTest(content=content), tempfile.NamedTemporaryFile(mode='w') as dataset:
                dataset.write(content)
                dataset.flush()
                with self.assertRaises(ValueError):
                    server.load_dataset(dataset.name)


if __name__ == '__main__':
    unittest.main()
