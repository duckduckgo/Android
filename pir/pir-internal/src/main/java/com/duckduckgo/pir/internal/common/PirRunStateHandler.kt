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

package com.duckduckgo.pir.internal.common

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerManualScanCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerManualScanStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutActionFailed
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutActionSucceeded
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerOptOutStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScanActionFailed
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScheduledScanCompleted
import com.duckduckgo.pir.internal.common.PirRunStateHandler.PirRunState.BrokerScheduledScanStarted
import com.duckduckgo.pir.internal.pixels.PirPixelSender
import com.duckduckgo.pir.internal.scripts.models.ExtractedProfile
import com.duckduckgo.pir.internal.scripts.models.PirErrorReponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.internal.store.db.BrokerScanEventType.BROKER_STARTED
import com.duckduckgo.pir.internal.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.internal.store.db.PirBrokerScanLog
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
            val startTimeInMillis: Long,
            val eventTimeInMillis: Long,
            val totalTimeMillis: Long,
            val isSuccess: Boolean,
        ) : PirRunState(brokerName)

        data class BrokerScanActionSucceeded(
            override val brokerName: String,
            val pirSuccessResponse: PirSuccessResponse,
        ) : PirRunState(brokerName)

        data class BrokerScanActionFailed(
            override val brokerName: String,
            val actionType: String,
            val pirErrorReponse: PirErrorReponse,
        ) : PirRunState(brokerName)

        data class BrokerOptOutStarted(
            override val brokerName: String,
        ) : PirRunState(brokerName)

        data class BrokerOptOutCompleted(
            override val brokerName: String,
            val startTimeInMillis: Long,
            val endTimeInMillis: Long,
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
            val result: PirErrorReponse,
        ) : PirRunState(brokerName)
    }
}

