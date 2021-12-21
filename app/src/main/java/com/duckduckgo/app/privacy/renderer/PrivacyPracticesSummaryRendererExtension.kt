/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.privacy.renderer

import android.content.Context
import androidx.annotation.DrawableRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.*

@DrawableRes
fun PrivacyPractices.Summary.banner(): Int =
    when (this) {
        GOOD -> R.drawable.practices_banner_good
        POOR -> R.drawable.practices_banner_bad
        MIXED, UNKNOWN -> R.drawable.practices_banner_neutral
    }

@DrawableRes
fun PrivacyPractices.Summary.icon(): Int =
    when (this) {
        GOOD -> R.drawable.practices_icon_good
        POOR -> R.drawable.practices_icon_bad
        MIXED, UNKNOWN -> R.drawable.practices_icon_neutral
    }

@DrawableRes
fun PrivacyPractices.Summary.successFailureIcon(): Int =
    when (this) {
        GOOD -> R.drawable.icon_success
        POOR, MIXED, UNKNOWN -> R.drawable.icon_fail
    }

fun PrivacyPractices.Summary.text(context: Context): String =
    when (this) {
        GOOD -> context.getString(R.string.practicesGood)
        POOR -> context.getString(R.string.practicesBad)
        MIXED -> context.getString(R.string.practicesMixed)
        UNKNOWN -> context.getString(R.string.practicesUnknown)
    }
