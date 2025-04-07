/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.senseofprotection

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "senseOfProtection",
)
interface SenseOfProtectionToggles {

    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun senseOfProtectionNewUserExperimentApr25(): Toggle

    @Toggle.DefaultValue(false)
    fun senseOfProtectionExistingUserExperimentApr25(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        MODIFIED_CONTROL("modifiedControl"), // without grey tracker logos from original animation
        VARIANT_1("variant1"), // Persistent Green Shield + X Trackers Blocked animation
        VARIANT_2("variant2"), // Persistent Green Shield + X Trackers Blocked animation + TabSwitcher animation
    }
}
