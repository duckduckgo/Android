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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.TestOption
import com.duckduckgo.app.onboarding.orchestrator.PasswordImportResult
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentConfigTest {

    @Test
    fun `address bar seeds its state from the configured initial position`() {
        val content = ContentConfig.AddressBar(
            title = TextConfig.Literal("title"),
            initialPosition = OmnibarType.SINGLE_BOTTOM,
            showSplitOption = true,
        )

        assertEquals(AddressBarContentState(position = OmnibarType.SINGLE_BOTTOM), content.initialState())
    }

    @Test
    fun `download reason seeds its state with no selection`() {
        val content = ContentConfig.DownloadReason(
            title = TextConfig.Literal("title"),
            body = TextConfig.Literal("body"),
        )

        assertEquals(DownloadReasonContentState(selection = null), content.initialState())
    }

    @Test
    fun `single choice seeds its state with the first row`() {
        val first = TestOption(id = "first")
        val content = ContentConfig.SingleChoice(
            title = TextConfig.Literal("title"),
            body = TextConfig.Literal("body"),
            rows = listOf(first, TestOption(id = "second")),
        )

        assertEquals(SingleChoiceContentState(selected = first), content.initialState())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `single choice rejects an empty row list`() {
        ContentConfig.SingleChoice(
            title = TextConfig.Literal("title"),
            body = TextConfig.Literal("body"),
            rows = emptyList(),
        )
    }

    @Test
    fun `configs with the same values are equal`() {
        val config = ComparisonChartConfig.Browser(isCustomAiCopy = false)
        val first = ContentConfig.ComparisonChart(title = TextConfig.Resource(1), config = config)
        val second = ContentConfig.ComparisonChart(title = TextConfig.Resource(1), config = config)

        assertEquals(first, second)
    }

    @Test
    fun `import complete seeds its state as parsing while the run has no outcome`() {
        assertEquals(ImportCompleteContentState.Parsing, importComplete(result = null).initialState())
        assertEquals(ImportCompleteContentState.Parsing, importComplete(PasswordImportResult.InProgress).initialState())
    }

    @Test
    fun `import complete seeds its state from the counts the run recorded`() {
        val content = importComplete(PasswordImportResult.Imported(imported = 4, skipped = 2))

        assertEquals(ImportCompleteContentState.Finished(imported = 4, skipped = 2), content.initialState())
    }

    @Test
    fun `import complete seeds its state as failed when the run recorded a failure`() {
        assertEquals(ImportCompleteContentState.Failed, importComplete(PasswordImportResult.Failed).initialState())
    }

    private fun importComplete(result: PasswordImportResult?) = ContentConfig.ImportComplete(
        title = TextConfig.Literal("title"),
        parsingTitle = TextConfig.Literal("parsing"),
        parsingBody = TextConfig.Literal("body"),
        failedTitle = TextConfig.Literal("failed"),
        failedRow = TextConfig.Literal("row"),
        result = result,
    )
}
