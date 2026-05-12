/*
 * Copyright (c) 2021 DuckDuckGo
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

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

fun String.applyBoldSpanTo(textToStyle: List<String>): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)
    textToStyle.forEach {
        val index = this.indexOf(it)
        spannable.setSpan(StyleSpan(Typeface.BOLD), index, index + it.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
    }
    return spannable
}

fun String.applyBoldSpanTo(textToStyle: String = this): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)
    val index = this.indexOf(textToStyle)
    spannable.setSpan(StyleSpan(Typeface.BOLD), index, index + textToStyle.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
    return spannable
}

fun String.applyUnderscoreSpanTo(textToStyle: List<String>): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)
    textToStyle.forEach {
        val index = this.indexOf(it)
        spannable.setSpan(UnderlineSpan(), index, index + it.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
    }
    return spannable
}

/**
 * Replaces placeholders in the format of "%1\$s", "%2\$s", etc. with the provided arguments and returns a [SpannedString].
 *
 * @param args The arguments to replace the placeholders with.
 * @return A [SpannedString] with the placeholders replaced by the provided arguments.
 */
fun CharSequence.formatWithSpans(vararg args: String): SpannedString {
    val builder = SpannableStringBuilder(this)
    args.forEachIndexed { index, replacement ->
        val placeholder = "%${index + 1}\$s"
        val start = builder.indexOf(placeholder)
        if (start >= 0) {
            builder.replace(start, start + placeholder.length, replacement)
        }
    }
    return SpannedString(builder)
}
