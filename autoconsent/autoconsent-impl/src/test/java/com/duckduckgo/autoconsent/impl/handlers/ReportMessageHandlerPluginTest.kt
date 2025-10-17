/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ReportMessageHandlerPluginTest {

    private val mockPixelManager: AutoconsentPixelManager = mock()
    private val mockCallback: AutoconsentCallback = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val reportMessageHandler = ReportMessageHandlerPlugin(mockPixelManager)

    @Test
    fun whenProcessAndUnsupportedMessageTypeThenDoNothing() {
        reportMessageHandler.process("unsupported", "", webView, mockCallback)

        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessAndInvalidJsonThenDoNothing() {
        reportMessageHandler.process("report", "invalid json", webView, mockCallback)

        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessThrowsExceptionThenDoNothing() {
        val message = createReportMessage(
            heuristicPatterns = listOf("pattern1"),
            heuristicSnippets = emptyList(),
            detectedPopups = emptyList(),
        )

        whenever(mockPixelManager.isDetectedByPatternsProcessed("id-123-abc")).thenThrow(RuntimeException("test"))

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessAndHeuristicPatternsOnlyThenFireDetectedByPatternsPixel() {
        val message = createReportMessage(
            heuristicPatterns = listOf("pattern1", "pattern2"),
            heuristicSnippets = emptyList(),
            detectedPopups = emptyList(),
        )

        whenever(mockPixelManager.isDetectedByPatternsProcessed("id-123-abc")).thenReturn(false)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager).isDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).markDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessAndHeuristicSnippetsOnlyThenFireDetectedByPatternsPixel() {
        val message = createReportMessage(
            heuristicPatterns = emptyList(),
            heuristicSnippets = listOf("snippet1", "snippet2"),
            detectedPopups = emptyList(),
        )

        whenever(mockPixelManager.isDetectedByPatternsProcessed("id-123-abc")).thenReturn(false)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager).isDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).markDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessAndHeuristicPatternsAndSnippetsThenFireDetectedByPatternsPixel() {
        val message = createReportMessage(
            heuristicPatterns = listOf("pattern1"),
            heuristicSnippets = listOf("snippet1"),
            detectedPopups = emptyList(),
        )

        whenever(mockPixelManager.isDetectedByPatternsProcessed("id-123-abc")).thenReturn(false)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager).isDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).markDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessAndHeuristicMatchAndDetectedPopupsThenFireDetectedByBothPixel() {
        val message = createReportMessage(
            heuristicPatterns = listOf("pattern1"),
            heuristicSnippets = emptyList(),
            detectedPopups = listOf("popup1", "popup2"),
        )

        whenever(mockPixelManager.isDetectedByPatternsProcessed("id-123-abc")).thenReturn(false)
        whenever(mockPixelManager.isDetectedByBothProcessed("id-123-abc")).thenReturn(false)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager).isDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).markDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager).isDetectedByBothProcessed("id-123-abc")
        verify(mockPixelManager).markDetectedByBothProcessed("id-123-abc")
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessAndNoHeuristicMatchAndDetectedPopupsThenFireDetectedOnlyRulesPixel() {
        val message = createReportMessage(
            heuristicPatterns = emptyList(),
            heuristicSnippets = emptyList(),
            detectedPopups = listOf("popup1", "popup2"),
        )

        whenever(mockPixelManager.isDetectedOnlyRulesProcessed("id-123-abc")).thenReturn(false)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager, never()).isDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
        verify(mockPixelManager).isDetectedOnlyRulesProcessed("id-123-abc")
        verify(mockPixelManager).markDetectedOnlyRulesProcessed("id-123-abc")
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenProcessAndAlreadyProcessedDetectedByPatternsThenDoNotFireAgain() {
        val message = createReportMessage(
            heuristicPatterns = listOf("pattern1"),
            heuristicSnippets = emptyList(),
            detectedPopups = emptyList(),
        )

        whenever(mockPixelManager.isDetectedByPatternsProcessed("id-123-abc")).thenReturn(true)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager).isDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager, never()).markDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
    }

    @Test
    fun whenProcessAndAlreadyProcessedDetectedByBothThenDoNotFireAgain() {
        val message = createReportMessage(
            heuristicPatterns = listOf("pattern1"),
            heuristicSnippets = emptyList(),
            detectedPopups = listOf("popup1"),
        )

        whenever(mockPixelManager.isDetectedByPatternsProcessed("id-123-abc")).thenReturn(false)
        whenever(mockPixelManager.isDetectedByBothProcessed("id-123-abc")).thenReturn(true)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager).isDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).markDetectedByPatternsProcessed("id-123-abc")
        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY)
        verify(mockPixelManager).isDetectedByBothProcessed("id-123-abc")
        verify(mockPixelManager, never()).markDetectedByBothProcessed("id-123-abc")
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_BY_BOTH_DAILY)
    }

    @Test
    fun whenProcessAndAlreadyProcessedDetectedOnlyRulesThenDoNotFireAgain() {
        val message = createReportMessage(
            heuristicPatterns = emptyList(),
            heuristicSnippets = emptyList(),
            detectedPopups = listOf("popup1"),
        )

        whenever(mockPixelManager.isDetectedOnlyRulesProcessed("id-123-abc")).thenReturn(true)

        reportMessageHandler.process("report", message, webView, mockCallback)

        verify(mockPixelManager).isDetectedOnlyRulesProcessed("id-123-abc")
        verify(mockPixelManager, never()).markDetectedOnlyRulesProcessed("id-123-abc")
        verify(mockPixelManager, never()).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DETECTED_ONLY_RULES_DAILY)
    }

    @Test
    fun whenSupportedTypesThenReturnReport() {
        assert(reportMessageHandler.supportedTypes == listOf("report"))
    }

    private fun createReportMessage(
        heuristicPatterns: List<String> = emptyList(),
        heuristicSnippets: List<String> = emptyList(),
        detectedPopups: List<String> = emptyList(),
        instanceId: String = "id-123-abc",
        url: String = "https://example.com",
    ): String {
        return """
        {
            "type": "report",
            "instanceId": "$instanceId",
            "url": "$url",
            "mainFrame": true,
            "state": {
                "cosmeticFiltersOn": false,
                "filterListReported": false,
                "lifecycle": "cmpDetected",
                "prehideOn": false,
                "findCmpAttempts": 1,
                "detectedCmps": ["detectedCmp"],
                "detectedPopups": ${detectedPopups.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},
                "heuristicPatterns": ${heuristicPatterns.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},
                "heuristicSnippets": ${heuristicSnippets.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},
                "selfTest": null
            }
        }
        """.trimIndent()
    }
}
