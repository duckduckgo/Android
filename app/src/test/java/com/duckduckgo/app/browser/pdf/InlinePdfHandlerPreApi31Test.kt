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

package com.duckduckgo.app.browser.pdf

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// SDK 29 (not 30) is chosen for Robolectric Sandbox sharing with UriExtensionTest:
// loading another SDK level inflates Sandbox memory and pushes CI into OOM. Any SDK
// strictly below 31 exercises the same `Build.VERSION.SDK_INT < 31` guard, so the
// specific number doesn't change what's under test — only the class name encodes
// the production boundary.
@Config(sdk = [29])
class InlinePdfHandlerPreApi31Test {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var inlinePdfHandler: RealInlinePdfHandler
    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val cookieManagerProvider = object : CookieManagerProvider {
        override fun get(): CookieManager? = null
    }

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        inlinePdfHandler = RealInlinePdfHandler(
            context = context,
            okHttpClient = OkHttpClient(),
            cookieManagerProvider = cookieManagerProvider,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
        )
        androidBrowserConfigFeature.pdfViewer().setRawStoredState(State(enable = true))
    }

    @Test
    fun whenApiBelow31AndPdfMimeTypeThenDecisionIsFallback() {
        assertEquals(
            PdfRenderDecision.Fallback,
            inlinePdfHandler.decideForPdf("https://example.com/doc.pdf", null, "application/pdf"),
        )
    }

    @Test
    fun whenApiBelow31AndContentDispositionInlineThenDecisionIsFallback() {
        assertEquals(
            PdfRenderDecision.Fallback,
            inlinePdfHandler.decideForPdf("https://example.com/doc.pdf", "inline", "application/pdf"),
        )
    }

    @Test
    fun whenApiBelow31AndUrlEndsInPdfWithOctetMimeThenDecisionIsFallback() {
        assertEquals(
            PdfRenderDecision.Fallback,
            inlinePdfHandler.decideForPdf("https://example.com/doc.pdf", null, "application/octet-stream"),
        )
    }

    @Test
    fun whenApiBelow31AndContentDispositionAttachmentThenDecisionIsNotApplicable() {
        assertEquals(
            PdfRenderDecision.NotApplicable,
            inlinePdfHandler.decideForPdf("https://example.com/doc.pdf", "attachment; filename=doc.pdf", "application/pdf"),
        )
    }
}
