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

package com.duckduckgo.sync.impl.sendtodevices

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

// Sync has no push, so while the app is open we listen for ntfy pings from senders, with a slow poll as a fallback.
// ponytail: foreground only; delivery to a closed app still waits for the 3h background sync
@ContributesIntoSet(scope = AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class SendLinksForegroundPoller @Inject constructor(
    private val sendLinksSetting: SendLinksSetting,
    private val pinger: SendLinksPinger,
    private val syncStore: SyncStore,
    private val syncEngine: SyncEngine,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    private val pollJob = ConflatedJob()
    private val listenJob = ConflatedJob()

    private fun isActive() = syncStore.isSignedIn() && sendLinksSetting.isEnabled()

    override fun onStart(owner: LifecycleOwner) {
        pollJob += appCoroutineScope.launch(dispatchers.io()) {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (isActive()) syncEngine.triggerSync(FEATURE_READ)
            }
        }
        listenJob += appCoroutineScope.launch(dispatchers.io()) {
            while (isActive) {
                if (isActive()) {
                    pinger.listen {
                        logcat { "Sync-SendLinks: ping received" }
                        appCoroutineScope.launch(dispatchers.io()) { syncEngine.triggerSync(FEATURE_READ) }
                    }
                }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        pollJob.cancel()
        listenJob.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val RECONNECT_DELAY_MS = 5_000L
    }
}
