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

package com.duckduckgo.build_time.reporter

import java.io.PrintStream
import java.time.LocalDateTime

class CSVReporter(
    private val ext: BuildTimePluginExtension,
    override val out: PrintStream = System.out,
    override val delimiter: String = " | "
) : BuildTimeReporter {
    override fun report(input: BuildTimeReport) {
        saveBuildTime(input.buildDuration)

        // call super to plot build time in console
        ConsoleReporter(ext).report(input)
    }

    private fun saveBuildTime(buildDuration: Long) {
        out.println("${LocalDateTime.now()},$buildDuration")
    }
}
