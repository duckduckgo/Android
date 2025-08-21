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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerManualScanCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerManualScanStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutActionFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScheduledScanCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScheduledScanStarted
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_STARTED
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.impl.store.db.PirBrokerScanLog
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface PirRunStateHandler {
    suspend fun handleState(pirRunState: PirRunState)

    sealed class PirRunState(open val brokerName: String) {
        data class BrokerManualScanStarted(
            override val brokerName: String,
            val eventTimeInMillis: Long,
        ) : PirRunState(brokerName)

        data class BrokerManualScanCompleted(
            override val brokerName: String,
            val profileQueryId: Long,
            val startTimeInMillis: Long,
            val eventTimeInMillis: Long,
            val totalTimeMillis: Long,
            val isSuccess: Boolean,
        ) : PirRunState(brokerName)

        data class BrokerScheduledScanStarted(
            override val brokerName: String,
            val eventTimeInMillis: Long,
        ) : PirRunState(brokerName)

        data class BrokerScheduledScanCompleted(
            override val brokerName: String,
            val profileQueryId: Long,
            val startTimeInMillis: Long,
            val eventTimeInMillis: Long,
            val totalTimeMillis: Long,
            val isSuccess: Boolean,
        ) : PirRunState(brokerName)

        data class BrokerScanActionSucceeded(
            override val brokerName: String,
            val profileQueryId: Long,
            val pirSuccessResponse: PirSuccessResponse,
        ) : PirRunState(brokerName)

        data class BrokerScanActionFailed(
            override val brokerName: String,
            val actionType: String,
            val actionID: String,
            val message: String,
        ) : PirRunState(brokerName)

        data class BrokerRecordOptOutStarted(
            override val brokerName: String,
            val extractedProfile: ExtractedProfile,
        ) : PirRunState(brokerName)

        data class BrokerRecordOptOutCompleted(
            override val brokerName: String,
            val extractedProfile: ExtractedProfile,
            val startTimeInMillis: Long,
            val endTimeInMillis: Long,
            val isSubmitSuccess: Boolean,
        ) : PirRunState(brokerName)

        data class BrokerOptOutActionSucceeded(
            override val brokerName: String,
            val extractedProfile: ExtractedProfile,
            val completionTimeInMillis: Long,
            val actionType: String,
            val result: PirSuccessResponse,
        ) : PirRunState(brokerName)

        data class BrokerOptOutActionFailed(
            override val brokerName: String,
            val extractedProfile: ExtractedProfile,
            val completionTimeInMillis: Long,
            val actionType: String,
            val actionID: String,
            val message: String,
        ) : PirRunState(brokerName)
    }
}

