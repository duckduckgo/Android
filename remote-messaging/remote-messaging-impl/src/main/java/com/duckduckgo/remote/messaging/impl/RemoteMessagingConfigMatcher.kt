/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.matchers.AndroidAppAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.DeviceAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.UserAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.toResult
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.duckduckgo.remote.messaging.impl.matchers.EvaluationResult
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Unknown
import timber.log.Timber

class RemoteMessagingConfigMatcher(
    val deviceAttributeMatcher: DeviceAttributeMatcher,
    val androidAppAttributeMatcher: AndroidAppAttributeMatcher,
    val userAttributeMatcher: UserAttributeMatcher
) {
    private val matchers = listOf(deviceAttributeMatcher, androidAppAttributeMatcher, userAttributeMatcher)

    suspend fun evaluate(remoteConfig: RemoteConfig): RemoteMessage? {
        val rules = remoteConfig.rules

        remoteConfig.messages.forEach { message ->
            val matchingRules = if (message.matchingRules.isEmpty() && message.exclusionRules.isEmpty()) return message else message.matchingRules

            val matchingResult = matchingRules.evaluateMatchingRules(rules)
            val excludeResult = message.exclusionRules.evaluateExclusionRules(rules)

            if (matchingResult == EvaluationResult.Match && excludeResult == EvaluationResult.Fail) return message
        }

        return null
    }

    private suspend fun Iterable<Int>.evaluateMatchingRules(rules: Map<Int, List<MatchingAttribute>>): EvaluationResult {
        var result: EvaluationResult = EvaluationResult.Match

        for (rule in this) {
            val attributes = rules[rule].takeUnless { it.isNullOrEmpty() } ?: return EvaluationResult.NextMessage
            result = EvaluationResult.Match

            for (attr in attributes) {
                result = evaluateAttribute(attr)
                if (result == EvaluationResult.Fail || result == EvaluationResult.NextMessage) {
                    Timber.i("RMF: first failed attribute $attr")
                    break
                }
            }

            if (result == EvaluationResult.NextMessage || result == EvaluationResult.Match) return result
        }

        return result
    }

    private suspend fun Iterable<Int>.evaluateExclusionRules(rules: Map<Int, List<MatchingAttribute>>): EvaluationResult {
        var result: EvaluationResult = EvaluationResult.Fail

        for (rule in this) {
            val attributes = rules[rule].takeUnless { it.isNullOrEmpty() } ?: return EvaluationResult.NextMessage
            result = EvaluationResult.Fail

            for (attr in attributes) {
                result = evaluateAttribute(attr)
                if (result == EvaluationResult.Fail || result == EvaluationResult.NextMessage) {
                    Timber.i("RMF: first failed attribute $attr")
                    break
                }
            }

            if (result == EvaluationResult.NextMessage || result == EvaluationResult.Match) return result
        }

        return result
    }

    private suspend fun evaluateAttribute(matchingAttribute: MatchingAttribute): EvaluationResult {
        if (matchingAttribute is Unknown) {
            return matchingAttribute.fallback.toResult()
        } else {
            matchers.forEach {
                val result = it.evaluate(matchingAttribute)
                if (result != null) return result
            }
        }

        return EvaluationResult.NextMessage
    }
}
