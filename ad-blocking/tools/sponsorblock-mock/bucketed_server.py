#!/usr/bin/env python3
"""Editable SponsorBlock mock preloaded with a prefix dataset."""
import argparse
import re
from http.server import ThreadingHTTPServer

import server


def load_dataset(path):
    prefixes = set()
    previous = None
    with open(path, encoding='ascii') as source:
        for line in source:
            value = line.strip()
            if not re.fullmatch(r'[0-9a-f]{8}', value) or (previous is not None and value <= previous):
                raise ValueError('Dataset must contain unique, sorted, lowercase 8-character hex prefixes')
            prefixes.add(value)
            previous = value
    return prefixes


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dataset', help='Sorted file of unique eight-character hex prefixes')
    parser.add_argument('--port', type=int, default=8766)
    parser.add_argument('--binary-device', action='store_true', help='Read the experimental binary file from the device')
    args = parser.parse_args()
    server.BINARY_DEVICE = args.binary_device
    prefixes = load_dataset(args.dataset)
    server.versions.clear()
    server.versions[1] = prefixes
    server.draft.update(prefixes)
    print(f'Bucketed mock: http://127.0.0.1:{args.port}/ (revision 1, {len(prefixes)} prefixes)', flush=True)
    ThreadingHTTPServer(('127.0.0.1', args.port), server.Handler).serve_forever()
