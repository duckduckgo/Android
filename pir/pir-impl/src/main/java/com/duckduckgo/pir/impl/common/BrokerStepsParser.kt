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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject
import javax.inject.Named

interface BrokerStepsParser {
    /**
     * This method parses the given json into BrokerStep that contains the actions needed to execute whatever step type.
     *
     * @param broker - Broker to which these steps belong to
     * @param stepsJson - string in JSONObject format obtained from the broker's json representing a step (scan / opt-out).
     * @param profileQueryId - profile query id associated with the step (used for the opt-out step)
     * @return list of broker steps resulting from the passed params. If the step is of type OptOut, it will return a list of
     *  OptOutSteps where an OptOut step is mapped to each of the profile for the broker.
     */
    suspend fun parseStep(
        broker: Broker,
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
        broker: Broker,
        optOutStepJson: String,
        emailConfirmationJob: EmailConfirmationJobRecord,
    ): BrokerStep?

    sealed class BrokerStep(
        open val broker: Broker,
        open val step: BrokerStepActions,
    ) {
        data class ScanStep(
            override val broker: Broker,
            override val step: ScanStepActions,
        ) : BrokerStep(broker, step)

        data class OptOutStep(
            override val broker: Broker,
            override val step: OptOutStepActions,
            val profileToOptOut: ExtractedProfile,
        ) : BrokerStep(broker, step)

        data class EmailConfirmationStep(
            override val broker: Broker,
            override val step: OptOutStepActions,
            val emailConfirmationJob: EmailConfirmationJobRecord,
            val profileToOptOut: ExtractedProfile,
        ) : BrokerStep(broker, step)
    }

    sealed class BrokerStepActions(
        open val stepType: String,
        open val actions: List<BrokerAction>,
    ) {
        data class ScanStepActions(
            override val stepType: String,
            override val actions: List<BrokerAction>,
            val scanType: String,
        ) : BrokerStepActions(stepType, actions)

        data class OptOutStepActions(
            override val stepType: String,
            override val actions: List<BrokerAction>,
            val optOutType: String,
        ) : BrokerStepActions(stepType, actions)
    }
}

@ContributesBinding(AppScope::class)
class RealBrokerStepsParser @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val repository: PirRepository,
    @Named("pir") private val moshi: Moshi,
) : BrokerStepsParser {
    val adapter: JsonAdapter<BrokerStepActions> by lazy {
        moshi.adapter(BrokerStepActions::class.java)
    }

    override suspend fun parseStep(
        broker: Broker,
        stepsJson: String,
        profileQueryId: Long?,
    ): List<BrokerStep> = withContext(dispatcherProvider.io()) {
        return@withContext runCatching {
            adapter.fromJson(stepsJson)?.run {
                val stepActions = this
                if (this is OptOutStepActions) {
                    if (profileQueryId == null) {
                        throw IllegalStateException("The profileQueryId is required when attempting to parse the opt-out steps.")
                    }
                    repository.getExtractedProfiles(broker.name, profileQueryId).map {
                        OptOutStep(
                            broker = broker,
                            step = stepActions as OptOutStepActions,
                            profileToOptOut = it,
                        )
                    }
                } else {
                    listOf(
                        ScanStep(
                            broker = broker,
                            step = stepActions as ScanStepActions,
                        ),
                    )
                }
            } ?: emptyList<BrokerStep>()
        }.onFailure {
            logcat(ERROR) { "PIR-SCAN: Parsing the steps failed due to: $it" }
        }.getOrElse {
            emptyList<BrokerStep>()
        }
    }

    override suspend fun parseEmailConfirmationStep(
        broker: Broker,
        optOutStepJson: String,
        emailConfirmationJob: EmailConfirmationJobRecord,
    ): BrokerStep? = withContext(dispatcherProvider.io()) {
        return@withContext runCatching {
            val profile = repository.getExtractedProfile(emailConfirmationJob.extractedProfileId)
            if (profile != null) {
                adapter.fromJson(optOutStepJson)?.run {
                    val optOutStepActions = this as OptOutStepActions
                    EmailConfirmationStep(
                        broker = broker,
                        step = OptOutStepActions(
                            stepType = optOutStepActions.stepType,
                            actions = optOutStepActions.actions.dropWhile { it !is BrokerAction.EmailConfirmation },
                            optOutType = optOutStepActions.optOutType,
                        ),
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
