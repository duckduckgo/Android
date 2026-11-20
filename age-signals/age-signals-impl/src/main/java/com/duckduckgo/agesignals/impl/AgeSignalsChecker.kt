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

package com.duckduckgo.agesignals.impl

import android.app.Activity
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Failed
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotApplicable
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.di.scopes.AppScope
import com.google.android.gms.tasks.Task
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.ApplicableAgeFeaturesRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

interface AgeSignalsChecker {
    /**
     * Runs the gated Play Age Signals flow and records its outcome. Concurrent calls run one after another.
     *
     * @return the outcome this run recorded, or null when it recorded nothing.
     */
    suspend fun check(currentActivity: () -> Activity?): AgeSignalsOutcome?
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealAgeSignalsChecker @Inject constructor(
    private val managerProvider: AgeSignalsManagerProvider,
    private val store: AgeSignalsStore,
) : AgeSignalsChecker {

    private val mutex = Mutex()

    override suspend fun check(currentActivity: () -> Activity?): AgeSignalsOutcome? = mutex.withLock {
        val outcome = runFlow(managerProvider.get(), currentActivity)
        if (outcome == null) {
            logcat(VERBOSE) { "Age signals: no live Activity to request access, nothing recorded" }
        } else {
            logcat(VERBOSE) { "Age signals outcome: $outcome" }
            store.record(outcome)
        }
        outcome
    }

    private suspend fun runFlow(manager: AgeSignalsManager, currentActivity: () -> Activity?): AgeSignalsOutcome? {
        var step = Step.APPLICABLE_FEATURES
        return try {
            val features = manager.getApplicableAgeFeatures(ApplicableAgeFeaturesRequest.builder().build()).awaitOrTimeout()
            if (!AgeSignalsParser.isSharingMandatory(features)) return NotApplicable

            step = Step.REQUEST_ACCESS
            val activity = currentActivity()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return null
            val accessRequest = AgeSignalsAccessRequest.builder().setActivity(activity).build()
            val access = AgeSignalsParser.parseAccess(manager.requestAgeSignalsAccess(accessRequest).awaitOrTimeout())
            if (access != Shared) return access

            step = Step.CHECK_SIGNALS
            manager.checkAgeSignals(AgeSignalsRequest.builder().build()).awaitOrTimeout()
            Shared
        } catch (e: AgeSignalsException) {
            AgeSignalsParser.parseFailure(step, e)
        } catch (e: TimeoutCancellationException) {
            Failed(step, AgeSignalsError.TIMEOUT)
        }
    }

    private suspend fun <T> Task<T>.awaitOrTimeout(): T = withTimeout(PLAY_CALL_TIMEOUT) { await() }

    companion object {
        val PLAY_CALL_TIMEOUT = 60.seconds
    }
}
