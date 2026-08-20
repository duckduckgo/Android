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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pixel names reach the shared promo screen as plain strings, so these assert the exact wire names
 * and parameters this entry point fired before the screens were consolidated.
 */
class SettingsDesktopBrowserPromotionParamsTest {

    @Test
    fun whenLaunchedFromCompleteSetupCardThenDismissButtonIsOfferedAndDismissPixelIsConfigured() {
        val params = SettingsDesktopBrowserPromotionParams.forCompleteSetupCard()

        assertTrue(params.showDismissButton)
        assertEquals("m_get_desktop_browser_dismissed", params.pixels.dismissed?.pixelName)
        assertEquals(mapOf("source" to "no_thanks"), params.pixels.dismissed?.parameters)
    }

    @Test
    fun whenLaunchedFromSettingsListItemThenNoDismissButtonAndNoDismissPixel() {
        val params = SettingsDesktopBrowserPromotionParams.forSettingsListItem()

        assertFalse(params.showDismissButton)
        assertNull(params.pixels.dismissed)
    }

    @Test
    fun whenLaunchedFromEitherEntryPointThenShareAndLinkPixelsAreUnchanged() {
        listOf(
            SettingsDesktopBrowserPromotionParams.forCompleteSetupCard(),
            SettingsDesktopBrowserPromotionParams.forSettingsListItem(),
        ).forEach { params ->
            assertEquals("m_get_desktop_browser_share_download_link_click", params.pixels.shareClicked?.pixelName)
            assertEquals("m_get_desktop_browser_link_click", params.pixels.linkClicked?.pixelName)
        }
    }

    @Test
    fun whenLaunchedFromEitherEntryPointThenNoImpressionPixelIsConfigured() {
        // The impression pixel belongs to the Settings card, which fires it before this screen opens.
        assertNull(SettingsDesktopBrowserPromotionParams.forCompleteSetupCard().pixels.impression)
        assertNull(SettingsDesktopBrowserPromotionParams.forSettingsListItem().pixels.impression)
    }

    @Test
    fun whenLaunchedFromEitherEntryPointThenAttributedUrlIsUnchanged() {
        listOf(
            SettingsDesktopBrowserPromotionParams.forCompleteSetupCard(),
            SettingsDesktopBrowserPromotionParams.forSettingsListItem(),
        ).forEach { params ->
            assertEquals("https://duckduckgo.com/browser?origin=funnel_appsettings_android", params.downloadUrl)
        }
    }

    @Test
    fun whenLaunchedFromEitherEntryPointThenTheSettingsHandlerPersistsTheDismissal() {
        listOf(
            SettingsDesktopBrowserPromotionParams.forCompleteSetupCard(),
            SettingsDesktopBrowserPromotionParams.forSettingsListItem(),
        ).forEach { params ->
            assertEquals(SettingsDesktopBrowserPromotionHandler.HANDLER_ID, params.handlerId)
        }
    }

    @Test
    fun whenLaunchedFromSettingsThenCopyIsLeftToThePromoScreenDefaults() {
        val params = SettingsDesktopBrowserPromotionParams.forCompleteSetupCard()

        assertNull(params.toolbarTitle)
        assertNull(params.title)
        assertNull(params.body)
        assertEquals(0, params.illustration)
    }
}
