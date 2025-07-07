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

package com.duckduckgo.autoconsent.impl.handlers

import java.io.BufferedReader
import java.io.StringReader
import kotlin.sequences.forEach

object ReplyHandler {
    fun constructReply(message: String): String {
        return """
            (function() {
                window.autoconsentMessageCallback($message, window.origin);
            })();
        """.trimIndentEfficient()
    }

    private fun String.trimIndentEfficient(): String {
        val reader1 = BufferedReader(StringReader(this))
        var minIndent = Int.MAX_VALUE
        reader1.useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    val indent = line.takeWhile(Char::isWhitespace).length
                    if (indent < minIndent) minIndent = indent
                }
            }
        }
        if (minIndent == Int.MAX_VALUE) minIndent = 0

        val sb = StringBuilder(this.length)
        val reader2 = BufferedReader(StringReader(this))
        reader2.useLines { lines ->
            lines.forEach { line ->
                if (line.length >= minIndent) {
                    sb.append(line, minIndent, line.length)
                } else {
                    sb.append(line)
                }
                sb.append('\n')
            }
        }

        return sb.toString()
    }
}
