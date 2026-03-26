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

package com.duckduckgo.autofill.impl.reporting.remoteconfig

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.impl.reporting.AutofillSiteBreakageReportingDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(AutofillSiteBreakageReportingFeature::class)
class AutofillSiteBreakageReportingRemoteSettingsPersister @Inject constructor(
    private val dataStore: AutofillSiteBreakageReportingDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : FeatureSettings.Store {

    override fun store(jsonString: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            val json = JSONObject(jsonString)

            "monitorIntervalDays".let {
                if (json.has(it)) {
                    dataStore.updateMinimumNumberOfDaysBeforeReportPromptReshown(json.getInt(it))
                }
            }
        }
    }
}
