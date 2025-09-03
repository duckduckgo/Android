/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.survey

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import com.duckduckgo.survey.api.SurveyParameterPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class VpnFirstUsedSurveyParameterPlugin @Inject constructor(
    private val netpCohortStore: NetpCohortStore,
    private val currentTimeProvider: CurrentTimeProvider,
) : SurveyParameterPlugin {
    override fun matches(paramKey: String): Boolean = paramKey == "vpn_first_used"

    override suspend fun evaluate(paramKey: String): String {
        val now = currentTimeProvider.localDateTimeNow()
        val days = netpCohortStore.cohortLocalDate?.let { cohortLocalDate ->
            ChronoUnit.DAYS.between(cohortLocalDate, now)
        } ?: 0
        return "$days"
    }
}
