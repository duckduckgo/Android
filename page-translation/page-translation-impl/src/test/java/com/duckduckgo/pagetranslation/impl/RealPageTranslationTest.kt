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

package com.duckduckgo.pagetranslation.impl

import com.duckduckgo.pagetranslation.api.TabContent
import org.junit.Assert.assertEquals
import org.junit.Test

class RealPageTranslationTest {

    private val preferencesStore = FakeTranslationPreferencesStore()
    private val testee: PageTranslationInternal = RealPageTranslation(preferencesStore)

    private var deviceLanguages = listOf("en")

    private fun decide(
        content: TabContent = TabContent.WebPage,
        language: String? = "es",
        bannerSuppressed: Boolean = false,
    ) = testee.decide(
        OfferInput(
            content = content,
            detection = language?.let(::Detection),
            deviceLanguages = deviceLanguages,
            bannerSuppressed = bannerSuppressed,
        ),
    ) { from, to -> to in setOf("en", "fr") && from in setOf("fr", "de", "es", "en") }

    @Test
    fun whenContentIsNotABrowserOrCustomTabWebPageThenNothing() {
        TabContent.entries.filter { it != TabContent.WebPage }.forEach {
            assertEquals(TranslationOffer.Nothing, decide(content = it))
        }
    }

    @Test
    fun whenMainFrameHasNotFinishedLoadingThenNothing() {
        assertEquals(TranslationOffer.Nothing, decide(language = null))
    }

    @Test
    fun whenDetectionIsNotReliableThenNothing() {
        deviceLanguages = listOf("en", "es")
        assertEquals(TranslationOffer.Nothing, decide(language = null))
    }

    @Test
    fun whenNoPairFromDetectedToPreferredLanguageThenNothing() {
        assertEquals(TranslationOffer.Nothing, decide(language = "ja"))
    }

    @Test
    fun whenDetectedLanguageIsSecondaryDeviceLanguageWithNoPairThenNothing() {
        deviceLanguages = listOf("en", "ja")
        assertEquals(TranslationOffer.Nothing, decide(language = "ja"))
    }

    @Test
    fun whenPairExistsOnlyToASecondaryDeviceLanguageThenNothing() {
        deviceLanguages = listOf("de", "fr")
        assertEquals(TranslationOffer.Nothing, decide(language = "es"))
    }

    @Test
    fun whenDetectedLanguageIsNotADeviceLanguageThenBannerAndMenuRow() {
        assertEquals(TranslationOffer.BannerAndMenuRow, decide())
    }

    @Test
    fun whenDetectedLanguageIsASecondaryDeviceLanguageThenMenuRowOnly() {
        deviceLanguages = listOf("en", "fr")
        assertEquals(TranslationOffer.MenuRowOnly, decide(language = "fr"))
    }

    @Test
    fun whenDetectedLanguageIsThePreferredDeviceLanguageThenNothing() {
        deviceLanguages = listOf("en", "fr")
        assertEquals(TranslationOffer.Nothing, decide(language = "en"))
    }

    @Test
    fun whenFirstDeviceLanguageIsPreferredThenItIsTheTranslationTarget() {
        deviceLanguages = listOf("fr-FR", "en-US")
        assertEquals(TranslationOffer.Nothing, decide(language = "fr"))
        assertEquals(TranslationOffer.MenuRowOnly, decide(language = "en"))
    }

    @Test
    fun whenThereAreNoDeviceLanguagesThenNothing() {
        deviceLanguages = emptyList()
        assertEquals(TranslationOffer.Nothing, decide())
    }

    @Test
    fun whenRegionalVariantsDifferThenOnlyBaseLanguageIsCompared() {
        deviceLanguages = listOf("en_US")
        assertEquals(TranslationOffer.Nothing, decide(language = "en-GB"))
        deviceLanguages = listOf("en-US", "fr-FR")
        assertEquals(TranslationOffer.MenuRowOnly, decide(language = "FR-ca"))
    }

    @Test
    fun whenAskToTranslatePagesIsOffThenMenuRowOnly() {
        preferencesStore.askToTranslatePages = false
        assertEquals(TranslationOffer.MenuRowOnly, decide())
    }

    @Test
    fun whenAskToTranslatePagesIsOffAndLanguageIsSecondaryDeviceLanguageThenMenuRowOnly() {
        preferencesStore.askToTranslatePages = false
        deviceLanguages = listOf("en", "fr")
        assertEquals(TranslationOffer.MenuRowOnly, decide(language = "fr"))
    }

    @Test
    fun whenBannerIsSuppressedAfterShowOriginalThenMenuRowOnly() {
        assertEquals(TranslationOffer.MenuRowOnly, decide(bannerSuppressed = true))
    }

    @Test
    fun whenBannerIsSuppressedAfterFailedTranslationThenMenuRowOnly() {
        assertEquals(TranslationOffer.MenuRowOnly, decide(bannerSuppressed = true, language = "de"))
    }

    @Test
    fun whenBannerIsSuppressedAndLanguageIsSecondaryDeviceLanguageThenMenuRowOnly() {
        deviceLanguages = listOf("en", "fr")
        assertEquals(TranslationOffer.MenuRowOnly, decide(bannerSuppressed = true, language = "fr"))
    }

    @Test
    fun whenDetectionIsReliableButNoPairIsSupportedThenNeitherBannerNorMenuRow() {
        assertEquals(TranslationOffer.Nothing, decide(language = "ja"))
        preferencesStore.askToTranslatePages = false
        assertEquals(TranslationOffer.Nothing, decide(language = "ja"))
    }
}

private class FakeTranslationPreferencesStore : TranslationPreferencesStore {
    override var askToTranslatePages: Boolean = true
}
