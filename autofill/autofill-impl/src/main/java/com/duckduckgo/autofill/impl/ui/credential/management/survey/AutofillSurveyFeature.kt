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

package com.duckduckgo.autofill.impl.ui.credential.management.survey

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.InternalAlwaysEnabled
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * This is the class that represents the feature flag for showing autofill user-surveys.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    boundType = AutofillSurveysFeature::class,
    featureName = "autofillSurveys",
    settingsStore = AutofillSurveyFeatureSettingsStore::class,
)
interface AutofillSurveysFeature {
    /**
     * @return `true` when the remote config has the global "autofillSurveys" feature flag enabled
     *
     * If the remote feature is not present defaults to `false`
     */

    @InternalAlwaysEnabled
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle
}

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(AutofillSurveysFeature::class)
class AutofillSurveyFeatureSettingsStore @Inject constructor(
    private val autofillSurveyStore: AutofillSurveyStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : FeatureSettings.Store {

    override fun store(jsonString: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            autofillSurveyStore.updateAvailableSurveys(jsonString)
        }
    }
}
