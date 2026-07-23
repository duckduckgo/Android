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

package com.duckduckgo.promptscoordinator.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.promptscoordinator.api.PromptsCoordinator
import com.duckduckgo.promptscoordinator.impl.di.PromptsCoordinatorStore
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPromptsCoordinator @Inject constructor(
    private val feature: PromptsCoordinatorFeature,
    private val remoteMessageModel: RemoteMessageModel,
    @PromptsCoordinatorStore private val store: DataStore<Preferences>,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PromptsCoordinator {

    /**
     * Guards [owner] and the [lastPromptAt] stamp. Held only for the check-and-set inside each
     * call (microseconds); the prompt-lifetime state is [owner], so a losing [tryClaim] returns
     * false immediately instead of waiting for the surface to free up.
     */
    private val claimMutex = Mutex()

    /** The live claim; in-memory only, resets on process death. Null = surface free. */
    private var owner: PromptType? = null

    private val lastPromptAt = AtomicLong(UNINITIALIZED)

    override suspend fun isEnabled(): Boolean = withContext(dispatchers.io()) {
        feature.self().isEnabled()
    }

    override suspend fun tryClaim(type: PromptType): Boolean = withContext(dispatchers.io()) {
        if (!feature.self().isEnabled()) return@withContext true

        claimMutex.withLock {
            val currentOwner = owner
            if (currentOwner != null) {
                // The RMF card is a single persistent surface that re-renders on every NTP render;
                // a re-claim while it already owns the surface is the same card painting again,
                // not a competing prompt. Modal re-claims stay refused so two sheets can't stack.
                val isRmfReclaim = currentOwner == PromptType.RMF && type == PromptType.RMF
                if (!isRmfReclaim) {
                    logcat { "PromptsCoordinator: $type claim refused, surface owned by $currentOwner" }
                }
                return@withLock isRmfReclaim
            }

            if (type != PromptType.RMF && isRemoteMessageActive()) {
                logcat { "PromptsCoordinator: $type claim refused, an RMF message is active" }
                return@withLock false
            }

            val sinceLastPrompt = currentTimeProvider.currentTimeMillis() - lastPromptDoneTimestamp()
            if (sinceLastPrompt < gapFor(type)) {
                logcat { "PromptsCoordinator: $type claim refused, gap not elapsed (${sinceLastPrompt}ms since last prompt)" }
                return@withLock false
            }

            owner = type
            logcat { "PromptsCoordinator: surface claimed by $type" }
            true
        }
    }

    override fun onClaimDone(type: PromptType) {
        appCoroutineScope.launch(dispatchers.io()) {
            claimMutex.withLock {
                if (owner == type) {
                    owner = null
                    stampLastPromptDone()
                    logcat { "PromptsCoordinator: $type claim done, gap timestamp stamped" }
                }
            }
        }
    }

    override fun onClaimCancelled(type: PromptType) {
        appCoroutineScope.launch(dispatchers.io()) {
            claimMutex.withLock {
                if (owner == type) {
                    owner = null
                    logcat { "PromptsCoordinator: $type claim cancelled, no stamp" }
                }
            }
        }
    }

    /**
     * "RMF active" means a message eligible to render on the New Tab Page ([Surface.NEW_TAB_PAGE]).
     * MODAL-surface-only messages are excluded: they are shown *through* the Modal Coordinator, so
     * counting them here would block the very claim they need.
     */
    private fun isRemoteMessageActive(): Boolean =
        remoteMessageModel.getActiveMessage()?.surfaces?.contains(Surface.NEW_TAB_PAGE) == true

    private fun gapFor(type: PromptType): Long = when (type) {
        PromptType.MODAL -> MODAL_GAP_MILLIS
        PromptType.RMF -> RMF_GAP_MILLIS
        // TBD (tech design §8): reserved; claims conservatively with the modal gap for now.
        PromptType.OTHER -> MODAL_GAP_MILLIS
    }

    private suspend fun lastPromptDoneTimestamp(): Long {
        val cached = lastPromptAt.get()
        if (cached != UNINITIALIZED) return cached
        val persisted = store.data.firstOrNull()?.get(LAST_PROMPT_AT_KEY) ?: NO_PROMPT
        return if (lastPromptAt.compareAndSet(UNINITIALIZED, persisted)) persisted else lastPromptAt.get()
    }

    private suspend fun stampLastPromptDone() {
        val now = currentTimeProvider.currentTimeMillis()
        lastPromptAt.set(now)
        store.edit { it[LAST_PROMPT_AT_KEY] = now }
    }

    companion object {
        private val LAST_PROMPT_AT_KEY = longPreferencesKey("last_prompt_done_timestamp")

        private const val MODAL_GAP_MILLIS = 24 * 60 * 60 * 1000L
        private const val RMF_GAP_MILLIS = 10 * 60 * 1000L

        private const val UNINITIALIZED = -1L
        private const val NO_PROMPT = 0L
    }
}
