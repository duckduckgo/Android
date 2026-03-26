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

package com.duckduckgo.autoconsent.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class AutoconsentReloadLoopDetectorTest {

    private val mockPixelManager: AutoconsentPixelManager = mock()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val webView: WebView = WebView(context)
    private val detector = AutoconsentReloadLoopDetector(mockPixelManager)

    @Test
    fun whenNoInteractionThenNoReloadLoopDetected() {
        assertFalse(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenSameCmpHandledAndDetectedOnSameUrlThenReloadLoopDetected() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "testCmp")

        assertTrue(detector.isReloadLoopDetected(webView))
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_ERROR_RELOAD_LOOP_DAILY)
    }

    @Test
    fun whenUrlChangesThenReloadLoopStateCleared() {
        detector.updateUrl(webView, "https://example.com/page1")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        detector.updateUrl(webView, "https://example.com/page2")
        detector.detectReloadLoop(webView, "testCmp")

        assertFalse(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenHostChangesThenReloadLoopStateCleared() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        detector.updateUrl(webView, "https://other.com/page")
        detector.detectReloadLoop(webView, "testCmp")

        assertFalse(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenSchemeChangesThenReloadLoopStateCleared() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        detector.updateUrl(webView, "http://example.com/page")
        detector.detectReloadLoop(webView, "testCmp")

        assertFalse(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenQueryParamChangesOnSamePageThenReloadLoopStillDetected() {
        detector.updateUrl(webView, "https://example.com/page?a=1")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        detector.updateUrl(webView, "https://example.com/page?b=2")
        detector.detectReloadLoop(webView, "testCmp")

        assertTrue(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenCosmeticRuleHandledThenReloadLoopStateCleared() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        detector.rememberLastHandledCMP(webView, "cosmeticCmp", isCosmetic = true)
        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "testCmp")

        assertFalse(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenDifferentCmpHandledThenReloadLoopStateCleared() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "cmp1", isCosmetic = false)
        detector.rememberLastHandledCMP(webView, "cmp2", isCosmetic = false)
        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "cmp1")

        assertFalse(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenDifferentCmpDetectedFromLastHandledThenNoLoop() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "cmp1", isCosmetic = false)
        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "cmp2")

        assertFalse(detector.isReloadLoopDetected(webView))
    }

    @Test
    fun whenReloadLoopAlreadyDetectedThenPixelNotFiredAgain() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)

        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "testCmp")
        detector.detectReloadLoop(webView, "testCmp")

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_ERROR_RELOAD_LOOP_DAILY)
    }

    @Test
    fun whenNoCmpHandledThenDetectReloadLoopDoesNothing() {
        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "testCmp")

        assertFalse(detector.isReloadLoopDetected(webView))
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_ERROR_RELOAD_LOOP_DAILY)
    }

    @Test
    fun whenMultipleWebViewsThenStatesAreIndependent() {
        val webView2 = WebView(context)

        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "testCmp")

        detector.updateUrl(webView2, "https://example.com/page")

        assertTrue(detector.isReloadLoopDetected(webView))
        assertFalse(detector.isReloadLoopDetected(webView2))
    }

    @Test
    fun whenFullReloadLoopSequenceThenAutoActionDisabled() {
        // Simulate: load -> handle -> reload -> detect loop -> reload again -> loop flag still set
        detector.updateUrl(webView, "https://example.com/page")
        detector.rememberLastHandledCMP(webView, "testCmp", isCosmetic = false)
        assertFalse(detector.isReloadLoopDetected(webView))

        detector.updateUrl(webView, "https://example.com/page")
        detector.detectReloadLoop(webView, "testCmp")
        assertTrue(detector.isReloadLoopDetected(webView))

        detector.updateUrl(webView, "https://example.com/page")
        assertTrue(detector.isReloadLoopDetected(webView))
    }
}
