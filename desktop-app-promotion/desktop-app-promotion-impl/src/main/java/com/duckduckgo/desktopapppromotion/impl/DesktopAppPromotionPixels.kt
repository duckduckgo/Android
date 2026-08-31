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

package com.duckduckgo.desktopapppromotion.impl

import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * The screen's own default pixels, fired parameterless when the caller's `PixelConfig` leaves
 * `shareClicked`/`linkClicked` unset. The wire names predate the shared screen — they are what the
 * Settings-owned screen always fired — so they keep their existing registry entries.
 */
object DesktopAppPromotionPixels {
    const val SHARE_DOWNLOAD_LINK_CLICK = "m_get_desktop_browser_share_download_link_click"
    const val LINK_CLICK = "m_get_desktop_browser_link_click"
}

@ContributesMultibinding(AppScope::class)
class DesktopAppPromotionPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            DesktopAppPromotionPixels.SHARE_DOWNLOAD_LINK_CLICK to PixelParameter.removeAtb(),
            DesktopAppPromotionPixels.LINK_CLICK to PixelParameter.removeAtb(),
        )
    }
}
