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

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * This is the class that represents the feature flag for offering to report Autofill breakages
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    boundType = AutofillSiteBreakageReportingFeature::class,
    featureName = "autofillBreakageReporter",
    settingsStore = AutofillSiteBreakageReportingRemoteSettingsPersister::class,
)
interface AutofillSiteBreakageReportingFeature {
    /**
     * @return `true` when the remote config has the global "autofillBreakageReporter" feature flag enabled, and always true for internal builds
     *
     * If the remote feature is not present defaults to `false`
     */

    @Toggle.InternalAlwaysEnabled
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle
}
