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

3. Open the [SponsorBlock mock server](http://127.0.0.1:8765/) on your computer.

## Use the web tool

1. **Edit the draft:** paste a YouTube video URL or ID and select **Add video**. Use **Remove** to remove a prefix. The draft and current dataset display hash prefixes.
2. **Save a revision:** choose a number greater than the current revision and select **Save revision**. Check **Force clients to replace their dataset** to send the full list to clients updating across this revision. The controls are disabled until the draft changes. If the device is ahead, the page suggests the device revision plus one.
3. **Preview a response:** under **Preview response**, enter a client revision and select **Show response**. The field starts with the device's revision when available. Revisions that were never saved fall back to the full dataset.
4. **Sync the device:** select **Force device to request revision**. Sync runs asynchronously and requires the SponsorBlock feature flag to be enabled.
5. **Inspect the device:** the **Device database** section shows its stored revision and prefixes, refreshing approximately every second while the page is open. It shows an error until the app creates the database. Select **Refresh** to read it immediately. If a sync is writing at the same time, the snapshot can temporarily fail or appear stale.
6. **Inspect requests:** the **Phone requests** column automatically shows the latest 100 dataset requests with local timestamps. Expand a request to see the response; preview requests from the page are excluded. Requests to the normal segment endpoint do not appear here.

The web tool uses the device selected by `SERIAL` and the `com.duckduckgo.mobile.android.debug` application ID. Start the app and enable SponsorBlock before requesting a sync. Watch the server log for `/revision` and `/hashPrefix` requests. Mock builds also schedule periodic syncs approximately every 15 minutes; Android may delay them.

After syncing, videos whose prefixes are in the dataset can request segments from the normal endpoint; other videos should not request segments. Restart the app before rechecking a video already looked up, because segment results are cached in the app process.

Restarting the server clears its revision history. When the device database is available, the server starts from its current revision and prefixes; otherwise it starts with an empty dataset at revision 1.

The mock implements full lists, diffs, current revisions, and the 500/1000 revision retention behavior. It returns the full list directly for expired revisions, as nginx would. Responses are uncompressed JSON and omit checksums.

Run the protocol checks with `python3 -m unittest discover -s ad-blocking/tools/sponsorblock-mock`.
