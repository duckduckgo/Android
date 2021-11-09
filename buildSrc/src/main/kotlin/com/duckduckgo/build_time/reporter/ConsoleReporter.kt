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

import com.duckduckgo.build_time.BuildTimePlugin
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.TypeOf
import java.io.PrintStream

open class ConsoleReporter(
    private val ext: BuildTimePluginExtension,
    override val out: PrintStream = System.out,
    override val delimiter: String = " | "
) : BuildTimeReporter {

    override fun report(input: BuildTimeReport) {
        val filteredDurations = input.taskDurations.filter { it.second >= ext.minTaskDuration.get().seconds }
        out.println("== Build time summary ==")
        out.println("Total build time ${input.buildDuration}s\n")
        if (filteredDurations.isEmpty()) {
            val extra = (ext as ExtensionAware).extensions.getByType(object : TypeOf<Map<String, Any>>() {})
            (extra[BuildTimePlugin.LOGGER_KEY] as Logger).lifecycle(
                "All tasks finished faster than {}s, nothing to report",
                ext.minTaskDuration.get().seconds
            )
        } else {
            super.report(input.copy(taskDurations = filteredDurations))
        }
    }
}