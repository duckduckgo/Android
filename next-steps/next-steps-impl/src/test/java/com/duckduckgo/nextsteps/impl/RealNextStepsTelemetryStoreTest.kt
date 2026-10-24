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
package com.duckduckgo.nextsteps.impl

import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealNextStepsTelemetryStoreTest {

    private val preferences = InMemorySharedPreferences()
    private val preferencesProvider: SharedPreferencesProvider = mock {
        whenever(it.getSharedPreferences(any(), any(), any())).thenReturn(preferences)
    }
    private val testee = RealNextStepsTelemetryStore(preferencesProvider)

    @Test
    fun `when nothing is stored then the state is empty`() {
        assertEquals(NextStepsTelemetryState(), testee.state())
    }

    @Test
    fun `state survives a new store instance and clearing keeps the anchor`() {
        val state = NextStepsTelemetryState(
            firstRenderDay = 20000L,
            displayedMessageId = "message",
            ctaClickedCardId = "setup_add_widget",
            openDay = 20002L,
            dayCounts = mapOf("setup_default_browser" to 0, "setup_add_widget" to 4),
        )
        testee.save(state)
        assertEquals(state, RealNextStepsTelemetryStore(preferencesProvider).state())

        testee.save(NextStepsTelemetryState(firstRenderDay = state.firstRenderDay))
        assertEquals(NextStepsTelemetryState(firstRenderDay = 20000L), RealNextStepsTelemetryStore(preferencesProvider).state())
    }
}
