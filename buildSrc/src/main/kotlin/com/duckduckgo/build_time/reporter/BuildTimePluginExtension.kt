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

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.reporting.ReportingExtension
import java.time.Duration

enum class Output {
    CONSOLE,
    PIXEL,
    CSV
}

open class BuildTimePluginExtension(private val project: Project) {
    val output: Property<Output> = project.objects.property(Output::class.java)
        .convention(Output.PIXEL)

    val minTaskDuration: Property<Duration> = project.objects.property(Duration::class.java)
        .convention(Duration.ofSeconds(1L))

    val reportsDir: DirectoryProperty = project.objects.directoryProperty()
        .convention(baseReportsDir.map { it.dir("build-time") })

    private val baseReportsDir: DirectoryProperty
        get() = project.objects.directoryProperty()
            .apply { set(project.layout.projectDirectory.dir("../report")) }
}
