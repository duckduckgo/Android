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

package com.duckduckgo.autofill.impl.reporting

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.duckduckgo.autofill.impl.reporting.remoteconfig.AutofillSiteBreakageReporting
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

interface AutofillSiteBreakageReportingDataStore {

    suspend fun getMinimumNumberOfDaysBeforeReportPromptReshown(): Int
    suspend fun updateMinimumNumberOfDaysBeforeReportPromptReshown(newValue: Int)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AutofillSiteBreakageReportingDataStoreImpl @Inject constructor(
    @AutofillSiteBreakageReporting private val store: DataStore<Preferences>,
) : AutofillSiteBreakageReportingDataStore {

    private val daysBeforePromptShownAgainKey = intPreferencesKey("days_before_prompt_shown_again")

    override suspend fun updateMinimumNumberOfDaysBeforeReportPromptReshown(newValue: Int) {
        store.edit {
            it[daysBeforePromptShownAgainKey] = newValue
        }
    }

    override suspend fun getMinimumNumberOfDaysBeforeReportPromptReshown(): Int {
        return store.data.firstOrNull()?.get(daysBeforePromptShownAgainKey) ?: DEFAULT_NUMBER_OF_DAYS_BEFORE_REPORT_PROMPT_RESHOWN
    }

    companion object {
        private const val DEFAULT_NUMBER_OF_DAYS_BEFORE_REPORT_PROMPT_RESHOWN = 42
    }
}
