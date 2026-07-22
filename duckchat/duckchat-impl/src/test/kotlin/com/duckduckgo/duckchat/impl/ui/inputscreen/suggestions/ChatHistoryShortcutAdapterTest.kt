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

package com.duckduckgo.duckchat.impl.ui.inputscreen.suggestions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatHistoryShortcutAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistoryShortcutAdapterTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenHiddenThenItemCountIsZero() {
        val adapter = ChatHistoryShortcutAdapter(onClick = {})
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun whenSetVisibleTrueThenItemCountIsOne() {
        val adapter = ChatHistoryShortcutAdapter(onClick = {})
        adapter.setVisible(true)
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun whenSetVisibleTrueThenFalseItemCountReturnsToZero() {
        val adapter = ChatHistoryShortcutAdapter(onClick = {})
        adapter.setVisible(true)
        adapter.setVisible(false)
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun whenSetVisibleSameValueTwiceThenNoCrash() {
        val adapter = ChatHistoryShortcutAdapter(onClick = {})
        adapter.setVisible(true)
        adapter.setVisible(true)
        assertEquals(1, adapter.itemCount)
    }
}
