# SponsorBlock local mock

The mock serves the proposed prefix dataset API. It keeps revision history in memory. On startup it initializes the current revision and prefixes from the connected device when available. It binds to localhost and needs Python 3; no packages are required.

1. Build and install the internal debug app with the mock endpoint enabled (JDK 21):

   ```sh
   ./gradlew :app:installInternalDebug -PsponsorBlockMock=true
   ```

   This build sends SponsorBlock dataset requests to `http://127.0.0.1:8765`; segment requests still use the normal endpoint. Enable SponsorBlock in the app.

2. Start the server and set up `adb reverse`:

   ```sh
   ad-blocking/tools/sponsorblock-mock/run.sh
   ```

   Pass `--dataset /path/to/prefixes_unique.txt` to preload prefixes, and optionally `--port <port>` to change the server port. The script sets up the corresponding `adb reverse`. Set `SERIAL=<device-id>` when multiple devices are connected, or `NO_DEVICE=1` to run the server without adb. Ctrl-C stops it and removes the reverse.

3. Open the [SponsorBlock mock server](http://127.0.0.1:8765/) on your computer.

## Use the web tool

1. **Edit the draft:** paste a YouTube video URL or ID and select **Add video**. Use **Remove** to remove a prefix. The draft and current dataset display hash prefixes.
2. **Save a revision:** choose a number greater than the current revision and select **Save revision**. Check **Force clients to replace their dataset** to send the full list to clients updating across this revision. The controls are disabled until the draft changes. If the device is ahead, the page suggests the device revision plus one.
3. **Preview a response:** under **Preview response**, enter a client revision and select **Show response**. The field starts with the device's revision when available. Revisions that were never saved fall back to the full dataset.
4. **Sync the device:** select **Force device to request revision**. Sync runs asynchronously and requires the SponsorBlock feature flag to be enabled.
5. **Inspect the device:** the **Device storage** section shows its stored revision and prefixes, refreshing approximately every second while the page is open. It shows an error until the app creates the file. Select **Refresh** to read it immediately. If a sync is writing at the same time, the snapshot can temporarily fail or appear stale.
6. **Inspect requests:** the **Phone requests** column automatically shows the latest 100 dataset requests with local timestamps. Expand a request to see the response; preview requests from the page are excluded. Requests to the normal segment endpoint do not appear here.

To test a sync requiring two diff requests, select **Set up two-request sync** under **Test scenarios**, then select **Force device to request revision**. The helper reads the device's saved dataset and creates two newer revisions so that one sync requires two requests. It replaces the mock's revision history; save or remove any draft changes first. Watch the phone requests and device storage to follow the sync.

The web tool uses the device selected by `SERIAL` and the `com.duckduckgo.mobile.android.debug` application ID. Start the app and enable SponsorBlock before requesting a sync. Watch the server log for `/revision` and `/hashPrefix` requests. Periodic syncs follow the app schedule; Android may delay them.

After syncing, videos whose prefixes are in the dataset can request segments from the normal endpoint; other videos should not request segments. Restart the app before rechecking a video already looked up, because segment results are cached in the app process.

Restarting the server clears its revision history. When the device storage is available, the server starts from its current revision and prefixes; otherwise it starts with an empty dataset at revision 1.

The mock returns a diff to the latest revision for clients up to 500 revisions behind. For revisions 501 to 1000 behind, it returns the last available intermediate diff; clients more than 1000 behind receive the full list, as they would through nginx. Missing revisions also receive the full list. The app requests binary responses and verifies the checksum of the resulting dataset.

Run the protocol checks with `python3 -m unittest discover -s ad-blocking/tools/sponsorblock-mock`.

## Preload a dataset

Run the editable mock preloaded with a sorted file of unique, lowercase eight-character hex prefixes:

```sh
python3 ad-blocking/tools/sponsorblock-mock/server.py --dataset /path/to/prefixes_unique.txt --port 8766
```

It loads the file as revision 1 and opens the same editing and device controls at [http://127.0.0.1:8766/](http://127.0.0.1:8766/). To direct the mock-enabled app to this server, run:

```sh
adb reverse tcp:8765 tcp:8766
```

Use `adb -s <device-id> reverse tcp:8765 tcp:8766` and set `SERIAL=<device-id>` when working with multiple devices. The server starts from the file even if a device has a newer revision; clear app data or save a newer revision before syncing. Restarting reloads revision 1 and discards edits. The page paginates prefixes; remove any prefix by entering its value or video URL/ID. Large phone responses are summarized in the request log; the response preview shows the header and first 256 bytes. API responses support gzip.

The experimental `/hashPrefix?revision=0&format=binary&version=1` response uses the `SBPL` version 1 format. Full lists contain a little-endian offset table and sorted two-byte remainders; diffs contain sorted four-byte insert and delete lists. Both include the resulting revision, entry count and checksum. The app validates the response, memory-maps the full list, and atomically replaces the old file after a successful update. Lookups can continue against the previous file while an update is prepared. Revisions use the full unsigned 32-bit range. The current Android implementation limits files to less than 2 GiB because each file is mapped into one buffer. The format is experimental and is not a production API.
