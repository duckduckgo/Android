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

import androidx.annotation.DrawableRes
import com.duckduckgo.common.utils.plugins.ActivePlugin

/**
 * Describes and commits an on/off onboarding preference from the module that owns the underlying
 * setting, so onboarding can offer it without that module publishing the setting.
 *
 * Onboarding owns which preferences exist, so it asks for a specific plugin by [Id] rather than
 * rendering whatever happens to be contributed. The module owning the setting names and illustrates
 * it; where the row sits and where its switch sits stay on the onboarding side.
 *
 * Whether the preference can be offered at all is expressed through [isActive]: a plugin that does
 * not resolve from the plugin point means the row is not shown.
 */
interface OnboardingBooleanPreferencePlugin : ActivePlugin {
    val id: Id

    val primaryText: String

    /** Null when the row renders as a single line. */
    val secondaryText: String? get() = null

    /** Null when the row renders without an icon. */
    @get:DrawableRes
    val iconRes: Int? get() = null

    suspend fun apply(enabled: Boolean)

    enum class Id {
        AdBlocking,
    }
}
