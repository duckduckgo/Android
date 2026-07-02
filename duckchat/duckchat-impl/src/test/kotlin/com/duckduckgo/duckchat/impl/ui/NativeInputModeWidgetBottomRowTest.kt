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

package com.duckduckgo.duckchat.impl.ui

import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowBottomRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputModeWidgetBottomRowTest {

    @Test
    fun `contextual widget shows bottom row even without focus`() {
        assertTrue(
            shouldShowBottomRow(
                onChatTab = true,
                isContextual = true,
                hasFocus = false,
                previewEnterFocus = false,
                isStreaming = false,
                suppress = false,
            ),
        )
    }

    @Test
    fun `non-contextual widget hides bottom row without focus`() {
        assertFalse(
            shouldShowBottomRow(
                onChatTab = true,
                isContextual = false,
                hasFocus = false,
                previewEnterFocus = false,
                isStreaming = false,
                suppress = false,
            ),
        )
    }

    @Test
    fun `non-contextual widget shows bottom row when focused`() {
        assertTrue(
            shouldShowBottomRow(
                onChatTab = true,
                isContextual = false,
                hasFocus = true,
                previewEnterFocus = false,
                isStreaming = false,
                suppress = false,
            ),
        )
    }

    @Test
    fun `non-contextual widget shows bottom row in preview enter focus`() {
        assertTrue(
            shouldShowBottomRow(
                onChatTab = true,
                isContextual = false,
                hasFocus = false,
                previewEnterFocus = true,
                isStreaming = false,
                suppress = false,
            ),
        )
    }

    @Test
    fun `bottom row hidden while streaming even in contextual`() {
        assertFalse(
            shouldShowBottomRow(
                onChatTab = true,
                isContextual = true,
                hasFocus = true,
                previewEnterFocus = false,
                isStreaming = true,
                suppress = false,
            ),
        )
    }

    @Test
    fun `bottom row hidden when suppressed even in contextual`() {
        assertFalse(
            shouldShowBottomRow(
                onChatTab = true,
                isContextual = true,
                hasFocus = true,
                previewEnterFocus = false,
                isStreaming = false,
                suppress = true,
            ),
        )
    }

    @Test
    fun `bottom row hidden when not on chat tab even in contextual`() {
        assertFalse(
            shouldShowBottomRow(
                onChatTab = false,
                isContextual = true,
                hasFocus = true,
                previewEnterFocus = false,
                isStreaming = false,
                suppress = false,
            ),
        )
    }
}
