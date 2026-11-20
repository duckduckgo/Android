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
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotApplicable
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.di.scopes.AppScope
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.ApplicableAgeFeaturesRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

internal interface AgeSignalsChecker {
    /** Runs the gated Play Age Signals flow and records its outcome. Concurrent calls run one after another. */
    suspend fun check(activity: Activity)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class RealAgeSignalsChecker @Inject constructor(
    private val managerProvider: AgeSignalsManagerProvider,
    private val store: AgeSignalsStore,
) : AgeSignalsChecker {

    private val mutex = Mutex()

    override suspend fun check(activity: Activity) {
        mutex.withLock {
            val outcome = runFlow(managerProvider.get(), activity)
            logcat(VERBOSE) { "Age signals outcome: $outcome" }
            store.record(outcome)
        }
    }

    private suspend fun runFlow(manager: AgeSignalsManager, activity: Activity): AgeSignalsOutcome {
        var step = Step.APPLICABLE_FEATURES
        return try {
            val features = manager.getApplicableAgeFeatures(ApplicableAgeFeaturesRequest.builder().build()).await()
            if (!AgeSignalsParser.isSharingMandatory(features)) return NotApplicable

            step = Step.REQUEST_ACCESS
            val accessRequest = AgeSignalsAccessRequest.builder().setActivity(activity).build()
            val access = AgeSignalsParser.parseAccess(manager.requestAgeSignalsAccess(accessRequest).await())
            if (access != Shared) return access

            step = Step.CHECK_SIGNALS
            manager.checkAgeSignals(AgeSignalsRequest.builder().build()).await()
            Shared
        } catch (e: AgeSignalsException) {
            AgeSignalsParser.parseFailure(step, e)
        }
    }
}
