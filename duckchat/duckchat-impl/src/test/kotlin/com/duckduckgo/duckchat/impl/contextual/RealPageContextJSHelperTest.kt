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

package com.duckduckgo.duckchat.impl.contextual

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.ContextType
import com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.Trigger
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class RealPageContextJSHelperTest {

    private val testee = RealPageContextJSHelper(
        org.mockito.kotlin.mock(),
        org.mockito.kotlin.mock { on { pdfPageContext() } doReturn org.mockito.kotlin.mock() },
        org.mockito.kotlin.mock(),
        org.mockito.kotlin.mock(),
    )

    @Test
    fun `successful markdown collection reports markdown carrier`() = runTest {
        val pixels = org.mockito.kotlin.mock<com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels>()
        val toggle = org.mockito.kotlin.mock<com.duckduckgo.feature.toggles.api.Toggle> { on { isEnabled() } doReturn true }
        val feature = org.mockito.kotlin.mock<com.duckduckgo.duckchat.impl.feature.DuckChatFeature> {
            on { pdfPageContext() } doReturn toggle
        }
        val dispatchers = org.mockito.kotlin.mock<com.duckduckgo.common.utils.DispatcherProvider>()
        org.mockito.kotlin.whenever(dispatchers.io()).thenReturn(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        val helper = RealPageContextJSHelper(pixels, feature, backgroundScope, dispatchers)
        helper.collectPageContext("tab")
        helper.processPageContext(
            "pageContext",
            "collectionResult",
            JSONObject().put(
                "serializedPageData",
                """{"title":"Page","content":"Markdown"}""",
            ),
            "tab",
        )
        org.mockito.kotlin.verify(pixels).success(com.duckduckgo.duckchat.impl.pixel.PageContextExtractionPixels.ContextType.MARKDOWN)
    }

    @Test
    fun `collect while Duck ai is not visible reports no outcome`() = runTest {
        val pixels = mock<PageContextExtractionPixels>()
        val helper = reportingHelper(pixels, backgroundScope, testScheduler)

        helper.collectPageContext("tab", Trigger.NAVIGATION, navigationId = 1L, isDuckAiVisible = false)
        helper.deliverMarkdown("tab")
        helper.collectPageContext("tab", Trigger.NAVIGATION, navigationId = 2L, isDuckAiVisible = false)
        advanceTimeBy(31_000)

        verifyNoInteractions(pixels)
    }

    @Test
    fun `outcomes for the same navigation are reported once`() = runTest {
        val pixels = mock<PageContextExtractionPixels>()
        val helper = reportingHelper(pixels, backgroundScope, testScheduler)

        helper.collectPageContext("tab", Trigger.NAVIGATION, navigationId = 1L)
        helper.deliverMarkdown("tab")
        helper.collectPageContext("tab", Trigger.NAVIGATION, navigationId = 1L)
        helper.deliverMarkdown("tab")
        helper.collectPageContext("tab", Trigger.NAVIGATION, navigationId = 1L)
        advanceTimeBy(31_000)

        verify(pixels, times(1)).success(ContextType.MARKDOWN)
        verifyNoMoreInteractions(pixels)
    }

    @Test
    fun `a new navigation reports its outcome again`() = runTest {
        val pixels = mock<PageContextExtractionPixels>()
        val helper = reportingHelper(pixels, backgroundScope, testScheduler)

        helper.collectPageContext("tab", Trigger.NAVIGATION, navigationId = 1L)
        helper.deliverMarkdown("tab")
        helper.collectPageContext("tab", Trigger.NAVIGATION, navigationId = 2L)
        helper.deliverMarkdown("tab")

        verify(pixels, times(2)).success(ContextType.MARKDOWN)
    }

    private fun reportingHelper(
        pixels: PageContextExtractionPixels,
        scope: CoroutineScope,
        scheduler: TestCoroutineScheduler,
    ): RealPageContextJSHelper {
        val toggle = mock<Toggle> { on { isEnabled() } doReturn true }
        val feature = mock<DuckChatFeature> { on { pdfPageContext() } doReturn toggle }
        val dispatchers = mock<DispatcherProvider> { on { io() } doReturn UnconfinedTestDispatcher(scheduler) }
        return RealPageContextJSHelper(pixels, feature, scope, dispatchers)
    }

    private suspend fun RealPageContextJSHelper.deliverMarkdown(tabId: String) {
        processPageContext(
            "pageContext",
            "collectionResult",
            JSONObject().put("serializedPageData", """{"title":"Page","content":"Markdown"}"""),
            tabId,
        )
    }

    @Test
    fun whenNonCollectionResultMethodThenReturnsNull() = runTest {
        val result =
            testee.processPageContext(
                featureName = "pageContext",
                method = "otherMethod",
                data = JSONObject("""{"serializedPageData":"ignored"}"""),
                tabId = "tab-1",
            )

        assertNull(result)
    }

    @Test
    fun whenCollectionResultMissingDataThenReturnsNull() = runTest {
        val result =
            testee.processPageContext(
                featureName = "pageContext",
                method = "collectionResult",
                data = JSONObject("""{"foo":"bar"}"""),
                tabId = "tab-1",
            )

        assertNull(result)
    }

    @Test
    fun whenCollectionResultHasSerializedPageDataThenReturnsValue() = runTest {
        val serialized = """{"title":"Example"}"""

        val result =
            testee.processPageContext(
                featureName = "pageContext",
                method = "collectionResult",
                data = JSONObject().apply { put("serializedPageData", serialized) },
                tabId = "tab-1",
            )

        assertEquals(serialized, result)
    }

    @Test
    fun whenCollectPageContextThenReturnsCollectSubscription() {
        val result = testee.collectPageContext()

        assertEquals(RealPageContextJSHelper.PAGE_CONTEXT_FEATURE_NAME, result.featureName)
        assertEquals("collect", result.subscriptionName)
    }
}
