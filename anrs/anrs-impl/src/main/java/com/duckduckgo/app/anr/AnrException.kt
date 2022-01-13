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

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.*

internal class AnrException(thread: Thread) : Exception("ANR detected") {
    val threadStateMap: String
    val threadStateList: List<ProcessThread>

    init {
        stackTrace = thread.stackTrace
        threadStateMap = generateProcessMap()
        threadStateList = generateProcessList()
    }

    private fun generateProcessList(): List<ProcessThread> {
        val list = arrayListOf<ProcessThread>()
        val stackTraces = Thread.getAllStackTraces()
        for (thread in stackTraces.keys) {
            if (!stackTraces[thread].isNullOrEmpty()) {
                val process = ProcessThread(thread.name, thread.state.name, stackTraces[thread]!!.asStringArray())
                list.add(process)
            }
        }
        return list
    }

    private fun generateProcessMap(): String {
        val bos = ByteArrayOutputStream()
        val ps = PrintStream(bos)
        printProcessMap(ps)
        return String(bos.toByteArray())
    }

    private fun printProcessMap(ps: PrintStream) {
        // Get all stack traces in the system
        val stackTraces = Thread.getAllStackTraces()
        ps.println("Process map:")
        for (thread in stackTraces.keys) {
            if (!stackTraces[thread].isNullOrEmpty()) {
                printThread(ps, Locale.getDefault(), thread, stackTraces[thread]!!)
                ps.println()
            }
        }
    }

    private fun printThread(
        ps: PrintStream,
        locale: Locale,
        thread: Thread,
        stack: Array<StackTraceElement>
    ) {
        ps.println(String.format(locale, "\t%s (%s)", thread.name, thread.state))
        for (element in stack) {
            element.apply {
                ps.println(String.format(locale, "\t\t%s.%s(%s:%d)", className, methodName, fileName, lineNumber))
            }
        }
    }
}
