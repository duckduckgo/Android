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

import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.matchers.EvaluationResult
import com.duckduckgo.remote.messaging.impl.matchers.toResult
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.duckduckgo.remote.messaging.impl.models.Rule
import com.duckduckgo.remote.messaging.impl.models.Unknown
import com.duckduckgo.remote.messaging.store.RemoteMessagingCohortStore
import logcat.LogPriority.INFO
import logcat.logcat

class RemoteMessagingConfigMatcher(
    private val matchers: Set<AttributeMatcherPlugin>,
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val remoteMessagingCohortStore: RemoteMessagingCohortStore,
) {

    suspend fun evaluate(remoteConfig: RemoteConfig): RemoteMessage? {
        val rules = remoteConfig.rules
        val dismissedMessages = remoteMessagingRepository.dismissedMessages()

        remoteConfig.messages.filter { !dismissedMessages.contains(it.id) }.forEach { message ->
            val matchingRules = if (message.matchingRules.isEmpty() && message.exclusionRules.isEmpty()) {
                val processed = filterCardsListMessage(message, rules)
                if (processed != null) return processed
                return@forEach
            } else {
                message.matchingRules
            }

            val matchingResult = matchingRules.evaluateMatchingRules(message.id, rules)
            val excludeResult = message.exclusionRules.evaluateExclusionRules(message.id, rules)

            if (matchingResult == EvaluationResult.Match && excludeResult == EvaluationResult.Fail) {
                val processed = filterCardsListMessage(message, rules)
                if (processed != null) return processed
                return@forEach
            }
        }

        return null
    }

    private suspend fun filterCardsListMessage(
        message: RemoteMessage,
        rules: List<Rule>,
    ): RemoteMessage? {
        val cardsList = message.content as? Content.CardsList ?: return message

        // First pass: filter list items based on matching/exclusion rules
        val filteredListItems = cardsList.listItems.filter { cardItem ->
            if (cardItem !is CardItem.ListItem) {
                true
            } else if (cardItem.matchingRules.isEmpty() && cardItem.exclusionRules.isEmpty()) {
                true
            } else {
                // Evaluate CardItem rules following the same logic as for remote message
                val itemMatching = if (cardItem.matchingRules.isEmpty()) {
                    EvaluationResult.Match
                } else {
                    cardItem.matchingRules.evaluateMatchingRules(cardItem.id, rules)
                }

                val itemExclusion = if (cardItem.exclusionRules.isEmpty()) {
                    EvaluationResult.Fail
                } else {
                    cardItem.exclusionRules.evaluateExclusionRules(cardItem.id, rules)
                }

                itemMatching == EvaluationResult.Match && itemExclusion == EvaluationResult.Fail
            }
        }

        // Second pass: filter section titles that have no remaining items
        val remainingItemIds = filteredListItems.filterIsInstance<CardItem.ListItem>().map { it.id }.toSet()
        val filteredItems = filteredListItems.filter { cardItem ->
            if (cardItem is CardItem.SectionTitle) {
                cardItem.itemIDs.any { it in remainingItemIds }
            } else {
                true
            }
        }

        if (filteredItems.isEmpty()) {
            logcat(INFO) { "RMF: All CardItems filtered out for message ${message.id}. Nothing to display." }
            return null
        }

        logcat(INFO) { "RMF: Filtered ${cardsList.listItems.size - filteredItems.size} CardItems for message ${message.id}." }
        return message.copy(content = cardsList.copy(listItems = filteredItems))
    }

    private suspend fun Iterable<Int>.evaluateMatchingRules(
        messageId: String,
        rules: List<Rule>,
    ): EvaluationResult {
        var result: EvaluationResult = EvaluationResult.Match

        for (ruleId in this) {
            val rule = rules.find { it.id == ruleId } ?: return EvaluationResult.NextMessage
            result = EvaluationResult.Match

            if (rule.targetPercentile != null && remoteMessagingCohortStore.getPercentile(messageId) > rule.targetPercentile.before) {
                logcat(INFO) { "RMF: percentile check failed" }
                return EvaluationResult.Fail
            }

            for (attr in rule.attributes) {
                result = evaluateAttribute(attr)
                if (result == EvaluationResult.Fail || result == EvaluationResult.NextMessage) {
                    logcat(INFO) { "RMF: first failed attribute $attr" }
                    break
                }
            }

            if (result == EvaluationResult.NextMessage || result == EvaluationResult.Match) return result
        }

        return result
    }

    private suspend fun Iterable<Int>.evaluateExclusionRules(messageId: String, rules: List<Rule>): EvaluationResult {
        var result: EvaluationResult = EvaluationResult.Fail

        for (ruleId in this) {
            val rule = rules.find { it.id == ruleId } ?: return EvaluationResult.NextMessage
            result = EvaluationResult.Fail

            if (rule.targetPercentile != null && remoteMessagingCohortStore.getPercentile(messageId) > rule.targetPercentile.before) {
                logcat(INFO) { "RMF: percentile check failed" }
                return EvaluationResult.Fail
            }

            for (attr in rule.attributes) {
                result = evaluateAttribute(attr)
                if (result == EvaluationResult.Fail || result == EvaluationResult.NextMessage) {
                    logcat(INFO) { "RMF: first failed attribute $attr" }
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
                if (result != null) return EvaluationResult.fromBoolean(result)
            }
        }

        return EvaluationResult.NextMessage
    }
}
