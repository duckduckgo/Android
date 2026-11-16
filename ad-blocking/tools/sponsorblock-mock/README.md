# SponsorBlock local mock

The mock serves the proposed prefix dataset API. It keeps revision history in memory. On startup it initializes the current revision and prefixes from the connected device when available. It binds to localhost and needs Python 3; no packages are required.

1. Build and install the internal debug app with the mock endpoint enabled (JDK 21):

   ```sh
   mise exec java@temurin-21 -- ./gradlew :app:installInternalDebug -PsponsorBlockMock=true
   ```

   The command uses JDK 21 through mise. Install it first with `mise install java@temurin-21` if needed.

   This build sends SponsorBlock dataset requests to `http://127.0.0.1:8765`; segment requests still use the normal endpoint. Enable SponsorBlock in the app.

2. Start the server and set up `adb reverse`:

   ```sh
   ad-blocking/tools/sponsorblock-mock/run.sh
   ```

   Set `SERIAL=<device-id>` when multiple devices are connected, or `NO_DEVICE=1` to run the server without adb. Ctrl-C stops it and removes the reverse.

3. Open the [SponsorBlock mock server](http://127.0.0.1:8765/) on your computer. Add YouTube video URLs or IDs to the draft, or remove prefixes from it, choose a new revision number, then select **Save revision**. The new number must be greater than the current one. If the device is ahead, the page suggests the device revision plus one. The page shows the draft and current dataset as hash prefixes, and can preview the response for any client revision. Revisions that were never saved fall back to the full dataset. Once synced, listed videos can request segments from the normal endpoint; unlisted videos should not request segments.

Select **Force device to request revision** under **Device database** to request a sync and refresh the displayed device data. The page also reads device data on load; **Refresh** reads it again on demand. Both actions use `adb` and the same `SERIAL` as the server. The sync runs asynchronously and still requires the SponsorBlock feature flag. Refresh reads a snapshot without stopping the app; if a sync is writing at the same time, refresh again if the snapshot fails or appears stale.

To sync a new revision immediately, run:

```sh
adb shell am broadcast -n com.duckduckgo.mobile.android.debug/com.duckduckgo.adblocking.impl.sponsorblock.SponsorBlockMockSyncReceiver
```

Use the application ID of your installed internal debug build if it differs. The receiver is present only in debug builds and triggers work only when the mock endpoint is enabled. The worker still checks the SponsorBlock feature flag. Watch the server log for `/revision` and `/hashPrefix` requests. Restart the app before rechecking a video already looked up: segment results are cached in the app process. Mock builds schedule periodic syncs approximately every 15 minutes; Android may delay them.

To print the revision stored on the device, use `sqlite3` on your computer:

```sh
(
  set -e
  package=com.duckduckgo.mobile.android.debug
  db_dir=$(mktemp -d)
  trap 'rm -rf "$db_dir"' EXIT
  adb shell am force-stop "$package"
  adb exec-out run-as "$package" cat databases/sponsor_block_dataset.db > "$db_dir/dataset.db"
  if adb shell run-as "$package" test -f databases/sponsor_block_dataset.db-wal; then
    adb exec-out run-as "$package" cat databases/sponsor_block_dataset.db-wal > "$db_dir/dataset.db-wal"
  fi
  sqlite3 "$db_dir/dataset.db" 'SELECT revision FROM sponsor_revision WHERE id = 0;'
)
```

This stops the app to copy a consistent snapshot. Open it again to resume testing. An empty result means no revision has been saved yet; the database file is created when the app first accesses it.

Restarting the server clears its revision history. When the device database is available, the server starts from its current revision and prefixes; otherwise it starts with an empty dataset at revision 1.

The mock implements full lists, diffs, current revisions, and the 500/1000 revision retention behavior. It returns the full list directly for expired revisions, as nginx would. Responses are uncompressed JSON and omit checksums.

Run the protocol checks with `python3 -m unittest discover -s ad-blocking/tools/sponsorblock-mock`.
