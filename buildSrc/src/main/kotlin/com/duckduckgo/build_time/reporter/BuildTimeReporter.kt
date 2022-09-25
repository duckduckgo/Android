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

import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.APPEND
import java.time.Duration
import kotlin.math.round

interface BuildTimeReporter {
    val out: PrintStream
    val delimiter: String

    fun report(input: BuildTimeReport) {
        // find the maxes needed for formatting
        val (maxLabelLen, maxDuration, maxFormattedDurationLen) = input.taskDurations.fold(
            Triple(-1, -1L, -1)
        ) { acc, elem ->
            val maxDuration = maxOf(acc.second, elem.second)
            Triple(maxOf(acc.first, elem.first.length), maxDuration, maxOf(acc.third, maxDuration.format().length))
        }

        // scale the values to max column width so that the corresponding bars don't shoot out of the screen
        val scalingFraction = minOf(80, maxDuration) / maxDuration.toDouble()
        val maxFormattedPercentLen = maxDuration.percentOf(input.buildDuration)
            .format()
            .length

        input.taskDurations.forEach {
            val numBlocks = round(it.second * scalingFraction).toInt()
            val percent = it.second.percentOf(input.buildDuration)

            val common = String.format(
                "%${maxLabelLen}s%s%${maxFormattedDurationLen}s%s%${maxFormattedPercentLen}s",
                it.first, delimiter, it.second.format(), delimiter, percent.format()
            )

            out.printf("%s%s%s\n", common, delimiter, "$BLOCK_CHAR".repeat(numBlocks))
        }
    }

    companion object {
        const val BLOCK_CHAR = '\u2588'

        private fun Long.percentOf(buildDuration: Long): Int = round(this / buildDuration.toDouble() * 100).toInt()
        internal fun Long.format(): String {
            val separators = setOf('P', 'D', 'T')
            return Duration.ofSeconds(this).toString()
                .filterNot { it in separators }
        }

        private fun Int.format(): String = String.format("%d%%", this)

        fun newInstance(ext: BuildTimePluginExtension): BuildTimeReporter {
            return when (ext.output.get()) {
                Output.CONSOLE -> ConsoleReporter(ext)
                Output.PIXEL -> PixelReporter(ext)
                Output.CSV -> {
                    val csvFile = ext.reportsDir.get()
                        .file("buildtimes.csv")
                        .asFile
                    CSVReporter(ext, out = newOutputStream(csvFile))
                }
            }
        }

        private fun newOutputStream(csvFile: File): PrintStream {
            csvFile.parentFile.mkdirs()
            return PrintStream(
                Files.newOutputStream(csvFile.toPath(), CREATE, APPEND),
                false,
                StandardCharsets.UTF_8.name()
            )
        }
    }
}

data class BuildTimeReport(
    val buildDuration: Long,
    val taskDurations: Collection<Pair<String, Long>>
)
