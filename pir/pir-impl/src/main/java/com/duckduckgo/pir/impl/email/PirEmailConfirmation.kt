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
import android.webkit.WebView
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.PirActionsRunner
import com.duckduckgo.pir.impl.common.PirJob
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.impl.common.PirWebViewDataCleaner
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.common.splitIntoParts
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.scripts.PirCssScriptLoader
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface PirEmailConfirmation {
    suspend fun executeForEmailConfirmationJobs(
        jobRecords: List<EmailConfirmationJobRecord>,
        context: Context,
        runType: RunType,
    ): Result<Unit>

    /**
     * This method should only be used if we want to debug email confirmation and pass in a specific [webView]
     * which will allow us to see the run in action.
     * Do not use this for non-debug scenarios.
     * You DO NOT need to set any dispatcher to call this suspend function. It is already set to run on IO.
     *
     * @param jobRecord - The email confirmation job record to run
     * @param webView - attached/visible WebView in which we will run the email confirmation flow.
     */
    suspend fun debugExecute(
        jobRecord: EmailConfirmationJobRecord,
        webView: WebView,
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
    private val brokerStepsParser: BrokerStepsParser,
    private val pirCssScriptLoader: PirCssScriptLoader,
    private val pirActionsRunnerFactory: RealPirActionsRunner.Factory,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewDataCleaner: PirWebViewDataCleaner,
    callbacks: PluginPoint<PirCallbacks>,
) : PirJob(callbacks),
    PirEmailConfirmation {
    private val runners: MutableList<PirActionsRunner> = mutableListOf()
    private var maxWebViewCount = 1

    override suspend fun executeForEmailConfirmationJobs(
        jobRecords: List<EmailConfirmationJobRecord>,
        context: Context,
        runType: RunType,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        if (jobRecords.isEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: Nothing to scan here." }
            return@withContext Result.success(Unit)
        }
        cleanPreviousRun()

        val processedJobRecords = processJobRecords(jobRecords)
        logcat { "PIR-EMAIL-CONFIRMATION: Total processed records ${processedJobRecords.size}" }

        if (processedJobRecords.isEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: No job records." }
            return@withContext Result.success(Unit)
        }

        val script = pirCssScriptLoader.getScript()
        maxWebViewCount = minOf(processedJobRecords.size, MAX_DETACHED_WEBVIEW_COUNT)

        logcat { "PIR-EMAIL-CONFIRMATION: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }
        // Initiate runners
        repeat(maxWebViewCount) {
            runners.add(pirActionsRunnerFactory.create(context, script, runType))
        }

        val jobRecordsParts = processedJobRecords.splitIntoParts(maxWebViewCount)

        logcat { "PIR-EMAIL-CONFIRMATION: Total parts ${jobRecordsParts.size}" }

        jobRecordsParts.mapIndexed { index, partSteps ->
            logcat { "PIR-EMAIL-CONFIRMATION:: Record part [$index] -> ${partSteps.size}" }
            logcat { "PIR-EMAIL-CONFIRMATION: Record part [$index] breakdown -> ${partSteps.map { it.first.id to it.second.broker.name }}" }
            // We want to run the runners in parallel but wait for everything to complete before we proceed
            async {
                partSteps.forEach { (profile, step) ->
                    logcat { "PIR-EMAIL-CONFIRMATION: Resuming opt-out on runner=$index for profile=$profile with step=$step" }
                    runners[index].start(profile, listOf(step))
                    runners[index].stop()
                    logcat { "PIR-EMAIL-CONFIRMATION: Finished resuming opt-out on runner=$index for profile=$profile with step=$step" }
                }
            }
        }.awaitAll()
        webViewDataCleaner.cleanWebViewData()
        return@withContext Result.success(Unit)
    }

    private suspend fun processJobRecords(jobRecords: List<EmailConfirmationJobRecord>): List<Pair<ProfileQuery, BrokerStep>> {
        val relevantBrokerJsons = jobRecords.mapTo(mutableSetOf()) { it.brokerName }.mapNotNull {
            val steps = repository.getBrokerOptOutSteps(it)
            if (!steps.isNullOrEmpty()) {
                it to steps
            } else {
                null
            }
        }.toMap()

        if (relevantBrokerJsons.isEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: No steps available." }
            return emptyList()
        }

        val relevantProfileIds = jobRecords.mapTo(mutableSetOf()) { it.userProfileId }
        // Multiple profile support (includes deprecated profiles as we need to process opt-out for them if there are extracted profiles)
        val relevantProfiles = obtainProfiles()
            .filter {
                it.id in relevantProfileIds
            }.associateBy { it.id }

        logcat { "PIR-EMAIL-CONFIRMATION: Relevant profileQueries $relevantProfiles" }

        if (relevantProfiles.isEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: Nothing to scan here. Can't map jobrecord profiles." }
            return emptyList()
        }

        val activeBrokers = repository.getAllActiveBrokerObjects().associateBy { it.name }
        if (activeBrokers.isEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: Nothing to scan here. No active brokers" }
            return emptyList()
        }

        return jobRecords.mapNotNull {
            val json = relevantBrokerJsons[it.brokerName] ?: return@mapNotNull null
            val broker = activeBrokers[it.brokerName] ?: return@mapNotNull null

            val brokerStep = brokerStepsParser.parseEmailConfirmationStep(
                broker,
                json,
                it,
            )
            val profileQuery = relevantProfiles[it.userProfileId]

            if (profileQuery != null && brokerStep != null) {
                profileQuery to brokerStep
            } else {
                null
            }
        }
    }

    private suspend fun obtainProfiles(): List<ProfileQuery> {
        return repository.getAllUserProfileQueries().ifEmpty {
            DEFAULT_PROFILE_QUERIES
        }
    }

    override suspend fun debugExecute(
        jobRecord: EmailConfirmationJobRecord,
        webView: WebView,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()
        cleanPreviousRun()

        logcat { "PIR-EMAIL-CONFIRMATION: Running debug email confirmation for ${jobRecord.brokerName} on ${Thread.currentThread().name}" }

        val activeBrokers = repository.getAllActiveBrokerObjects().associateBy { it.name }
        val broker = activeBrokers[jobRecord.brokerName]
        if (broker == null) {
            logcat { "PIR-EMAIL-CONFIRMATION: Broker ${jobRecord.brokerName} not found." }
            onJobCompleted()
            return@withContext Result.failure(IllegalStateException("Broker not found"))
        }

        val json = repository.getBrokerOptOutSteps(jobRecord.brokerName)
        if (json.isNullOrEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: No opt-out steps for ${jobRecord.brokerName}." }
            onJobCompleted()
            return@withContext Result.failure(IllegalStateException("No opt-out steps found"))
        }

        val brokerStep = brokerStepsParser.parseEmailConfirmationStep(broker, json, jobRecord)
        if (brokerStep == null) {
            logcat { "PIR-EMAIL-CONFIRMATION: Failed to parse email confirmation step." }
            onJobCompleted()
            return@withContext Result.failure(IllegalStateException("Failed to parse email confirmation step"))
        }

        val profileQuery = repository.getUserProfileQuery(jobRecord.userProfileId)
            ?: obtainProfiles().firstOrNull { it.id == jobRecord.userProfileId }
        if (profileQuery == null) {
            logcat { "PIR-EMAIL-CONFIRMATION: Profile query not found for ${jobRecord.userProfileId}." }
            onJobCompleted()
            return@withContext Result.failure(IllegalStateException("Profile query not found"))
        }

        runners.add(
            pirActionsRunnerFactory.create(
                webView.context,
                pirCssScriptLoader.getScript(),
                RunType.EMAIL_CONFIRMATION,
            ),
        )

        logcat { "PIR-EMAIL-CONFIRMATION: Starting debug execution on visible WebView" }
        runners[0].startOn(webView, profileQuery, listOf(brokerStep))
        runners[0].stop()
        logcat { "PIR-EMAIL-CONFIRMATION: Debug execution completed" }

        onJobCompleted()
        webViewDataCleaner.cleanWebViewData()
        return@withContext Result.success(Unit)
    }

    override fun stop() {
        logcat { "PIR-EMAIL-CONFIRMATION: Stopping all runners" }
        webViewDataCleaner.cleanWebViewData()
        runners.forEach {
            it.stop()
        }
        runners.clear()
    }

    private fun cleanPreviousRun() {
        if (runners.isNotEmpty()) {
            runners.forEach {
                it.stop()
            }
            runners.clear()
        }
    }
}
