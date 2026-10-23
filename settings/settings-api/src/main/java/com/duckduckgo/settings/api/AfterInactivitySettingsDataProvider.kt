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

package com.duckduckgo.settings.api

import kotlinx.coroutines.flow.Flow

interface AfterInactivitySettingsDataProvider {
    val settings: Flow<AfterInactivitySettings>

    suspend fun setDestination(destination: AfterInactivityReturnDestination)
}

sealed interface AfterInactivitySettings {
    data object LastUsedTab : AfterInactivitySettings

    data class NewTabPage(
        val effectiveTimeoutSeconds: Long,
        val returnToLastTabShortcutEnabled: Boolean,
    ) : AfterInactivitySettings

    data class SpecificPage(
        val url: String,
        val effectiveTimeoutSeconds: Long,
    ) : AfterInactivitySettings
}

sealed interface AfterInactivityReturnDestination {
    data object LastUsedTab : AfterInactivityReturnDestination

    data class NewTabPage(
        val selectedTimeoutSeconds: Long? = null,
        val returnToLastTabShortcutEnabled: Boolean? = null,
    ) : AfterInactivityReturnDestination

    data class SpecificPage(
        val url: String = DEFAULT_URL,
        val selectedTimeoutSeconds: Long? = null,
    ) : AfterInactivityReturnDestination {
        companion object {
            const val DEFAULT_URL = "https://duckduckgo.com/"
        }
    }
}
