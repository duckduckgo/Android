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

package com.duckduckgo.app.browser

import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewImeInsetsTest {

    @Test
    fun whenImeInsetsPresentThenStripZeroesImeAndPreservesSystemBars() {
        val incoming = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 400))
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 48, 0, 24))
            .build()

        val outgoing = WebViewImeInsets.strip(incoming)

        assertEquals(Insets.NONE, outgoing.getInsets(WindowInsetsCompat.Type.ime()))
        assertEquals(Insets.of(0, 48, 0, 24), outgoing.getInsets(WindowInsetsCompat.Type.systemBars()))
    }

    @Test
    fun whenImeInsetsAlreadyNoneThenReturnsSameInstance() {
        val incoming = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 48, 0, 24))
            .build()

        val outgoing = WebViewImeInsets.strip(incoming)

        assertSame(incoming, outgoing)
        assertEquals(Insets.of(0, 48, 0, 24), outgoing.getInsets(WindowInsetsCompat.Type.systemBars()))
    }
}
