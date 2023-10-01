#!/bin/bash
# TODO: replace with master branch after merge
curl -O- https://github.com/duckduckgo/sync-config/blob/lkruger/sync1/test/setup.sh
curl -O- https://github.com/duckduckgo/sync-config/blob/lkruger/sync1/test/sync_predefined_autofill.json
curl -O- https://github.com/duckduckgo/sync-config/blob/lkruger/sync1/test/sync_predefined_bookmarks.json
curl -O- https://github.com/duckduckgo/sync-config/blob/lkruger/sync1/test/teardown.sh

token="$(./setup.sh)"

# ... run the tests

./teardown.sh "$token"
