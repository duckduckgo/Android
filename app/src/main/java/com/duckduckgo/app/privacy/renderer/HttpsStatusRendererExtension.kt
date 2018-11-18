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
import com.duckduckgo.app.privacy.model.HttpsStatus


@DrawableRes
fun HttpsStatus.icon(): Int {
    return when (this) {
        HttpsStatus.NONE -> R.drawable.dashboard_https_bad
        HttpsStatus.MIXED -> R.drawable.dashboard_https_neutral
        HttpsStatus.SECURE -> R.drawable.dashboard_https_good
    }
}

fun HttpsStatus.text(context: Context): String {
    return when (this) {
        HttpsStatus.NONE -> context.getString(R.string.httpsBad)
        HttpsStatus.MIXED -> context.getString(R.string.httpsMixed)
        HttpsStatus.SECURE -> context.getString(R.string.httpsGood)
    }
}

@DrawableRes
fun HttpsStatus.successFailureIcon(): Int {
    return when (this) {
        HttpsStatus.SECURE -> R.drawable.icon_success
        HttpsStatus.MIXED, HttpsStatus.NONE -> R.drawable.icon_fail
    }
}
