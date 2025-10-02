/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.email

import android.content.Context
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.PirActionsRunner
import com.duckduckgo.pir.impl.common.PirJob
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.scripts.PirCssScriptLoader
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

interface PirEmailConfirmation {
    suspend fun executeForEmailConfirmationJobs(
        jobRecords: List<EmailConfirmationJobRecord>,
        context: Context,
        runType: RunType,
    ): Result<Unit>

    fun stop()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PirEmailConfirmation::class,
)
@SingleInstanceIn(AppScope::class)
class RealPirEmailConfirmation @Inject constructor(
    private val repository: PirRepository,
    private val eventsRepository: PirEventsRepository,
    private val brokerStepsParser: BrokerStepsParser,
    private val pirCssScriptLoader: PirCssScriptLoader,
    private val pirActionsRunnerFactory: RealPirActionsRunner.Factory,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    callbacks: PluginPoint<PirCallbacks>,
) : PirJob(callbacks),
    PirEmailConfirmation {
    private val runners: MutableList<PirActionsRunner> = mutableListOf()
    private var maxWebViewCount = 1

    override suspend fun executeForEmailConfirmationJobs(
        jobRecords: List<EmailConfirmationJobRecord>,
        context: Context,
        runType: RunType,
    ): Result<Unit> = Result.success(Unit)

    override fun stop() {
        logcat { "PIR-SCAN: Stopping all runners" }
        runners.forEach {
            it.stop()
        }
        runners.clear()
        onJobStopped()
    }
}
