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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighUsageModelNoticeTest {

    private val testee = HighUsageModelNoticeResolver()

    @Test
    fun whenClaudeOpus48IsSelectedThenNoticeIsReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "claude-opus-4-8", shortName = "Claude Opus 4.8"),
            footerContext = duckAiContext(),
            dismissedModelIds = emptySet(),
        )

        assertEquals(
            HighUsageModelNotice(
                modelId = "claude-opus-4-8",
                modelShortName = "Claude Opus 4.8",
            ),
            notice,
        )
    }

    @Test
    fun whenAnotherModelIsSelectedThenNoticeIsNotReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "another-model", shortName = "Another model"),
            footerContext = duckAiContext(),
            dismissedModelIds = emptySet(),
        )

        assertNull(notice)
    }

    @Test
    fun whenSelectedModelHasNoShortNameThenNoticeIsNotReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "claude-opus-4-8", shortName = null),
            footerContext = duckAiContext(),
            dismissedModelIds = emptySet(),
        )

        assertNull(notice)
    }

    @Test
    fun whenSelectedModelWasDismissedThenNoticeIsNotReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "claude-opus-4-8", shortName = "Claude Opus 4.8"),
            footerContext = duckAiContext(),
            dismissedModelIds = setOf("claude-opus-4-8"),
        )

        assertNull(notice)
    }

    @Test
    fun whenFireModeIsActiveThenNoticeIsNotReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "claude-opus-4-8", shortName = "Claude Opus 4.8"),
            footerContext = duckAiContext(isFireMode = true),
            dismissedModelIds = emptySet(),
        )

        assertNull(notice)
    }

    @Test
    fun whenSearchModeIsActiveThenNoticeIsNotReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "claude-opus-4-8", shortName = "Claude Opus 4.8"),
            footerContext = duckAiContext(isDuckAiSelected = false),
            dismissedModelIds = emptySet(),
        )

        assertNull(notice)
    }

    @Test
    fun whenEditingThenNoticeIsNotReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "claude-opus-4-8", shortName = "Claude Opus 4.8"),
            footerContext = duckAiContext(isEditing = true),
            dismissedModelIds = emptySet(),
        )

        assertNull(notice)
    }

    @Test
    fun whenInputIsNotFocusedThenNoticeIsNotReturned() {
        val notice = testee.resolve(
            modelState = selectedModel(id = "claude-opus-4-8", shortName = "Claude Opus 4.8"),
            footerContext = duckAiContext(isInputFocused = false),
            dismissedModelIds = emptySet(),
        )

        assertNull(notice)
    }

    private fun selectedModel(
        id: String,
        shortName: String?,
    ) = ModelState(
        selectedModelId = id,
        selectedModelShortName = shortName,
    )

    private fun duckAiContext(
        isDuckAiSelected: Boolean = true,
        isEditing: Boolean = false,
        isFireMode: Boolean = false,
        isInputFocused: Boolean = true,
    ) = NativeInputFooterContext(
        isDuckAiSelected = isDuckAiSelected,
        isEditing = isEditing,
        isFireMode = isFireMode,
        isInputFocused = isInputFocused,
    )
}
