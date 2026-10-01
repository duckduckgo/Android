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

package com.duckduckgo.app.anr.ndk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DefaultCrashpadInitializerTest {

    private val toggle: Toggle = mock()
    private val feature: NativeCrashFeature = mock<NativeCrashFeature>().also {
        whenever(it.uploadMinidumps()).thenReturn(toggle)
    }
    private val initializer = DefaultCrashpadInitializer(mock(), mock(), feature)

    @Test
    fun `upload url empty when toggle disabled even if settings has url`() {
        whenever(toggle.isEnabled()).thenReturn(false)
        whenever(toggle.getSettings()).thenReturn("""{"uploadUrl":"https://example.com/upload"}""")
        assertEquals("", initializer.uploadUrl())
    }

    @Test
    fun `upload url read from settings when toggle enabled`() {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(toggle.getSettings()).thenReturn("""{"uploadUrl":"https://example.com/upload"}""")
        assertEquals("https://example.com/upload", initializer.uploadUrl())
    }

    @Test
    fun `upload url empty when enabled but settings missing or malformed`() {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(toggle.getSettings()).thenReturn(null)
        assertEquals("", initializer.uploadUrl())
        whenever(toggle.getSettings()).thenReturn("not json")
        assertEquals("", initializer.uploadUrl())
    }
}
