/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.mockenvironment

import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.duckduckgo.app.surrogates.api.ResourceSurrogateListDownloader
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.trackerdetection.api.TrackerDataDownloader
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.tracker.detection.api.TrackerDetector
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * [seed] drives the app's own downloads (answered from the snapshot by [MockEnvironmentApiInterceptor]) and may retry freely because the
 * seeding process is not measured. [verify] only reads, so the measured process never writes. Both log ready only once the sentinel host,
 * which exists only in the pinned TDS, is blocked by the live tracker detector.
 */
@SingleIn(AppScope::class)
class MockEnvironmentReadiness @Inject constructor(
    private val snapshot: MockEnvironmentSnapshot,
    private val store: MockEnvironmentStore,
    private val dispatchers: DispatcherProvider,
    private val trackerDataDownloader: TrackerDataDownloader,
    private val trackerDataLoader: TrackerDataLoader,
    private val tdsMetadataDao: TdsMetadataDao,
    private val surrogateListDownloader: ResourceSurrogateListDownloader,
    private val trackerDetector: TrackerDetector,
) {

    private val mutex = Mutex()
    private var seeded = false
    private var verified = false

    suspend fun seed() = mutex.withLock {
        if (seeded) return@withLock
        val reason = applyProxy() ?: awaitPinnedTrackers(download = true)
        if (reason != null) {
            Log.e(TAG, "mockEnvironment=failed reason=$reason pid=${Process.myPid()}")
            error("Mock environment could not be seeded: $reason")
        }
        seeded = true
        Log.i(TAG, "mockEnvironment=seeded ${stateSummary()} pid=${Process.myPid()}")
    }

    suspend fun verify() = mutex.withLock {
        if (seeded || verified) return@withLock
        val reason = applyProxy() ?: awaitPinnedTrackers(download = false)
        if (reason != null) {
            Log.e(TAG, "mockEnvironment=failed reason=$reason pid=${Process.myPid()}")
            return@withLock
        }
        verified = true
        Log.i(TAG, "mockEnvironment=ready ${stateSummary()} pid=${Process.myPid()}")
    }

    /**
     * Returns null once the proxy is in effect, otherwise the reason it is not. Never reports ready without it.
     */
    private suspend fun applyProxy(): String? {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return "proxy_unsupported"
        return if (runCatching { setProxy("$PROXY_HOST:${store.fixturePort}") }.isSuccess) null else "proxy_failed"
    }

    private suspend fun setProxy(hostAndPort: String) = suspendCancellableCoroutine { continuation ->
        // One rule for every scheme and no direct fallback, so a dead proxy fails requests instead of reaching the internet.
        // Loopback stays implicitly bypassed, which is how the fixture page itself is still loaded directly.
        val config = ProxyConfig.Builder().addProxyRule(hostAndPort).build()
        ProxyController.getInstance().setProxyOverride(config, { it.run() }) { continuation.resume(Unit) }
    }

    /**
     * Tracker data is loaded asynchronously at process start, so right after launch the live tracker client may still be the bundled one.
     * Polls until the in-memory client is the pinned one (the sentinel host is BLOCKED) and the database etag matches the snapshot.
     * With [download], missing data is fetched (served from the snapshot); without it, a pinned database with a stale client is only
     * reloaded in memory, never written. Gives up after [TIMEOUT_MS] and returns the last failure reason, or null once pinned.
     */
    private suspend fun awaitPinnedTrackers(download: Boolean): String? = withContext(dispatchers.io()) {
        var reason: String? = "not checked"
        val completed = withTimeoutOrNull(TIMEOUT_MS) {
            while (true) {
                reason = unpinnedReason()
                if (reason == null) break
                if (download) runCatching { downloadSnapshot() }
                delay(POLL_INTERVAL_MS)
            }
            true
        }
        if (completed == true) null else reason
    }

    private fun downloadSnapshot() {
        if (tdsMetadataDao.eTag() != snapshot.tdsEtag) trackerDataDownloader.downloadTds().blockingAwait()
        surrogateListDownloader.downloadList().blockingAwait()
    }

    private fun unpinnedReason(): String? {
        if (tdsMetadataDao.eTag() != snapshot.tdsEtag) return "tds etag"
        if (isSentinelBlocked()) return null
        trackerDataLoader.loadTrackers()
        return if (isSentinelBlocked()) null else "sentinel not blocked"
    }

    private fun isSentinelBlocked(): Boolean {
        val event = trackerDetector.evaluate(Uri.parse("http://$SENTINEL_HOST/script-0.js"), Uri.parse(DOCUMENT_URL), true, emptyMap())
        return event?.status == TrackerStatus.BLOCKED && event.surrogateId == null
    }

    private fun stateSummary(): String {
        return "tds=${snapshot.tdsEtag} proxy=$PROXY_HOST:${store.fixturePort}"
    }

    private companion object {
        const val TAG = "DdgTestSeeder"

        /**
         * Never resolves, and exists only in the pinned TDS (not in the bundled one), so it proves the pinned client is the live one.
         */
        const val SENTINEL_HOST = "mock-environment-sentinel.invalid"

        /**
         * Not allow-listed in any scenario, so protections-off runs are proven against the same input as the others.
         */
        const val DOCUMENT_URL = "http://mock-environment-readiness.test/"
        const val PROXY_HOST = "127.0.0.1"
        const val TIMEOUT_MS = 45_000L
        const val POLL_INTERVAL_MS = 500L
    }
}
