import tempfile
import unittest

import bucketed_server


class BucketedServerTest(unittest.TestCase):
    def test_loads_sorted_prefixes(self):
        with tempfile.NamedTemporaryFile(mode='w', encoding='ascii') as dataset:
            dataset.write('00000001\n00000002\nffff0000\n')
            dataset.flush()
            self.assertEqual(bucketed_server.load_dataset(dataset.name), {'00000001', '00000002', 'ffff0000'})

    def test_rejects_unsorted_or_invalid_input(self):
        for content in ('00000002\n00000001\n', '00000001\n00000001\n', 'invalid\n'):
            with self.subTest(content=content), tempfile.NamedTemporaryFile(mode='w') as dataset:
                dataset.write(content)
                dataset.flush()
                with self.assertRaises(ValueError):
                    bucketed_server.load_dataset(dataset.name)
