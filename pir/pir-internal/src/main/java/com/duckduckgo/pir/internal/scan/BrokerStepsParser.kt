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

package com.duckduckgo.pir.internal.scan

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.pir.internal.scan.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.internal.scripts.models.BrokerAction
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

interface BrokerStepsParser {
    /**
     * This method parses the given json into BrokerStep that contains the actions needed to execute whatever step type.
     *
     * @param stepsJson - string in JSONObject format obtained from the broker's json representing a step (scan / opt-out).
     */
    suspend fun parseStep(stepsJson: String): BrokerStep?

    data class BrokerStep(
        val stepType: String,
        val scanType: String,
        val actions: List<BrokerAction>,
    )
}

@ContributesBinding(ServiceScope::class)
class RealBrokerStepsParser @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
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
                    .withSubtype(BrokerAction.GetCaptchInfo::class.java, "getCaptchaInfo")
                    .withSubtype(BrokerAction.SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(BrokerAction.EmailConfirmation::class.java, "emailConfirmation"),
            ).add(KotlinJsonAdapterFactory())
            .build()
            .adapter(BrokerStep::class.java)
    }

    override suspend fun parseStep(stepsJson: String): BrokerStep? = withContext(dispatcherProvider.io()) {
        return@withContext runCatching {
            adapter.fromJson(stepsJson)
        }.onFailure {
            logcat(ERROR) { "PIR-SCAN: Parsing the steps failed due to: $it" }
        }.getOrNull()
    }
}
