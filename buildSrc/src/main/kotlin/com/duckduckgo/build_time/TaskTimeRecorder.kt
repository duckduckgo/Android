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
import com.duckduckgo.build_time.reporter.BuildTimeReport
import com.duckduckgo.build_time.reporter.BuildTimeReporter
import org.gradle.BuildResult
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.TaskState
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class TaskTimeRecorder(private val ext: BuildTimePluginExtension) : TaskExecutionListener, BuildAndTaskExecutionListener {
    private val taskStartTime: MutableMap<String, Instant> = ConcurrentHashMap()
    private val taskDurations: MutableCollection<Pair<String, Long>> = ConcurrentLinkedQueue()
    private lateinit var buildStarted: Instant
    private val logger by lazy {
        val extra = (ext as ExtensionAware).extensions.getByType(object : TypeOf<Map<String, Any>>() {})
        extra[BuildTimePlugin.LOGGER_KEY] as Logger
    }

    override fun beforeExecute(task: Task) {
        taskStartTime[task.path] = Instant.now()
    }

    override fun afterExecute(task: Task, state: TaskState) {
        check(taskStartTime.contains(task.path)) { "No start time for ${task.path}" }
        val duration = Duration.between(taskStartTime[task.path], Instant.now()).seconds
        taskDurations.add(task.path to duration)
    }

    override fun buildFinished(result: BuildResult) {
        // skip when build fails
        result.failure?.let {
            logger.info("Build failed, nothing to record: {}", it)
            return
        }

        val buildTime = Duration.between(buildStarted, Instant.now())

        BuildTimeReporter.newInstance(ext).run {
            report(
                BuildTimeReport(
                    buildDuration = buildTime.seconds,
                    taskDurations = taskDurations.sortedBy { -it.second }
                )
            )
        }
    }

    override fun projectsEvaluated(gradle: Gradle) {
        buildStarted = Instant.now()
    }
}
