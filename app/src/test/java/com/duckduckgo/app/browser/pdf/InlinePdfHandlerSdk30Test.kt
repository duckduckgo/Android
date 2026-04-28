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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class InlinePdfHandlerSdk30Test {

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
    fun whenApiBelow31AndPdfMimeTypeThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/pdf"))
    }

    @Test
    fun whenApiBelow31AndContentDispositionInlineThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", "inline", "application/pdf"))
    }

    @Test
    fun whenApiBelow31AndUrlEndsInPdfWithOctetMimeThenShouldNotRenderInline() {
        assertFalse(inlinePdfHandler.shouldRenderPdfInline("https://example.com/doc.pdf", null, "application/octet-stream"))
    }
}
