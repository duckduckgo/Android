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

import android.webkit.URLUtil
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.anrs.store.AnrEntity
import com.duckduckgo.app.anrs.store.ExceptionEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import logcat.asLog
import okio.ByteString.Companion.encode

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
    )
}

internal fun Throwable.asAnrData(webView: String): AnrData {
    return AnrData(
        name = this.toString().replace(": $message", "", true),
        message = message,
        stackTrace = stackTrace.asStringArray(),
        file = stackTrace.getOrNull(0)?.fileName,
        lineNumber = stackTrace.getOrNull(0)?.lineNumber ?: Int.MIN_VALUE,
        webView = webView,
    )
}

internal fun CrashLogger.Crash.asCrashEntity(
    appVersion: String,
    processName: String,
    webView: String,
): ExceptionEntity {
    val timestamp = FORMATTER_SECONDS.format(LocalDateTime.now())
    val stacktrace = this.t.asLog().redactDomains()
    return ExceptionEntity(
        hash = (stacktrace + timestamp).encode().md5().hex(),
        shortName = this.shortName,
        processName = processName,
        message = this.t.extractExceptionCause(),
        stackTrace = stacktrace,
        version = appVersion,
        timestamp = timestamp,
        webView = webView,
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

internal fun String.redactDomains(): String {
    return this.split(" ").map {
        if (URLUtil.isValidUrl(it)) {
            "[REDACTED]"
        } else {
            it
        }
    }.joinToString(" ")
}
