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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutConditionFound
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutConditionNotFound
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageCaptchaParsed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageCaptchaSent
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageCaptchaSolved
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageFillForm
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageGenerateEmailReceived
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageSubmit
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageValidate
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationNeeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutSubmitted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanSuccess
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerStepActionFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerStepInvalidEvent
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.asActionType
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_STARTED
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.impl.store.db.EmailConfirmationEventType.EMAIL_CONFIRMATION_FAILED
import com.duckduckgo.pir.impl.store.db.EmailConfirmationEventType.EMAIL_CONFIRMATION_SUCCESS
import com.duckduckgo.pir.impl.store.db.PirBrokerScanLog
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

interface PirRunStateHandler {
    suspend fun handleState(pirRunState: PirRunState)

    sealed class PirRunState(
        open val broker: Broker,
    ) {
        data class BrokerScanStarted(
            override val broker: Broker,
            val eventTimeInMillis: Long,
        ) : PirRunState(broker)

        data class BrokerScanSuccess(
            override val broker: Broker,
            val profileQueryId: Long,
            val startTimeInMillis: Long,
            val eventTimeInMillis: Long,
            val totalTimeMillis: Long,
            val isManualRun: Boolean,
            val lastAction: BrokerAction,
        ) : PirRunState(broker)

        data class BrokerScanFailed(
            override val broker: Broker,
            val profileQueryId: Long,
            val startTimeInMillis: Long,
            val eventTimeInMillis: Long,
            val totalTimeMillis: Long,
            val isManualRun: Boolean,
            val errorCategory: String?,
            val errorDetails: String?,
            val failedAction: BrokerAction,
        ) : PirRunState(broker)

        data class BrokerScanActionStarted(
            override val broker: Broker,
            val profileQueryId: Long,
            val currentActionAttemptCount: Int,
            val currentAction: BrokerAction,
        ) : PirRunState(broker)

        data class BrokerScanActionSucceeded(
            override val broker: Broker,
            val profileQueryId: Long,
            val pirSuccessResponse: PirSuccessResponse,
        ) : PirRunState(broker)

        data class BrokerRecordEmailConfirmationNeeded(
            override val broker: Broker,
            val extractedProfile: ExtractedProfile,
            val attemptId: String,
            val lastActionId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerRecordEmailConfirmationStarted(
            override val broker: Broker,
            val extractedProfileId: Long,
            val firstActionId: String,
        ) : PirRunState(broker)

        data class BrokerRecordEmailConfirmationCompleted(
            override val broker: Broker,
            val extractedProfile: ExtractedProfile,
            val isSuccess: Boolean,
            val lastActionId: String,
            val totalTimeMillis: Long,
            val emailPattern: String,
            val attemptId: String,
        ) : PirRunState(broker)

        data class BrokerRecordOptOutStarted(
            override val broker: Broker,
            val extractedProfile: ExtractedProfile,
            val attemptId: String,
        ) : PirRunState(broker)

        data class BrokerRecordOptOutSubmitted(
            override val broker: Broker,
            val extractedProfile: ExtractedProfile,
            val attemptId: String,
            val startTimeInMillis: Long,
            val endTimeInMillis: Long,
            val emailPattern: String?,
        ) : PirRunState(broker)

        data class BrokerRecordOptOutFailed(
            override val broker: Broker,
            val extractedProfile: ExtractedProfile,
            val attemptId: String,
            val startTimeInMillis: Long,
            val endTimeInMillis: Long,
            val failedAction: BrokerAction,
            val stage: PirStage,
            val emailPattern: String?,
        ) : PirRunState(broker)

        data class BrokerOptOutActionSucceeded(
            override val broker: Broker,
            val extractedProfile: ExtractedProfile,
            val completionTimeInMillis: Long,
            val actionType: String,
            val result: PirSuccessResponse,
        ) : PirRunState(broker)

        data class BrokerOptOutStageGenerateEmailReceived(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutStageCaptchaParsed(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutStageCaptchaSent(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutStageCaptchaSolved(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutStageSubmit(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutStageValidate(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutStageFillForm(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutConditionFound(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerOptOutConditionNotFound(
            override val broker: Broker,
            val actionID: String,
            val attemptId: String,
            val durationMs: Long,
            val currentActionAttemptCount: Int,
        ) : PirRunState(broker)

        data class BrokerStepActionFailed(
            override val broker: Broker,
            val extractedProfile: ExtractedProfile?,
            val completionTimeInMillis: Long,
            val stepType: String,
            val actionType: String,
            val actionID: String,
            val errorMessage: String,
        ) : PirRunState(broker)

        data class BrokerStepInvalidEvent(
            override val broker: Broker,
            val runType: RunType,
        ) : PirRunState(broker)
    }
}

@ContributesBinding(AppScope::class)
class RealPirRunStateHandler @Inject constructor(
    private val repository: PirRepository,
    private val eventsRepository: PirEventsRepository,
    private val pixelSender: PirPixelSender,
    private val dispatcherProvider: DispatcherProvider,
    private val jobRecordUpdater: JobRecordUpdater,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    @Named("pir") private val moshi: Moshi,
    private val networkProtectionState: NetworkProtectionState,
) : PirRunStateHandler {
    private val pirSuccessAdapter by lazy { moshi.adapter(PirSuccessResponse::class.java) }

    override suspend fun handleState(pirRunState: PirRunState) =
        withContext(dispatcherProvider.io()) {
            when (pirRunState) {
                is BrokerScanStarted -> handleBrokerScanStarted(pirRunState)
                is BrokerScanFailed -> handleBrokerScanFailed(pirRunState)
                is BrokerScanSuccess -> handleBrokerScanSuccess(pirRunState)
                is BrokerScanActionStarted -> handleBrokerScanActionStarted(pirRunState)
                is BrokerScanActionSucceeded -> handleBrokerScanActionSucceeded(pirRunState)
                is BrokerStepActionFailed -> handleBrokerActionFailed(pirRunState)
                is BrokerRecordOptOutStarted -> handleRecordOptOutStarted(pirRunState)
                is BrokerRecordOptOutSubmitted -> handleBrokerRecordOptOutSubmitted(pirRunState)
                is BrokerRecordOptOutFailed -> handleBrokerRecordOptOutFailed(pirRunState)
                is BrokerOptOutActionSucceeded -> handleBrokerOptOutActionSucceeded(pirRunState)
                is BrokerRecordEmailConfirmationNeeded -> handleBrokerRecordEmailConfirmationNeeded(pirRunState)
                is BrokerRecordEmailConfirmationStarted -> handleBrokerRecordEmailConfirmationStarted(pirRunState)
                is BrokerRecordEmailConfirmationCompleted -> handleBrokerRecordEmailConfirmationCompleted(pirRunState)
                is BrokerOptOutConditionFound -> handleBrokerOptOutConditionFound(pirRunState)
                is BrokerOptOutConditionNotFound -> handleBrokerOptOutConditionNotFound(pirRunState)
                is BrokerOptOutStageCaptchaParsed -> handleBrokerOptOutStageCaptchaParsed(pirRunState)
                is BrokerOptOutStageCaptchaSent -> handleBrokerOptOutStageCaptchaSent(pirRunState)
                is BrokerOptOutStageCaptchaSolved -> handleBrokerOptOutStageCaptchaSolved(pirRunState)
                is BrokerOptOutStageFillForm -> handleBrokerOptOutStageFillForm(pirRunState)
                is BrokerOptOutStageGenerateEmailReceived -> handleBrokerOptOutStageGenerateEmailReceived(pirRunState)
                is BrokerOptOutStageSubmit -> handleBrokerOptOutStageSubmit(pirRunState)
                is BrokerOptOutStageValidate -> handleBrokerOptOutStageValidate(pirRunState)
                is BrokerStepInvalidEvent -> handleBrokerStepInvalidEvent(pirRunState)
            }
        }

    private fun handleBrokerStepInvalidEvent(pirRunState: BrokerStepInvalidEvent) {
        if (pirRunState.runType == RunType.MANUAL || pirRunState.runType == RunType.SCHEDULED) {
            pixelSender.reportScanInvalidEvent(
                brokerUrl = pirRunState.broker.url,
                brokerVersion = pirRunState.broker.version,
            )
        } else {
            pixelSender.reportOptOutInvalidEvent(
                brokerUrl = pirRunState.broker.url,
                brokerVersion = pirRunState.broker.version,
            )
        }
    }

    private fun handleBrokerOptOutStageSubmit(pirRunState: BrokerOptOutStageSubmit) {
        pixelSender.reportOptOutStageSubmit(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutStageGenerateEmailReceived(pirRunState: BrokerOptOutStageGenerateEmailReceived) {
        pixelSender.reportOptOutStageEmailGenerate(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutStageFillForm(pirRunState: BrokerOptOutStageFillForm) {
        pixelSender.reportOptOutStageFillForm(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutStageCaptchaSolved(pirRunState: BrokerOptOutStageCaptchaSolved) {
        pixelSender.reportOptOutStageCaptchaSolve(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutStageCaptchaSent(pirRunState: BrokerOptOutStageCaptchaSent) {
        pixelSender.reportOptOutStageCaptchaSend(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutStageCaptchaParsed(pirRunState: BrokerOptOutStageCaptchaParsed) {
        pixelSender.reportOptOutStageCaptchaParse(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutConditionNotFound(pirRunState: BrokerOptOutConditionNotFound) {
        pixelSender.reportOptOutConditionNotFound(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutConditionFound(pirRunState: BrokerOptOutConditionFound) {
        pixelSender.reportOptOutConditionFound(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerOptOutStageValidate(pirRunState: BrokerOptOutStageValidate) {
        pixelSender.reportOptOutStageValidate(
            brokerUrl = pirRunState.broker.url,
            parentUrl = pirRunState.broker.parent.orEmpty(),
            brokerVersion = pirRunState.broker.version,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
            actionId = pirRunState.actionID,
        )
    }

    private fun handleBrokerScanActionStarted(state: BrokerScanActionStarted) {
        pixelSender.reportScanStage(
            brokerUrl = state.broker.url,
            brokerVersion = state.broker.version,
            tries = state.currentActionAttemptCount,
            parentUrl = state.broker.parent.orEmpty(),
            actionId = state.currentAction.id,
            actionType = state.currentAction.asActionType(),
        )
    }

    private suspend fun handleBrokerScanStarted(state: BrokerScanStarted) {
        pixelSender.reportScanStarted(brokerUrl = state.broker.url)
        eventsRepository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.broker.name,
                eventType = BROKER_STARTED,
            ),
        )
    }

    private suspend fun handleBrokerScanFailed(state: BrokerScanFailed) {
        val brokerName = state.broker.name
        jobRecordUpdater.updateScanError(brokerName, state.profileQueryId)
        pixelSender.reportScanError(
            brokerUrl = state.broker.url,
            brokerVersion = state.broker.version,
            durationMs = state.totalTimeMillis,
            errorCategory = state.errorCategory ?: "Unknown",
            errorDetails = state.errorDetails ?: "Unknown",
            inManualStarted = state.isManualRun,
            parentUrl = state.broker.parent.orEmpty(),
            actionId = state.failedAction.id,
            actionType = state.failedAction.asActionType(),
            isVpnRunning = safeIsVpnRunning(),
        )
        eventsRepository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = brokerName,
                eventType = BROKER_ERROR,
            ),
        )
        eventsRepository.saveScanCompletedBroker(
            brokerName = brokerName,
            profileQueryId = state.profileQueryId,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.eventTimeInMillis,
            isSuccess = false,
        )
    }

    private suspend fun handleBrokerScanSuccess(state: BrokerScanSuccess) {
        val matchCount = repository.getExtractedProfiles(state.broker.name, state.profileQueryId).size
        if (matchCount == 0) {
            pixelSender.reportScanNoMatch(
                brokerUrl = state.broker.url,
                brokerVersion = state.broker.version,
                durationMs = state.totalTimeMillis,
                inManualStarted = state.isManualRun,
                parentUrl = state.broker.parent.orEmpty(),
                actionId = state.lastAction.id,
                actionType = state.lastAction.asActionType(),
                isVpnRunning = safeIsVpnRunning(),
            )
        } else {
            pixelSender.reportScanMatches(
                brokerUrl = state.broker.url,
                durationMs = state.totalTimeMillis,
                inManualStarted = state.isManualRun,
                parentUrl = state.broker.parent.orEmpty(),
                totalMatches = matchCount,
                isVpnRunning = safeIsVpnRunning(),
            )
        }
        val brokerName = state.broker.name
        eventsRepository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = brokerName,
                eventType = BROKER_SUCCESS,
            ),
        )
        eventsRepository.saveScanCompletedBroker(
            brokerName = brokerName,
            profileQueryId = state.profileQueryId,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.eventTimeInMillis,
            isSuccess = true,
        )
    }

    private suspend fun handleBrokerRecordEmailConfirmationStarted(pirRunState: BrokerRecordEmailConfirmationStarted) {
        val updatedRecord = jobRecordUpdater.recordEmailConfirmationAttempt(pirRunState.extractedProfileId)

        if (updatedRecord != null) {
            pixelSender.reportEmailConfirmationAttemptStart(
                brokerUrl = pirRunState.broker.url,
                brokerVersion = pirRunState.broker.version,
                attemptNumber = updatedRecord.jobAttemptData.jobAttemptCount,
                actionId = pirRunState.firstActionId,
            )
        }
    }

    private suspend fun handleBrokerRecordEmailConfirmationCompleted(pirRunState: BrokerRecordEmailConfirmationCompleted) {
        val extractedProfileId = pirRunState.extractedProfile.dbId
        if (pirRunState.isSuccess) {
            // The job we pass to the engine could have outdated info so we just re-fetch it
            val updatedRecord = pirSchedulingRepository.getEmailConfirmationJob(extractedProfileId)

            if (updatedRecord != null) {
                pixelSender.reportEmailConfirmationAttemptSuccess(
                    brokerUrl = pirRunState.broker.url,
                    brokerVersion = pirRunState.broker.version,
                    attemptNumber = updatedRecord.jobAttemptData.jobAttemptCount,
                    actionId = pirRunState.lastActionId,
                    durationMs = pirRunState.totalTimeMillis,
                )
                emitAndLogBrokerOptOutSubmitted(
                    brokerUrl = pirRunState.broker.url,
                    brokerName = pirRunState.broker.name,
                    brokerParent = pirRunState.broker.parent.orEmpty(),
                    attemptId = pirRunState.attemptId,
                    attemptCount = updatedRecord.jobAttemptData.jobAttemptCount,
                    emailPattern = pirRunState.emailPattern,
                    extractedProfile = pirRunState.extractedProfile,
                    startTimeInMillis = currentTimeProvider.currentTimeMillis() - pirRunState.totalTimeMillis,
                    endTimeInMillis = currentTimeProvider.currentTimeMillis(),
                )
            }

            jobRecordUpdater.recordEmailConfirmationCompleted(extractedProfileId)

            pixelSender.reportEmailConfirmationJobSuccess(
                brokerUrl = pirRunState.broker.url,
                brokerVersion = pirRunState.broker.version,
            )

            eventsRepository.saveEmailConfirmationLog(
                eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                type = EMAIL_CONFIRMATION_SUCCESS,
                detail = pirRunState.broker.name,
            )
        } else {
            val updatedRecord = jobRecordUpdater.recordEmailConfirmationFailed(
                extractedProfileId,
                pirRunState.lastActionId,
            )

            if (updatedRecord != null) {
                pixelSender.reportEmailConfirmationAttemptFailed(
                    brokerUrl = pirRunState.broker.url,
                    brokerVersion = pirRunState.broker.version,
                    attemptNumber = updatedRecord.jobAttemptData.jobAttemptCount,
                    actionId = updatedRecord.jobAttemptData.lastJobAttemptActionId,
                    durationMs = pirRunState.totalTimeMillis,
                )
                eventsRepository.saveEmailConfirmationLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    type = EMAIL_CONFIRMATION_FAILED,
                    detail = pirRunState.broker.name,
                )
            }
        }
    }

    private suspend fun handleBrokerRecordEmailConfirmationNeeded(pirRunState: BrokerRecordEmailConfirmationNeeded) {
        jobRecordUpdater.markOptOutAsWaitingForEmailConfirmation(
            profileQueryId = pirRunState.extractedProfile.profileQueryId,
            extractedProfileId = pirRunState.extractedProfile.dbId,
            brokerName = pirRunState.broker.name,
            email = pirRunState.extractedProfile.email,
            attemptId = pirRunState.attemptId,
        )
        pixelSender.reportStagePendingEmailConfirmation(
            brokerUrl = pirRunState.broker.url,
            brokerVersion = pirRunState.broker.version,
            actionId = pirRunState.lastActionId,
            durationMs = pirRunState.durationMs,
            tries = pirRunState.currentActionAttemptCount,
        )
    }

    private suspend fun handleBrokerScanActionSucceeded(state: BrokerScanActionSucceeded) {
        val brokerName = state.broker.name

        when (state.pirSuccessResponse) {
            is ExtractedResponse -> {
                state.pirSuccessResponse.response
                    .map {
                        ExtractedProfile(
                            profileUrl = it.profileUrl.orEmpty(),
                            profileQueryId = state.profileQueryId,
                            brokerName = brokerName,
                            name = it.name.orEmpty(),
                            alternativeNames = it.alternativeNames,
                            age = it.age.orEmpty(),
                            addresses =
                            it.addresses.map { item ->
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
                        /**
                         * For every locally stored extractedProfile for the broker x profile that is not part of the newly received
                         * extracted Profiles, or no extracted Profiles were found on the broker:
                         * - We update the optOut status to REMOVED
                         * - We store the new extracted profiles (if profile query is not deprecated). We ignore the ones that already exist.
                         * - Update the corresponding ScanJobRecord
                         */
                        jobRecordUpdater.markRemovedOptOutJobRecords(it, brokerName, state.profileQueryId)

                        if (it.isNotEmpty()) {
                            jobRecordUpdater.updateScanMatchesFound(it, brokerName, state.profileQueryId)
                            repository.saveNewExtractedProfiles(it)
                        } else {
                            jobRecordUpdater.updateScanNoMatchFound(brokerName, state.profileQueryId)
                        }
                    }
            }

            else -> {}
        }
    }

    private suspend fun handleBrokerActionFailed(state: BrokerStepActionFailed) {
        pixelSender.reportBrokerActionFailure(
            brokerUrl = state.broker.url,
            brokerVersion = state.broker.version,
            parentUrl = state.broker.parent.orEmpty(),
            actionId = state.actionID,
            errorMessage = state.errorMessage,
            stepType = state.stepType,
        )

        if (state.stepType == KEY_STEPTYPE_OPTOUT && state.extractedProfile != null) {
            eventsRepository.saveOptOutActionLog(
                brokerName = state.broker.name,
                extractedProfile = state.extractedProfile,
                completionTimeInMillis = state.completionTimeInMillis,
                actionType = state.actionType,
                isError = true,
                result = "${state.actionID}: ${state.errorMessage}",
            )
        }
    }

    private suspend fun handleRecordOptOutStarted(state: BrokerRecordOptOutStarted) {
        jobRecordUpdater.markOptOutAsAttempted(state.extractedProfile.dbId)
        pixelSender.reportOptOutStageStart(
            brokerUrl = state.broker.url,
            parentUrl = state.broker.parent.orEmpty(),
        )
    }

    private suspend fun handleBrokerRecordOptOutSubmitted(state: BrokerRecordOptOutSubmitted) {
        val optOutJobRecord = updateOptOutRecord(true, state.extractedProfile.dbId) ?: return
        emitAndLogBrokerOptOutSubmitted(
            brokerUrl = state.broker.url,
            brokerName = state.broker.name,
            brokerParent = state.broker.parent.orEmpty(),
            attemptId = state.attemptId,
            attemptCount = optOutJobRecord.attemptCount,
            emailPattern = state.emailPattern.orEmpty(),
            extractedProfile = state.extractedProfile,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.endTimeInMillis,
        )
    }

    private suspend fun emitAndLogBrokerOptOutSubmitted(
        brokerUrl: String,
        brokerName: String,
        brokerParent: String,
        attemptId: String,
        attemptCount: Int,
        emailPattern: String,
        extractedProfile: ExtractedProfile,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
    ) {
        pixelSender.reportOptOutSubmitted(
            brokerUrl = brokerUrl,
            parent = brokerParent,
            durationMs = endTimeInMillis - startTimeInMillis,
            optOutAttemptCount = attemptCount,
            emailPattern = emailPattern,
            isVpnRunning = safeIsVpnRunning(),
        )

        eventsRepository.saveOptOutCompleted(
            brokerName = brokerName,
            extractedProfile = extractedProfile,
            startTimeInMillis = startTimeInMillis,
            endTimeInMillis = endTimeInMillis,
            isSubmitSuccess = true,
        )
    }

    private suspend fun handleBrokerRecordOptOutFailed(state: BrokerRecordOptOutFailed) {
        val optOutJobRecord = updateOptOutRecord(false, state.extractedProfile.dbId) ?: return

        pixelSender.reportOptOutFailed(
            brokerUrl = state.broker.url,
            parent = state.broker.parent.orEmpty(),
            brokerJsonVersion = state.broker.version,
            durationMs = state.endTimeInMillis - state.startTimeInMillis,
            tries = optOutJobRecord.attemptCount,
            emailPattern = state.emailPattern,
            stage = state.stage,
            actionId = state.failedAction.id,
            actionType = state.failedAction.asActionType(),
            isVpnRunning = safeIsVpnRunning(),
        )

        eventsRepository.saveOptOutCompleted(
            brokerName = state.broker.name,
            extractedProfile = state.extractedProfile,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.endTimeInMillis,
            isSubmitSuccess = false,
        )
    }

    private suspend fun handleBrokerOptOutActionSucceeded(state: BrokerOptOutActionSucceeded) {
        eventsRepository.saveOptOutActionLog(
            brokerName = state.broker.name,
            extractedProfile = state.extractedProfile,
            completionTimeInMillis = state.completionTimeInMillis,
            actionType = state.actionType,
            isError = false,
            result = pirSuccessAdapter.toJson(state.result),
        )
    }

    private suspend fun updateOptOutRecord(
        isSubmitted: Boolean,
        extractedProfileId: Long,
    ): OptOutJobRecord? {
        return if (isSubmitted) {
            jobRecordUpdater.updateOptOutRequested(extractedProfileId)
        } else {
            jobRecordUpdater.updateOptOutError(extractedProfileId)
        }
    }

    private suspend fun safeIsVpnRunning(): Boolean {
        return runCatching { networkProtectionState.isRunning() }.getOrElse { false }
    }

    companion object {
        private const val KEY_STEPTYPE_OPTOUT = "optOut"
    }
}
