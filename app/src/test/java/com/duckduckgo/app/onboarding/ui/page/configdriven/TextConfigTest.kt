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

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TextConfigTest {

    private val context: Context = mock()

    @Test
    fun `resolves a string resource through the context`() {
        whenever(context.getString(42)).thenReturn("resolved")

        assertEquals("resolved", TextConfig.Resource(42).resolve(context))
    }

    @Test
    fun `resolves a literal without touching the context`() {
        assertEquals("literal", TextConfig.Literal("literal").resolve(context))
    }
}
