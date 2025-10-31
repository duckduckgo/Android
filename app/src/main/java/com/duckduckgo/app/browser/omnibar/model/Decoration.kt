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

package com.duckduckgo.app.browser.omnibar.model

import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.trackerdetection.model.Entity

sealed class Decoration {
    data class Mode(
        val viewMode: Omnibar.ViewMode,
    ) : Decoration()

    data class LaunchTrackersAnimation(
        val entities: List<Entity>?,
    ) : Decoration()

    data class LaunchCookiesAnimation(
        val isCosmetic: Boolean,
    ) : Decoration()

    data class QueueCookiesAnimation(
        val isCosmetic: Boolean,
    ) : Decoration()

    data object CancelAnimations : Decoration()

    data class ChangeCustomTabTitle(
        val title: String,
        val domain: String?,
        val showDuckPlayerIcon: Boolean,
    ) : Decoration()

    data class PrivacyShieldChanged(
        val privacyShield: PrivacyShield,
    ) : Decoration()

    data class HighlightOmnibarItem(
        val fireButton: Boolean,
        val privacyShield: Boolean,
    ) : Decoration()

    data class DisableVoiceSearch(
        val url: String,
    ) : Decoration()
}
