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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ContentValueStoreTest {

    private val testee = ContentValueStore()

    private fun addressBar(initialPosition: OmnibarType = OmnibarType.SINGLE_TOP) = ContentConfig.AddressBar(
        title = TextConfig.Literal("title"),
        initialPosition = initialPosition,
        showSplitOption = false,
    )

    @Test
    fun `seeds the state from the content's initial state`() {
        val state = testee.contentState("address_bar_position", addressBar(OmnibarType.SINGLE_BOTTOM))

        assertEquals(AddressBarContentState(position = OmnibarType.SINGLE_BOTTOM), state.value)
    }

    @Test
    fun `returns the same flow for the same step so live edits survive a rebind`() {
        val first = testee.contentState("address_bar_position", addressBar())
        first.value = AddressBarContentState(position = OmnibarType.SPLIT)

        val second = testee.contentState("address_bar_position", addressBar())

        assertSame(first, second)
        assertEquals(AddressBarContentState(position = OmnibarType.SPLIT), second.value)
    }

    @Test
    fun `keeps independent state per step`() {
        val first = testee.contentState("address_bar_position", addressBar())
        val second = testee.contentState("quick_setup_address_bar", addressBar(OmnibarType.SINGLE_BOTTOM))

        assertNotSame(first, second)
        assertEquals(AddressBarContentState(position = OmnibarType.SINGLE_TOP), first.value)
        assertEquals(AddressBarContentState(position = OmnibarType.SINGLE_BOTTOM), second.value)
    }
}