@ContributesBinding(AppScope::class)
class RealPirRunStateHandler @Inject constructor(
    private val repository: PirRepository,
    private val eventsRepository: PirEventsRepository,
    private val pixelSender: PirPixelSender,
    private val dispatcherProvider: DispatcherProvider,
    private val jobRecordUpdater: JobRecordUpdater,
) : PirRunStateHandler {
    private val moshi: Moshi by lazy {
        Moshi.Builder().add(
            PolymorphicJsonAdapterFactory.of(PirSuccessResponse::class.java, "actionType")
                .withSubtype(NavigateResponse::class.java, "navigate")
                .withSubtype(ExtractedResponse::class.java, "extract")
                .withSubtype(GetCaptchaInfoResponse::class.java, "getCaptchaInfo")
                .withSubtype(SolveCaptchaResponse::class.java, "solveCaptcha")
                .withSubtype(ClickResponse::class.java, "click")
                .withSubtype(ExpectationResponse::class.java, "expectation")
                .withSubtype(FillFormResponse::class.java, "fillForm"),
        ).add(KotlinJsonAdapterFactory())
            .build()
    }

    private val pirSuccessAdapter by lazy { moshi.adapter(PirSuccessResponse::class.java) }

    override suspend fun handleState(pirRunState: PirRunState) = withContext(dispatcherProvider.io()) {
        when (pirRunState) {
            is BrokerManualScanStarted -> handleBrokerManualScanStarted(pirRunState)
            is BrokerManualScanCompleted -> handleBrokerManualScanCompleted(pirRunState)
            is BrokerScheduledScanStarted -> handleBrokerScheduledScanStarted(pirRunState)
            is BrokerScheduledScanCompleted -> handleBrokerScheduledScanCompleted(pirRunState)
            is BrokerScanActionSucceeded -> handleBrokerScanActionSucceeded(pirRunState)
            is BrokerScanActionFailed -> handleBrokerScanActionFailed(pirRunState)
            is BrokerRecordOptOutStarted -> handleRecordOptOutStarted(pirRunState)
            is BrokerRecordOptOutCompleted -> handleRecordOptOutCompleted(pirRunState)
            is BrokerOptOutActionSucceeded -> handleBrokerOptOutActionSucceeded(pirRunState)
            is BrokerOptOutActionFailed -> handleBrokerOptOutActionFailed(pirRunState)
            else -> {}
        }
    }

    private suspend fun handleBrokerManualScanStarted(state: BrokerManualScanStarted) {
        pixelSender.reportBrokerScanStarted(state.brokerName)
        eventsRepository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = BROKER_STARTED,
            ),
        )
    }

    private suspend fun handleBrokerManualScanCompleted(state: BrokerManualScanCompleted) {
        handleScanError(state.isSuccess, state.brokerName, state.profileQueryId)
        pixelSender.reportBrokerScanCompleted(
            brokerName = state.brokerName,
            totalTimeInMillis = state.totalTimeMillis,
            isSuccess = state.isSuccess,
        )
        eventsRepository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = if (state.isSuccess) BROKER_SUCCESS else BROKER_ERROR,
            ),
        )
        eventsRepository.saveScanCompletedBroker(
            brokerName = state.brokerName,
            profileQueryId = state.profileQueryId,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.eventTimeInMillis,
            isSuccess = state.isSuccess,
        )
    }

    private suspend fun handleBrokerScheduledScanStarted(state: BrokerScheduledScanStarted) {
        pixelSender.reportBrokerScanStarted(state.brokerName)
        eventsRepository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = BROKER_STARTED,
            ),
        )
    }

    private suspend fun handleBrokerScheduledScanCompleted(state: BrokerScheduledScanCompleted) {
        handleScanError(state.isSuccess, state.brokerName, state.profileQueryId)
        pixelSender.reportBrokerScanCompleted(
            brokerName = state.brokerName,
            totalTimeInMillis = state.totalTimeMillis,
            isSuccess = state.isSuccess,
        )
        eventsRepository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = if (state.isSuccess) BROKER_SUCCESS else BROKER_ERROR,
            ),
        )
        eventsRepository.saveScanCompletedBroker(
            brokerName = state.brokerName,
            profileQueryId = state.profileQueryId,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.eventTimeInMillis,
            isSuccess = state.isSuccess,
        )
    }

    private suspend fun handleBrokerScanActionSucceeded(state: BrokerScanActionSucceeded) {
        when (state.pirSuccessResponse) {
            is ExtractedResponse -> state.pirSuccessResponse.response.map {
                ExtractedProfile(
                    profileUrl = it.profileUrl.orEmpty(),
                    profileQueryId = state.profileQueryId,
                    brokerName = state.brokerName,
                    name = it.name.orEmpty(),
                    alternativeNames = it.alternativeNames,
                    age = it.age.orEmpty(),
                    addresses = it.addresses.map { item ->
                        AddressCityState(
                            city = item.city,
                            state = item.state,
                            fullAddress = item.fullAddress,
                        )
                    },
                    phoneNumbers = it.phoneNumbers,
                    relatives = it.relatives,
                    identifier = it.identifier.orEmpty(),
                    reportId = it.reportId.orEmpty(),
                    email = it.email.orEmpty(),
                    fullName = it.fullName.orEmpty(),
                )
            }.also {
                if (it.isNotEmpty()) {
                    /**
                     * For every locally stored extractedProfile for the broker x profile that is not part of the newly received extracted Profiles,
                     * - We update the optOut status to REMOVED
                     * - We store the new extracted profiles. We ignore the ones that already exist.
                     * - Update the corresponding ScanJobRecord
                     */
                    jobRecordUpdater.markRemovedOptOutJobRecords(it, state.brokerName, state.profileQueryId)
                    repository.saveNewExtractedProfiles(it)
                    jobRecordUpdater.updateScanMatchesFound(state.brokerName, state.profileQueryId)
                } else {
                    jobRecordUpdater.updateScanNoMatchFound(state.brokerName, state.profileQueryId)
                }
            }

            else -> {}
        }
    }

    private fun handleBrokerScanActionFailed(state: BrokerScanActionFailed) {
        // TODO: remove if not needed later, might be used for stages
    }

    private suspend fun handleRecordOptOutStarted(state: BrokerRecordOptOutStarted) {
        jobRecordUpdater.markOptOutAsAttempted(state.extractedProfile.dbId)

        pixelSender.reportOptOutStarted(
            brokerName = state.brokerName,
        )
    }

    private suspend fun handleRecordOptOutCompleted(state: BrokerRecordOptOutCompleted) {
        updateOptOutRecord(state.isSubmitSuccess, state.extractedProfile.dbId)
        pixelSender.reportOptOutCompleted(
            brokerName = state.brokerName,
            totalTimeInMillis = state.endTimeInMillis - state.startTimeInMillis,
            isSuccess = state.isSubmitSuccess,
        )
        eventsRepository.saveOptOutCompleted(
            brokerName = state.brokerName,
            extractedProfile = state.extractedProfile,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.endTimeInMillis,
            isSubmitSuccess = state.isSubmitSuccess,
        )
    }

    private suspend fun handleBrokerOptOutActionSucceeded(state: BrokerOptOutActionSucceeded) {
        eventsRepository.saveOptOutActionLog(
            brokerName = state.brokerName,
            extractedProfile = state.extractedProfile,
            completionTimeInMillis = state.completionTimeInMillis,
            actionType = state.actionType,
            isError = false,
            result = pirSuccessAdapter.toJson(state.result),
        )
    }

    private suspend fun handleBrokerOptOutActionFailed(state: BrokerOptOutActionFailed) {
        eventsRepository.saveOptOutActionLog(
            brokerName = state.brokerName,
            extractedProfile = state.extractedProfile,
            completionTimeInMillis = state.completionTimeInMillis,
            actionType = state.actionType,
            isError = true,
            result = "${state.actionID}: ${state.message}}",
        )
    }

    private suspend fun handleScanError(
        isSuccess: Boolean,
        brokerName: String,
        profileQueryId: Long,
    ) {
        if (!isSuccess) {
            jobRecordUpdater.updateScanError(brokerName, profileQueryId)
        }
    }

    private suspend fun updateOptOutRecord(
        isSubmitted: Boolean,
        extractedProfileId: Long,
    ) {
        if (isSubmitted) {
            jobRecordUpdater.updateOptOutRequested(extractedProfileId)
        } else {
            jobRecordUpdater.updateOptOutError(extractedProfileId)
        }
    }
}
