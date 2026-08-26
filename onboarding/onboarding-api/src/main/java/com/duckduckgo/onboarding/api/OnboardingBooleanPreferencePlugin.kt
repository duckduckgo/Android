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

package com.duckduckgo.onboarding.api

import com.duckduckgo.common.utils.plugins.ActivePlugin

/**
 * Commits an on/off onboarding preference into the module that owns the underlying setting, so
 * onboarding can offer it without that module publishing the setting.
 *
 * Onboarding owns which preferences exist and how they are presented, so it asks for a specific
 * plugin by [Id] rather than rendering whatever happens to be contributed. The row's copy, icon,
 * position and switch position all stay on the onboarding side.
 *
 * Whether the preference can be offered at all is expressed through [isActive]: a plugin that does
 * not resolve from the plugin point means the row is not shown.
 */
interface OnboardingBooleanPreferencePlugin : ActivePlugin {
    val id: Id

    suspend fun apply(enabled: Boolean)

    enum class Id {
        AdBlocking,
    }
}
