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

package com.duckduckgo.duckchat.impl.rmf

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.survey.api.SurveyParameterPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class LastDuckAiUsageSurveyParameterPlugin @Inject constructor(
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val currentTimeProvider: CurrentTimeProvider,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "last_duck_ai_usage"

    override suspend fun evaluate(paramKey: String): String {
        val lastUsage = duckChatFeatureRepository.lastSessionTimestamp()
        if (lastUsage == 0L) return "none"

        val daysSinceLastUsage = TimeUnit.MILLISECONDS.toDays(currentTimeProvider.currentTimeMillis() - lastUsage)

        return when {
            daysSinceLastUsage < 2 -> "day"
            daysSinceLastUsage <= 7 -> "week"
            else -> "none"
        }
    }
}
