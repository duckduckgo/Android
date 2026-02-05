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

package com.duckduckgo.common.utils

import logcat.asLog

fun Throwable.sanitizeStackTrace(): String {
    // if we fail for whatever reason, we don't include the stack trace
    return runCatching {
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        val phoneRegex = Regex("\\b(?:\\d[\\s()-]?){6,14}\\b") // This regex matches common phone number formats
        val phoneRegex2 = Regex("\\b\\+?\\d[- (]*\\d{3}[- )]*\\d{3}[- ]*\\d{4}\\b") // enhanced to redact also other phone number formats
        val urlRegex = Regex("\\b(?:https?://|www\\.)\\S+\\b")
        val ipv4Regex = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

        var sanitizedStackTrace = this.asLog()
        sanitizedStackTrace = sanitizedStackTrace.replace(urlRegex, "[REDACTED_URL]")
        sanitizedStackTrace = sanitizedStackTrace.replace(emailRegex, "[REDACTED_EMAIL]")
        sanitizedStackTrace = sanitizedStackTrace.replace(phoneRegex2, "[REDACTED_PHONE]")
        sanitizedStackTrace = sanitizedStackTrace.replace(phoneRegex, "[REDACTED_PHONE]")
        sanitizedStackTrace = sanitizedStackTrace.replace(ipv4Regex, "[REDACTED_IPV4]")

        sanitizedStackTrace
    }.getOrDefault(this.javaClass.name)
}