@ContributesBinding(AppScope::class)
class RealPirRunStateHandler @Inject constructor(
    private val repository: PirRepository,
    private val pixelSender: PirPixelSender,
    private val dispatcherProvider: DispatcherProvider,
) : PirRunStateHandler {
    private val pirSuccessAdapter by lazy {
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
            .build().adapter(PirSuccessResponse::class.java)
    }
    private val pirErrorAdapter by lazy {
        Moshi.Builder().build().adapter(PirErrorReponse::class.java)
    }

    override suspend fun handleState(pirRunState: PirRunState) = withContext(dispatcherProvider.io()) {
        when (pirRunState) {
            is BrokerManualScanStarted -> handleBrokerManualScanStarted(pirRunState)
            is BrokerManualScanCompleted -> handleBrokerManualScanCompleted(pirRunState)
            is BrokerScheduledScanStarted -> handleBrokerScheduledScanStarted(pirRunState)
            is BrokerScheduledScanCompleted -> handleBrokerScheduledScanCompleted(pirRunState)
            is BrokerScanActionSucceeded -> handleBrokerScanActionSucceeded(pirRunState)
            is BrokerScanActionFailed -> handleBrokerScanActionFailed(pirRunState)
            is BrokerOptOutStarted -> handleBrokerOptOutStarted(pirRunState)
            is BrokerOptOutCompleted -> handleBrokerOptOutCompleted(pirRunState)
            is BrokerRecordOptOutStarted -> handleRecordOptOutStarted(pirRunState)
            is BrokerRecordOptOutCompleted -> handleRecordOptOutCompleted(pirRunState)
            is BrokerOptOutActionSucceeded -> handleBrokerOptOutActionSucceeded(pirRunState)
            is BrokerOptOutActionFailed -> handleBrokerOptOutActionFailed(pirRunState)
            else -> {}
        }
    }

    private suspend fun handleBrokerManualScanStarted(state: BrokerManualScanStarted) {
        pixelSender.reportManualScanBrokerStarted(state.brokerName)
        repository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = BROKER_STARTED,
            ),
        )
    }

    private suspend fun handleBrokerManualScanCompleted(state: BrokerManualScanCompleted) {
        pixelSender.reportManualScanBrokerCompleted(
            brokerName = state.brokerName,
            totalTimeInMillis = state.totalTimeMillis,
            isSuccess = state.isSuccess,
        )
        repository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = if (state.isSuccess) BROKER_SUCCESS else BROKER_ERROR,
            ),
        )
        repository.saveScanCompletedBroker(
            brokerName = state.brokerName,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.eventTimeInMillis,
        )
    }

    private suspend fun handleBrokerScheduledScanStarted(state: BrokerScheduledScanStarted) {
        pixelSender.reportScheduledScanBrokerStarted(state.brokerName)
        repository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = BROKER_STARTED,
            ),
        )
    }

    private suspend fun handleBrokerScheduledScanCompleted(state: BrokerScheduledScanCompleted) {
        pixelSender.reportScheduledScanBrokerCompleted(
            brokerName = state.brokerName,
            totalTimeInMillis = state.totalTimeMillis,
            isSuccess = state.isSuccess,
        )
        repository.saveBrokerScanLog(
            PirBrokerScanLog(
                eventTimeInMillis = state.eventTimeInMillis,
                brokerName = state.brokerName,
                eventType = if (state.isSuccess) BROKER_SUCCESS else BROKER_ERROR,
            ),
        )
        repository.saveScanCompletedBroker(
            brokerName = state.brokerName,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.eventTimeInMillis,
        )
    }

    private suspend fun handleBrokerScanActionSucceeded(state: BrokerScanActionSucceeded) {
        when (state.pirSuccessResponse) {
            is NavigateResponse -> repository.saveNavigateResult(
                state.brokerName,
                state.pirSuccessResponse,
            )

            is ExtractedResponse -> repository.saveExtractProfileResult(
                state.brokerName,
                state.pirSuccessResponse,
            )

            else -> {}
        }
    }

    private suspend fun handleBrokerScanActionFailed(state: BrokerScanActionFailed) {
        repository.saveErrorResult(
            brokerName = state.brokerName,
            actionType = state.actionType,
            error = state.pirErrorReponse,
        )
    }

    private fun handleBrokerOptOutStarted(state: BrokerOptOutStarted) {
        pixelSender.reportBrokerOptOutStarted(
            brokerName = state.brokerName,
        )
    }

    private fun handleBrokerOptOutCompleted(state: BrokerOptOutCompleted) {
        pixelSender.reportBrokerOptOutCompleted(
            brokerName = state.brokerName,
            totalTimeInMillis = state.endTimeInMillis - state.startTimeInMillis,
        )
    }

    private fun handleRecordOptOutStarted(state: BrokerRecordOptOutStarted) {
        pixelSender.reportRecordOptOutStarted(
            brokerName = state.brokerName,
        )
    }

    private suspend fun handleRecordOptOutCompleted(state: BrokerRecordOptOutCompleted) {
        pixelSender.reportRecordOptOutCompleted(
            brokerName = state.brokerName,
            totalTimeInMillis = state.endTimeInMillis - state.startTimeInMillis,
            isSuccess = state.isSubmitSuccess,
        )
        repository.saveOptOutCompleted(
            brokerName = state.brokerName,
            extractedProfile = state.extractedProfile,
            startTimeInMillis = state.startTimeInMillis,
            endTimeInMillis = state.endTimeInMillis,
            isSubmitSuccess = state.isSubmitSuccess,
        )
    }

    private suspend fun handleBrokerOptOutActionSucceeded(state: BrokerOptOutActionSucceeded) {
        repository.saveOptOutActionLog(
            brokerName = state.brokerName,
            extractedProfile = state.extractedProfile,
            completionTimeInMillis = state.completionTimeInMillis,
            actionType = state.actionType,
            isError = false,
            result = pirSuccessAdapter.toJson(state.result),
        )
    }

    private suspend fun handleBrokerOptOutActionFailed(state: BrokerOptOutActionFailed) {
        repository.saveOptOutActionLog(
            brokerName = state.brokerName,
            extractedProfile = state.extractedProfile,
            completionTimeInMillis = state.completionTimeInMillis,
            actionType = state.actionType,
            isError = true,
            result = pirErrorAdapter.toJson(state.result),
        )
    }
}
