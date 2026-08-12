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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * Guards [owner] and the gap stamp. Never held across the wait in [tryClaim], so a claimant
     * waiting for the surface cannot block the current owner from releasing it.
     */
    private val claimMutex = Mutex()

    /**
     * The live claim. Null = surface free. Observable so a waiting [tryClaim] is woken by the release
     * rather than polling.
     *
     * In-memory by design: a claim that outlived its process would have nobody left to release it.
     * The quiet gap is persisted separately, so a restart cannot skip it.
     */
    private val owner = MutableStateFlow<PromptType?>(null)

    private val lastPromptAt = AtomicLong(UNINITIALIZED)

    override suspend fun isEnabled(): Boolean = withContext(dispatchers.io()) {
        feature.self().isEnabled()
    }

    override suspend fun tryClaim(type: PromptType): Boolean = withContext(dispatchers.io()) {
        if (!feature.self().isEnabled()) return@withContext true

        when (val immediate = attemptClaim(type)) {
            is ClaimOutcome.Settled -> return@withContext immediate.granted
            ClaimOutcome.SurfaceBusy -> Unit
        }

        // A claim that never becomes a prompt is released within milliseconds, so a short wait turns
        // most collisions into a granted claim. Only the wait is bounded, never the check-and-set: a
        // timeout cancelling a granted claim would leak the surface.
        val surfaceAvailable = withTimeoutOrNull(CLAIM_WAIT_TIMEOUT_MILLIS) {
            // Available to *this* type, not strictly free: owner conflates, so a card waiting only
            // for null can miss a release that another card claims in the same breath.
            owner.first { it == null || isNtpCardReclaim(type, it) }
            true
        } ?: false

        if (!surfaceAvailable) {
            logcat {
                "PromptsCoordinator: $type claim refused, surface still owned by " +
                    "${owner.value} after ${CLAIM_WAIT_TIMEOUT_MILLIS}ms"
            }
            return@withContext false
        }

        when (val afterWait = attemptClaim(type)) {
            is ClaimOutcome.Settled -> afterWait.granted
            ClaimOutcome.SurfaceBusy -> {
                logcat { "PromptsCoordinator: $type claim refused, surface reclaimed by ${owner.value} while waiting" }
                false
            }
        }
    }

    /** A busy surface is reported back so [tryClaim] can wait it out; the gap cannot clear in that window. */
    private suspend fun attemptClaim(type: PromptType): ClaimOutcome = claimMutex.withLock {
        val now = currentTimeProvider.currentTimeMillis()

        val currentOwner = owner.value
        if (currentOwner != null) {
            if (isNtpCardReclaim(type, currentOwner)) return@withLock ClaimOutcome.Settled(granted = true)
            return@withLock ClaimOutcome.SurfaceBusy
        }

        val sinceLastPrompt = now - lastPromptDoneTimestamp()
        if (sinceLastPrompt < type.cooldownMillis) {
            logcat { "PromptsCoordinator: $type claim refused, gap not elapsed (${sinceLastPrompt}ms since last prompt)" }
            return@withLock ClaimOutcome.Settled(granted = false)
        }

        owner.value = type
        logcat { "PromptsCoordinator: surface claimed by $type" }
        ClaimOutcome.Settled(granted = true)
    }

    /**
     * The RMF card re-renders on every NTP render, so a claim arriving while it already owns the
     * surface is the same card painting again. Modals are never re-claims, so two sheets can't stack.
     */
    private fun isNtpCardReclaim(type: PromptType, currentOwner: PromptType) =
        currentOwner == PromptType.NTP_CARD && type == PromptType.NTP_CARD

    private sealed interface ClaimOutcome {
        data class Settled(val granted: Boolean) : ClaimOutcome

        /** Held by another prompt, so the claim is still worth retrying once the surface frees up. */
        data object SurfaceBusy : ClaimOutcome
    }

    override suspend fun onClaimDone(type: PromptType) = withContext(dispatchers.io()) {
        claimMutex.withLock {
            if (owner.value == type) {
                owner.value = null
                stampLastPromptDone()
                logcat { "PromptsCoordinator: $type claim done, gap timestamp stamped" }
            }
        }
    }

    override suspend fun onClaimCancelled(type: PromptType) = withContext(dispatchers.io()) {
        claimMutex.withLock {
            if (owner.value == type) {
                owner.value = null
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

        private const val CLAIM_WAIT_TIMEOUT_MILLIS = 1_000L
    }
}
