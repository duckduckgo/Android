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

package com.duckduckgo.pir.impl.brokers

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.INVALID_STEP
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.MISSING_STEP
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.UNSUPPORTED_ACTION
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.UNSUPPORTED_STEP
import com.duckduckgo.pir.impl.common.BrokerJsonTypes
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject
import javax.inject.Named

interface BrokerStepsValidator {
    /**
     * Checks that this app version can execute every step of a broker JSON.
     *
     * @param steps the broker's steps, each one a JSON object string
     * @return null when every step can be executed, otherwise why the broker JSON has to be rejected
     */
    fun validate(steps: List<String>): BrokerJsonUpdateFailureReason?
}

@ContributesBinding(AppScope::class)
class RealBrokerStepsValidator @Inject constructor(
    @Named("pir") private val moshi: Moshi,
) : BrokerStepsValidator {
    private val stepAdapter by lazy { moshi.adapter(BrokerStepActions::class.java) }
    private val stepMapAdapter by lazy {
        moshi.adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    }

    override fun validate(steps: List<String>): BrokerJsonUpdateFailureReason? {
        val stepTypes = mutableSetOf<String>()
        for (step in steps) {
            val stepMap = runCatching { stepMapAdapter.fromJson(step) }.getOrNull() ?: return INVALID_STEP
            val stepType = stepMap[BrokerJsonTypes.STEP_TYPE_KEY] as? String
            if (stepType == null || stepType !in BrokerJsonTypes.stepSubtypes) return UNSUPPORTED_STEP
            if (containsUnsupportedAction(stepMap[BrokerJsonTypes.ACTIONS_KEY])) return UNSUPPORTED_ACTION
            // The scan engine parses steps with this same adapter, so anything it rejects here would fail at run time.
            if (runCatching { stepAdapter.fromJson(step) }.getOrNull() == null) return INVALID_STEP
            stepTypes += stepType
        }
        return if (stepTypes.containsAll(REQUIRED_STEP_TYPES)) null else MISSING_STEP
    }

    private fun containsUnsupportedAction(actions: Any?): Boolean =
        (actions as? List<*>).orEmpty().any { action ->
            val actionMap = action as? Map<*, *> ?: return@any false
            val actionType = actionMap[BrokerJsonTypes.ACTION_TYPE_KEY] as? String
            val isUnsupported = actionType != null && actionType !in BrokerJsonTypes.actionSubtypes
            isUnsupported || containsUnsupportedAction(actionMap[BrokerJsonTypes.ACTIONS_KEY])
        }

    companion object {
        private val REQUIRED_STEP_TYPES = setOf(BrokerJsonTypes.STEP_TYPE_SCAN, BrokerJsonTypes.STEP_TYPE_OPT_OUT)
    }
}
