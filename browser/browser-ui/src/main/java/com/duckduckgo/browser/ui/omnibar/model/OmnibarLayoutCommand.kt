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

package com.duckduckgo.browser.ui.omnibar.model

import com.duckduckgo.app.trackerdetection.model.Entity

sealed class OmnibarLayoutCommand {
    data object CancelAnimations : OmnibarLayoutCommand()
    data class StartTrackersAnimation(
        val entities: List<Entity>?,
        val isCustomTab: Boolean,
        val isAddressBarTrackersAnimationEnabled: Boolean,
    ) : OmnibarLayoutCommand()

    data class StartCookiesAnimation(val isCosmetic: Boolean) : OmnibarLayoutCommand()
    data object MoveCaretToFront : OmnibarLayoutCommand()
    data class LaunchInputScreen(val query: String) : OmnibarLayoutCommand()
    data class EasterEggLogoClicked(val url: String) : OmnibarLayoutCommand()
    data object FocusInputField : OmnibarLayoutCommand()
    data object CancelEasterEggLogoAnimation : OmnibarLayoutCommand()
}
