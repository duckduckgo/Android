/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.cookies.impl

import android.util.Base64
import com.duckduckgo.cookies.api.CookiesFeatureName

/**
 * Convenience method to get the [CookiesFeatureName] from its [String] value
 */
fun cookiesFeatureValueOf(value: String): CookiesFeatureName? {
    return CookiesFeatureName.values().find { it.value == value }
}

/**
 * Redacts the stacktrace for SQL crashes removing the WHERE statement if it exists and returns a base64 string
 */
fun redactStacktraceInBase64(stacktrace: String): String {
    val start = stacktrace.indexOf("WHERE")
    val redacted = if (start == -1) {
        stacktrace
    } else {
        val finish = stacktrace.substring(start).indexOf("\n") + start
        if (finish > start) {
            stacktrace.removeRange(start, finish)
        } else {
            stacktrace
        }
    }
    return Base64.encodeToString(redacted.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
}
