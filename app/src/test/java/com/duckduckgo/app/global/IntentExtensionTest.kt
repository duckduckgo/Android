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

package com.duckduckgo.app.global

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentExtensionTest {

    @Test
    fun whenSharedTextIsPlainStringThenIntentTextIsThatString() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "plain share example")

        assertEquals("plain share example", intent.intentText)
    }

    @Test
    fun whenSharedTextIsStyledThenIntentTextIsItsPlainText() {
        val styled = SpannableString("styled share example").apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, 0) }
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, styled as CharSequence)

        assertEquals("styled share example", intent.intentText)
    }

    @Test
    fun whenIntentHasDataThenIntentTextIsTheDataUri() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/page")).putExtra(Intent.EXTRA_TEXT, "ignored")

        assertEquals("https://example.com/page", intent.intentText)
    }

    @Test
    fun whenIntentHasNoDataAndNoTextThenIntentTextIsNull() {
        assertNull(Intent(Intent.ACTION_SEND).intentText)
    }
}
