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
 * Supplies the options for a single-choice onboarding step. The module that owns the underlying
 * setting keeps it, and onboarding only renders the choice and commits the pick.
 *
 * Onboarding owns which steps exist, so it asks for a specific provider by [Id] rather than
 * rendering whatever happens to be contributed.
 */
interface OnboardingSingleChoiceDataPlugin : ActivePlugin {
    val id: Id

    /** Warms up whatever [options] reads. Called while the step is still several screens away. */
    suspend fun prefetch()

    /** Display order, first entry is the default. Empty when the choice is unavailable. */
    suspend fun options(): List<Option>

    suspend fun apply(option: Option)

    enum class Id {
        DuckAiModelProvider,
    }

    /**
     * An interface rather than a data class so a plugin can carry its own payload from [options]
     * into [apply] without publishing it here.
     */
    interface Option {
        /**
         * Stable identifier. Used as the pixel value for the step, so it must not change once
         * shipped and must be safe to send.
         */
        val id: String
        val label: String

        @get:DrawableRes
        val iconRes: Int
    }
}
