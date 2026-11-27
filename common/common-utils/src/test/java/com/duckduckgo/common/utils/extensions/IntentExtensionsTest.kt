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

package com.duckduckgo.common.utils.extensions

import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentExtensionsTest {

    @Test
    fun whenTextExtraIsPlainStringThenReturnsThatString() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, "example")

        assertEquals("example", intent.getTextExtra(Intent.EXTRA_PROCESS_TEXT))
    }

    @Test
    fun whenTextExtraIsStyledThenReturnsItsPlainText() {
        val styled = SpannableString("example").apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, 0) }
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, styled as CharSequence)

        assertEquals("example", intent.getTextExtra(Intent.EXTRA_PROCESS_TEXT))
    }

    @Test
    fun whenTextExtraIsSelectionOfEditableTextThenReturnsItsPlainText() {
        val selection = SpannableStringBuilder("an example subject").subSequence(3, 10)
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, selection)

        assertEquals("example", intent.getTextExtra(Intent.EXTRA_PROCESS_TEXT))
    }

    @Test
    fun whenTextExtraIsMissingThenReturnsNull() {
        assertNull(Intent(Intent.ACTION_PROCESS_TEXT).getTextExtra(Intent.EXTRA_PROCESS_TEXT))
    }
}
