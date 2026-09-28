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

package com.duckduckgo.app.settings

import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.GetDesktopBrowserCompleteSetupSettings.Companion.GET_DESKTOP_BROWSER_SOURCE_PIXEL_PARAM
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionParams
import com.duckduckgo.desktopapppromotion.api.DownloadLinkConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SettingsDesktopBrowserPromotionHandler @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
) : DesktopAppPromotionInteractionHandler {

    override val handlerId: String = HANDLER_ID

    override suspend fun onInteraction(interaction: Interaction): Boolean {
        when (interaction) {
            Interaction.LINK_COPIED, Interaction.SHARE_COMPLETED, Interaction.DISMISSED -> {
                withContext(dispatchers.io()) {
                    settingsDataStore.getDesktopBrowserSettingDismissed = true
                }
            }
            Interaction.IMPRESSION, Interaction.SHARE_CLICKED -> Unit
        }

        return when (interaction) {
            Interaction.DISMISSED -> {
                pixel.fire(
                    AppPixelName.GET_DESKTOP_BROWSER_DISMISSED,
                    mapOf(GET_DESKTOP_BROWSER_SOURCE_PIXEL_PARAM to SOURCE_NO_THANKS),
                )
                true
            }
            Interaction.SHARE_CLICKED, Interaction.LINK_COPIED -> false
            Interaction.IMPRESSION, Interaction.SHARE_COMPLETED -> true
        }
    }

    companion object {
        const val HANDLER_ID = "settings_desktop_browser"
        private const val SOURCE_NO_THANKS = "no_thanks"
    }
}

/**
 * The promo screen's default copy, and its default share/link-copy pixels, are the ones Settings
 * always fired, so Settings supplies only the parts that are genuinely its own: the attributed URL,
 * whether the dismiss button is offered, and the handler that persists the dismissal and fires its
 * pixel.
 */
object SettingsDesktopBrowserPromotionParams {

    fun forCompleteSetupCard(): DesktopAppPromotionParams = DesktopAppPromotionParams(
        link = DownloadLinkConfig(downloadUrl = DOWNLOAD_URL),
        showDismissButton = true,
        handlerId = SettingsDesktopBrowserPromotionHandler.HANDLER_ID,
    )

    fun forSettingsListItem(): DesktopAppPromotionParams = DesktopAppPromotionParams(
        link = DownloadLinkConfig(downloadUrl = DOWNLOAD_URL),
        showDismissButton = false,
        handlerId = SettingsDesktopBrowserPromotionHandler.HANDLER_ID,
    )

    private const val DOWNLOAD_URL = "https://duckduckgo.com/browser?origin=funnel_appsettings_android"
}
