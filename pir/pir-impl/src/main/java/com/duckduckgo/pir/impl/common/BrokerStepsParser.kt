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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

interface BrokerStepsParser {
    /**
     * This method parses the given json into BrokerStep that contains the actions needed to execute whatever step type.
     *
     * @param brokerName - name of the broker to which these steps belong to
     * @param stepsJson - string in JSONObject format obtained from the broker's json representing a step (scan / opt-out).
     * @param profileQueryId - profile query id associated with the step (used for the opt-out step)
     * @return list of broker steps resulting from the passed params. If the step is of type OptOut, it will return a list of
     *  OptOutSteps where an OptOut step is mapped to each of the profile for the broker.
     */
    suspend fun parseStep(
        brokerName: String,
        stepsJson: String,
        profileQueryId: Long? = null,
    ): List<BrokerStep>

    /**
     * This method parses the [optOutStepJson] and takes the subset starting from the emailConfirmation action.
     *
     * @param emailConfirmationJob associated email confirmation job record for this step
     * @param optOutStepJson - string in JSONObject format obtained from the emailConfirmationJob's broker's json representing an opt-out step.
     */
    suspend fun parseEmailConfirmationStep(
        optOutStepJson: String,
        emailConfirmationJob: EmailConfirmationJobRecord,
    ): BrokerStep?

    sealed class BrokerStep(
        open val brokerName: String = "", // this will be set later / not coming from json
        open val stepType: String,
        open val actions: List<BrokerAction>,
    ) {
        data class ScanStep(
            override val brokerName: String = "", // this will be set later / not coming from json
            override val stepType: String,
            override val actions: List<BrokerAction>,
            val scanType: String,
        ) : BrokerStep(brokerName, stepType, actions)

        data class OptOutStep(
            override val brokerName: String = "", // this will be set later / not coming from json
            override val stepType: String,
            override val actions: List<BrokerAction>,
            val optOutType: String,
            val profileToOptOut: ExtractedProfile = ExtractedProfile(-1, -1, ""), // this will be set later / not coming from json
        ) : BrokerStep(brokerName, stepType, actions)

        data class EmailConfirmationStep(
            override val brokerName: String,
            override val stepType: String,
            override val actions: List<BrokerAction>, // Actions will be a subset already starting from emailConfirmationStep
            val emailConfirmationJob: EmailConfirmationJobRecord,
            val profileToOptOut: ExtractedProfile,
        ) : BrokerStep(brokerName, stepType, actions)
    }
}

@ContributesBinding(AppScope::class)
class RealBrokerStepsParser @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val repository: PirRepository,
) : BrokerStepsParser {
    val adapter: JsonAdapter<BrokerStep> by lazy {
        Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(BrokerAction::class.java, "actionType")
                    .withSubtype(BrokerAction.Extract::class.java, "extract")
                    .withSubtype(BrokerAction.Expectation::class.java, "expectation")
                    .withSubtype(BrokerAction.Click::class.java, "click")
                    .withSubtype(BrokerAction.FillForm::class.java, "fillForm")
                    .withSubtype(BrokerAction.Navigate::class.java, "navigate")
                    .withSubtype(BrokerAction.GetCaptchaInfo::class.java, "getCaptchaInfo")
                    .withSubtype(BrokerAction.SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(BrokerAction.EmailConfirmation::class.java, "emailConfirmation"),
            )
            .add(
                PolymorphicJsonAdapterFactory.of(BrokerStep::class.java, "stepType")
                    .withSubtype(ScanStep::class.java, "scan")
                    .withSubtype(OptOutStep::class.java, "optOut"),
            )
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(BrokerStep::class.java)
    }

    override suspend fun parseStep(
        brokerName: String,
        stepsJson: String,
        profileQueryId: Long?,
    ): List<BrokerStep> = withContext(dispatcherProvider.io()) {
        return@withContext runCatching {
            adapter.fromJson(stepsJson)?.run {
                if (this is OptOutStep) {
                    if (profileQueryId == null) {
                        throw IllegalStateException("The profileQueryId is required when attempting to parse the opt-out steps.")
                    }
                    repository.getExtractedProfiles(brokerName, profileQueryId).map {
                        this.copy(
                            brokerName = brokerName,
                            profileToOptOut = it,
                        )
                    }
                } else {
                    listOf((this as ScanStep).copy(brokerName = brokerName))
                }
            } ?: emptyList<BrokerStep>()
        }.onFailure {
            logcat(ERROR) { "PIR-SCAN: Parsing the steps failed due to: $it" }
        }.getOrElse {
            emptyList<BrokerStep>()
        }
    }

    override suspend fun parseEmailConfirmationStep(
        optOutStepJson: String,
        emailConfirmationJob: EmailConfirmationJobRecord,
    ): BrokerStep? = withContext(dispatcherProvider.io()) {
        return@withContext runCatching {
            val profile = repository.getExtractedProfiles(emailConfirmationJob.extractedProfileId)
            if (profile != null) {
                adapter.fromJson(optOutStepJson)?.run {
                    EmailConfirmationStep(
                        brokerName = emailConfirmationJob.brokerName,
                        stepType = this.stepType,
                        actions = actions.dropWhile { it !is BrokerAction.EmailConfirmation },
                        emailConfirmationJob = emailConfirmationJob,
                        profileToOptOut = profile,
                    )
                }
            } else {
                null
            }
        }.onFailure {
            logcat(ERROR) { "PIR-SCAN: Parsing the steps failed due to: $it" }
        }.getOrNull()
    }
}
