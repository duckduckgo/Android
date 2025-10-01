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

package com.duckduckgo.app.anr

import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.anrs.store.AnrEntity
import com.duckduckgo.app.anrs.store.ExceptionEntity
import logcat.asLog
import okio.ByteString.Companion.encode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class ProcessThread(
    val name: String,
    val state: String,
    val stackTrace: ArrayList<String>,
)

internal data class AnrData(
    val message: String?,
    val name: String?,
    val file: String?,
    val lineNumber: Int,
    val stackTrace: ArrayList<String>,
    val timeStamp: String = FORMATTER_SECONDS.format(LocalDateTime.now()),
    val webView: String,
    val customTab: Boolean,
)

internal fun AnrData.asAnrEntity(): AnrEntity {
    return AnrEntity(
        hash = stackTrace.toString().encode().md5().hex(),
        message = message.orEmpty(),
        name = name.orEmpty(),
        file = file,
        lineNumber = lineNumber,
        stackTrace = stackTrace,
        timestamp = timeStamp,
        webView = webView,
        customTab = customTab,
    )
}

internal fun Throwable.asAnrData(webView: String, customTab: Boolean): AnrData {
    return AnrData(
        name = this.toString().replace(": $message", "", true),
        message = message,
        stackTrace = stackTrace.asStringArray(),
        file = stackTrace.getOrNull(0)?.fileName,
        lineNumber = stackTrace.getOrNull(0)?.lineNumber ?: Int.MIN_VALUE,
        webView = webView,
        customTab = customTab,
    )
}

internal fun CrashLogger.Crash.asCrashEntity(
    appVersion: String,
    processName: String,
    webView: String,
    customTab: Boolean,
): ExceptionEntity {
    val timestamp = FORMATTER_SECONDS.format(LocalDateTime.now())
    val stacktrace = this.t.asLog().sanitizeStackTrace()
    return ExceptionEntity(
        hash = (stacktrace + timestamp).encode().md5().hex(),
        shortName = this.shortName,
        processName = processName,
        message = this.t.extractExceptionCause(),
        stackTrace = stacktrace,
        version = appVersion,
        timestamp = timestamp,
        webView = webView,
        customTab = customTab,
    )
}

private fun Throwable?.extractExceptionCause(): String {
    if (this == null) {
        return "Exception missing"
    }
    return "${this.javaClass.name} - ${this.stackTrace.firstOrNull()}"
}

internal fun Array<StackTraceElement>.asStringArray(): ArrayList<String> {
    val array = arrayListOf<String>()
    forEach {
        if (it.isNativeMethod) {
            array.add("${it.className}.${it.methodName}(Native Method)")
        } else {
            array.add("${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
        }
    }
    return array
}

private val FORMATTER_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

internal fun String.sanitizeStackTrace(): String {
    // if we fail for whatever reason, we don't include the stack trace
    return runCatching {
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        val phoneRegex = Regex("\\b(?:\\d[\\s()-]?){6,14}\\b") // This regex matches common phone number formats
        val phoneRegex2 = Regex("\\b\\+?\\d[- (]*\\d{3}[- )]*\\d{3}[- ]*\\d{4}\\b") // enhanced to redact also other phone number formats
        val urlRegex = Regex("\\b(?:https?://|www\\.)\\S+\\b")
        val ipv4Regex = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

        var sanitizedStackTrace = this
        sanitizedStackTrace = sanitizedStackTrace.replace(urlRegex, "[REDACTED_URL]")
        sanitizedStackTrace = sanitizedStackTrace.replace(emailRegex, "[REDACTED_EMAIL]")
        sanitizedStackTrace = sanitizedStackTrace.replace(phoneRegex2, "[REDACTED_PHONE]")
        sanitizedStackTrace = sanitizedStackTrace.replace(phoneRegex, "[REDACTED_PHONE]")
        sanitizedStackTrace = sanitizedStackTrace.replace(ipv4Regex, "[REDACTED_IPV4]")

        return sanitizedStackTrace
    }.getOrDefault(this)
}
