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

package com.duckduckgo.pagetranslation.impl.offer

import com.duckduckgo.pagetranslation.api.TabContent
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import com.duckduckgo.pagetranslation.impl.store.TranslationPreferencesStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealTranslationOfferRulesTest {

    private val preferencesStore: TranslationPreferencesStore = mock()
    private val deviceLanguages: DeviceLanguages = mock()
    private val manifestStore: ModelsManifestStore = mock()
    private val testee = RealTranslationOfferRules(deviceLanguages, preferencesStore, manifestStore)

    private val pairs = setOf("fr-en", "de-en", "es-en", "en-fr", "en-en", "de-fr", "es-fr", "fr-fr")

    @Before
    fun setUp() {
        whenever(preferencesStore.askToTranslatePages).thenReturn(flowOf(true))
        whenever(deviceLanguages.get()).thenReturn(listOf("en"))
    }

    private suspend fun decide(
        content: TabContent = TabContent.WebPage,
        detection: Detection? = Detection("es", isReliable = true),
        bannerSuppressed: Boolean = false,
        availablePairs: Set<String>? = pairs,
    ): TranslationOffer {
        whenever(manifestStore.readManifest()).thenReturn(availablePairs?.let(::manifestOf))
        return testee.decide(content, detection, bannerSuppressed)
    }

    private fun manifestOf(pairs: Set<String>) = ModelsManifest(
        schemaVersion = 1,
        baseUrl = "https://example.com",
        models = pairs.associateWith { pair ->
            ModelsManifest.TranslationModel(
                sourceLanguage = pair.substringBefore('-'),
                targetLanguage = pair.substringAfter('-'),
                version = "1",
                architecture = "base",
                totalBytes = 1L,
                files = emptyMap(),
            )
        },
    )

    @Test
    fun whenContentIsNotABrowserOrCustomTabWebPageThenNone() = runTest {
        TabContent.entries.filter { it != TabContent.WebPage }.forEach {
            assertEquals(TranslationOffer.None, decide(content = it))
        }
    }

    @Test
    fun whenMainFrameHasNotFinishedLoadingThenNone() = runTest {
        assertEquals(TranslationOffer.None, decide(detection = null))
    }

    @Test
    fun whenDetectionIsNotReliableThenNone() = runTest {
        assertEquals(TranslationOffer.None, decide(detection = Detection("es", isReliable = false)))
    }

    @Test
    fun whenNoPairFromDetectedToPreferredLanguageThenNone() = runTest {
        assertEquals(TranslationOffer.None, decide(detection = Detection("ja", isReliable = true)))
    }

    @Test
    fun whenManifestIsNotAvailableThenNone() = runTest {
        assertEquals(TranslationOffer.None, decide(availablePairs = null))
    }

    @Test
    fun whenManifestHasNoModelsThenNone() = runTest {
        assertEquals(TranslationOffer.None, decide(availablePairs = emptySet()))
    }

    @Test
    fun whenPairIsInTheOppositeDirectionOnlyThenNone() = runTest {
        assertEquals(TranslationOffer.None, decide(availablePairs = setOf("en-es")))
    }

    @Test
    fun whenDetectedLanguageIsSecondaryDeviceLanguageWithNoPairThenNone() = runTest {
        whenever(deviceLanguages.get()).thenReturn(listOf("en", "ja"))
        assertEquals(TranslationOffer.None, decide(detection = Detection("ja", isReliable = true)))
    }

    @Test
    fun whenPairExistsOnlyToASecondaryDeviceLanguageThenNone() = runTest {
        whenever(deviceLanguages.get()).thenReturn(listOf("de", "fr"))
        assertEquals(TranslationOffer.None, decide(detection = Detection("es", isReliable = true), availablePairs = setOf("es-fr")))
    }

    @Test
    fun whenDetectedLanguageIsNotADeviceLanguageThenBannerAndMenuRow() = runTest {
        assertEquals(TranslationOffer.BannerAndMenuRow, decide())
    }

    @Test
    fun whenDetectedLanguageIsASecondaryDeviceLanguageThenMenuRowOnly() = runTest {
        whenever(deviceLanguages.get()).thenReturn(listOf("en", "fr"))
        assertEquals(TranslationOffer.MenuRowOnly, decide(detection = Detection("fr", isReliable = true)))
    }

    @Test
    fun whenDetectedLanguageIsThePreferredDeviceLanguageThenNone() = runTest {
        whenever(deviceLanguages.get()).thenReturn(listOf("en", "fr"))
        assertEquals(TranslationOffer.None, decide(detection = Detection("en", isReliable = true)))
    }

    @Test
    fun whenFirstDeviceLanguageIsPreferredThenItIsTheTranslationTarget() = runTest {
        whenever(deviceLanguages.get()).thenReturn(listOf("fr-FR", "en-US"))
        assertEquals(TranslationOffer.None, decide(detection = Detection("fr", isReliable = true)))
        assertEquals(TranslationOffer.MenuRowOnly, decide(detection = Detection("en", isReliable = true)))
    }

    @Test
    fun whenThereAreNoDeviceLanguagesThenNone() = runTest {
        whenever(deviceLanguages.get()).thenReturn(emptyList())
        assertEquals(TranslationOffer.None, decide())
    }

    @Test
    fun whenDeviceLanguagesAreRegionalThenOnlyTheBaseLanguageIsCompared() = runTest {
        whenever(deviceLanguages.get()).thenReturn(listOf("en_US"))
        assertEquals(TranslationOffer.None, decide(detection = Detection("en", isReliable = true)))
        whenever(deviceLanguages.get()).thenReturn(listOf("pt-BR", "FR-ca"))
        assertEquals(TranslationOffer.MenuRowOnly, decide(detection = Detection("fr", isReliable = true), availablePairs = setOf("fr-pt")))
    }

    @Test
    fun whenAskToTranslatePagesIsOffThenMenuRowOnly() = runTest {
        whenever(preferencesStore.askToTranslatePages).thenReturn(flowOf(false))
        assertEquals(TranslationOffer.MenuRowOnly, decide())
    }

    @Test
    fun whenAskToTranslatePagesIsOffAndLanguageIsSecondaryDeviceLanguageThenMenuRowOnly() = runTest {
        whenever(preferencesStore.askToTranslatePages).thenReturn(flowOf(false))
        whenever(deviceLanguages.get()).thenReturn(listOf("en", "fr"))
        assertEquals(TranslationOffer.MenuRowOnly, decide(detection = Detection("fr", isReliable = true)))
    }

    @Test
    fun whenBannerIsSuppressedAfterShowOriginalThenMenuRowOnly() = runTest {
        assertEquals(TranslationOffer.MenuRowOnly, decide(bannerSuppressed = true))
    }

    @Test
    fun whenBannerIsSuppressedAfterFailedTranslationThenMenuRowOnly() = runTest {
        assertEquals(TranslationOffer.MenuRowOnly, decide(bannerSuppressed = true, detection = Detection("de", isReliable = true)))
    }

    @Test
    fun whenBannerIsSuppressedAndLanguageIsSecondaryDeviceLanguageThenMenuRowOnly() = runTest {
        whenever(deviceLanguages.get()).thenReturn(listOf("en", "fr"))
        assertEquals(TranslationOffer.MenuRowOnly, decide(bannerSuppressed = true, detection = Detection("fr", isReliable = true)))
    }

    @Test
    fun whenDetectionIsReliableButNoPairIsSupportedThenNeitherBannerNorMenuRow() = runTest {
        assertEquals(TranslationOffer.None, decide(detection = Detection("ja", isReliable = true)))
        whenever(preferencesStore.askToTranslatePages).thenReturn(flowOf(false))
        assertEquals(TranslationOffer.None, decide(detection = Detection("ja", isReliable = true)))
    }
}
