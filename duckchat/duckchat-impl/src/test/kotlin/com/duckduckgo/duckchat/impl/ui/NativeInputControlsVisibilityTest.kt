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

import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowInputControls
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputControlsVisibilityTest {

    @Test
    fun `controls shown on chat tab when not streaming`() {
        assertTrue(shouldShowInputControls(onChatTab = true, isStreaming = false))
    }

    @Test
    fun `controls hidden on chat tab while streaming`() {
        assertFalse(shouldShowInputControls(onChatTab = true, isStreaming = true))
    }

    @Test
    fun `controls hidden off chat tab when not streaming`() {
        assertFalse(shouldShowInputControls(onChatTab = false, isStreaming = false))
    }

    @Test
    fun `controls hidden off chat tab while streaming`() {
        assertFalse(shouldShowInputControls(onChatTab = false, isStreaming = true))
    }
}
