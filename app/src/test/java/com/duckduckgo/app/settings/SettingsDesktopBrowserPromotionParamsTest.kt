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
 * Pixels are no longer part of this params object — they're fired by [SettingsDesktopBrowserPromotionHandler],
 * covered by its own test — so these assert the surface that's still genuinely Settings' own.
 */
class SettingsDesktopBrowserPromotionParamsTest {

    @Test
    fun whenLaunchedFromCompleteSetupCardThenDismissButtonIsOffered() {
        val params = SettingsDesktopBrowserPromotionParams.forCompleteSetupCard()

        assertTrue(params.showDismissButton)
    }

    @Test
    fun whenLaunchedFromSettingsListItemThenNoDismissButton() {
        val params = SettingsDesktopBrowserPromotionParams.forSettingsListItem()

        assertFalse(params.showDismissButton)
    }

    @Test
    fun whenLaunchedFromEitherEntryPointThenAttributedUrlIsUnchanged() {
        listOf(
            SettingsDesktopBrowserPromotionParams.forCompleteSetupCard(),
            SettingsDesktopBrowserPromotionParams.forSettingsListItem(),
        ).forEach { params ->
            assertEquals("https://duckduckgo.com/browser?origin=funnel_appsettings_android", params.link.downloadUrl)
        }
    }

    @Test
    fun whenLaunchedFromEitherEntryPointThenTheSettingsHandlerReceivesInteractions() {
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
        assertNull(params.illustration)
    }
}
