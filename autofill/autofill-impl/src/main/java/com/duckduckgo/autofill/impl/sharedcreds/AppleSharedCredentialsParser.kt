/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.sharedcreds

import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.OmnidirectionalRule
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.SharedCredentialConfig
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.UnidirectionalRule
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

interface SharedCredentialsParser {
    suspend fun read(): SharedCredentialConfig

    data class SharedCredentialConfig(
        val omnidirectionalRules: List<OmnidirectionalRule>,
        val unidirectionalRules: List<UnidirectionalRule>,
    ) {
        override fun toString(): String {
            return "SharedCredentialConfig(omnidirectionalRules=${omnidirectionalRules.size}, unidirectionalRules=${unidirectionalRules.size})"
        }
    }

    data class OmnidirectionalRule(val shared: List<ExtractedUrlParts>)
    data class UnidirectionalRule(
        val from: List<ExtractedUrlParts>,
        val to: List<ExtractedUrlParts>,
        val fromDomainsAreObsoleted: Boolean?,
    )
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppleSharedCredentialsParser @Inject constructor(
    private val jsonReader: SharedCredentialJsonReader,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider,
    private val autofillUrlMatcher: AutofillUrlMatcher,
) : SharedCredentialsParser {

    override suspend fun read(): SharedCredentialConfig {
        return withContext(dispatchers.io()) {
            runCatching {
                val json = jsonReader.read()
                convertJsonToRules(json)
            }
                .getOrElse {
                    logcat(ERROR) { "Failed to parse shared credentials json: ${it.asLog()}" }
                    CONFIG_WHEN_ERROR_HAPPENED
                }
        }
    }

    private fun convertJsonToRules(json: String?): SharedCredentialConfig {
        if (json == null) return CONFIG_WHEN_ERROR_HAPPENED

        val adapter = moshi.adapter<List<Rule>>(Types.newParameterizedType(List::class.java, Rule::class.java))

        val rules = try {
            adapter.fromJson(json) ?: return CONFIG_WHEN_ERROR_HAPPENED
        } catch (e: IOException) {
            logcat(ERROR) { "Failed to load Apple shared credential config: ${e.asLog()}" }
            return CONFIG_WHEN_ERROR_HAPPENED
        }

        val omnidirectionalRules = mutableListOf<OmnidirectionalRule>()
        val unidirectionalRules = mutableListOf<UnidirectionalRule>()

        rules.forEach { rule ->
            if (rule.shared != null) {
                processOmnidirectionalRule(rule.shared, omnidirectionalRules)
            } else if (rule.from != null && rule.to != null) {
                processUnidirectionalRule(rule.from, rule.to, unidirectionalRules, rule)
            } else {
                // not a rule we understand
                logcat(WARN) { "Could not process rule as it appears to be invalid: $rule" }
            }
        }

        return SharedCredentialConfig(
            omnidirectionalRules = omnidirectionalRules,
            unidirectionalRules = unidirectionalRules,
        )
    }

    private fun processUnidirectionalRule(
        from: List<String>,
        to: List<String>,
        unidirectionalRules: MutableList<UnidirectionalRule>,
        rule: Rule,
    ) {
        val fromList = from.map { autofillUrlMatcher.extractUrlPartsForAutofill(it) }
        val toList = to.map { autofillUrlMatcher.extractUrlPartsForAutofill(it) }
        unidirectionalRules.add(UnidirectionalRule(fromList, toList, rule.fromDomainsAreObsoleted))
    }

    private fun processOmnidirectionalRule(
        shared: List<String>,
        omnidirectionalRules: MutableList<OmnidirectionalRule>,
    ) {
        val sharedList = shared.map { autofillUrlMatcher.extractUrlPartsForAutofill(it) }
        omnidirectionalRules.add(OmnidirectionalRule(sharedList))
    }

    data class Rule(
        val shared: List<String>?,
        val from: List<String>?,
        val to: List<String>?,
        val fromDomainsAreObsoleted: Boolean?,
    )

    private companion object {
        private val CONFIG_WHEN_ERROR_HAPPENED = SharedCredentialConfig(emptyList(), emptyList())
    }
}
