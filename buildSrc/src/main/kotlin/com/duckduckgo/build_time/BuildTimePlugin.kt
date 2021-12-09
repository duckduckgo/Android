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

package com.duckduckgo.build_time

import com.duckduckgo.build_time.reporter.BuildTimePluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.reflect.TypeOf

open class BuildTimePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(ReportingBasePlugin::class.java)
        val ext = project.extensions.create(PLUGIN_EXTENSION_NAME, BuildTimePluginExtension::class.java, project)
        (ext as ExtensionAware).extensions.add(
            object : TypeOf<Map<String, Any>>() {},
            EXTRA_EXTENSION_NAME,
            mapOf<String, Any>(LOGGER_KEY to project.logger)
        )

        project.gradle.addBuildListener(TaskTimeRecorder(ext))
    }

    companion object {
        const val PLUGIN_EXTENSION_NAME = "BuildTimeReporter"
        const val EXTRA_EXTENSION_NAME = "extra"
        const val LOGGER_KEY = "logger"
    }
}
