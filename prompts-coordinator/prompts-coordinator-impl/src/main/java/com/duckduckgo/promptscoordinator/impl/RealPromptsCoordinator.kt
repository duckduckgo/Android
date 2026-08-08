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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.promptscoordinator.api.PromptsCoordinator
import com.duckduckgo.promptscoordinator.impl.di.PromptsCoordinatorStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPromptsCoordinator @Inject constructor(
    private val feature: PromptsCoordinatorFeature,
    @PromptsCoordinatorStore private val store: DataStore<Preferences>,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
) : PromptsCoordinator {

    /** Guards [owner] and the gap stamp. */
    private val claimMutex = Mutex()

    /**
     * The live claim. Null = surface free. Claims are never speculative — a claim is taken only when
     * a prompt is definitely about to show — so a busy surface is a final answer, never waited out.
     *
     * In-memory by design: a claim that outlived its process would have nobody left to release it.
     * The quiet gap is persisted separately, so a restart cannot skip it.
     */
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
                if (isNtpCardReclaim(type, currentOwner)) return@withLock true
                logcat { "PromptsCoordinator: $type claim refused, surface owned by $currentOwner" }
                return@withLock false
            }

            val sinceLastPrompt = currentTimeProvider.currentTimeMillis() - lastPromptDoneTimestamp()
            if (sinceLastPrompt < type.cooldownMillis) {
                logcat { "PromptsCoordinator: $type claim refused, gap not elapsed (${sinceLastPrompt}ms since last prompt)" }
                return@withLock false
            }

            owner = type
            logcat { "PromptsCoordinator: surface claimed by $type" }
            true
        }
    }

    /**
     * The RMF card re-renders on every NTP render, so a claim arriving while it already owns the
     * surface is the same card painting again. Modals are never re-claims, so two sheets can't stack.
     */
    private fun isNtpCardReclaim(type: PromptType, currentOwner: PromptType) =
        currentOwner == PromptType.NTP_CARD && type == PromptType.NTP_CARD

    override suspend fun onClaimDone(type: PromptType) = withContext(dispatchers.io()) {
        claimMutex.withLock {
            if (owner == type) {
                owner = null
                stampLastPromptDone()
                logcat { "PromptsCoordinator: $type claim done, gap timestamp stamped" }
            }
        }
    }

    override suspend fun onClaimCancelled(type: PromptType) = withContext(dispatchers.io()) {
        claimMutex.withLock {
            if (owner == type) {
                owner = null
                logcat { "PromptsCoordinator: $type claim cancelled, no stamp" }
            }
        }
    }

    private val PromptType.cooldownMillis: Long
        get() = (cooldownMinutes * TimeUnit.MINUTES.toMillis(1)).toLong()

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

        private const val UNINITIALIZED = -1L
        private const val NO_PROMPT = 0L
    }
}
