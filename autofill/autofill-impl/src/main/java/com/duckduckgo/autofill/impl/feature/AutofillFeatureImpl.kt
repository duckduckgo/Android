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

package com.duckduckgo.autofill.impl.feature

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.feature.AutofillFeatureToggle
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName
import com.duckduckgo.autofill.impl.feature.plugin.getAutofillSubfeatureElement
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggleRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(
    scope = AppScope::class,
    boundType = AutofillFeatureToggle::class,
)
@SingleInstanceIn(AppScope::class)
class AutofillFeatureImpl @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val repository: AutofillFeatureToggleRepository,
) : AutofillFeatureToggle {

    override fun isFeatureEnabled(
        featureName: AutofillSubfeatureName,
        defaultValue: Boolean,
    ): Boolean {
        val autofillElement = getAutofillSubfeatureElement(featureName.value) ?: return defaultValue
        return repository.get(autofillElement, defaultValue) &&
            appBuildConfig.versionCode >= repository.getMinSupportedVersion(autofillElement)
    }
}
