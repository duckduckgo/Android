/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.gradle

import org.gradle.api.GradleException
import java.io.File

class BuildGradleModifier(private val gradleFile: File) {

    fun lineExists(placeholder: String): Boolean {
        val lines = gradleFile.readText().lines()

        for (line in lines) {
            if (line.contains(placeholder)) {
                return true
            }
        }

        return false
    }

    fun removeDependency(placeholder: String) {
        val lines = gradleFile.readText().lines()
        val updatedLines = mutableListOf<String>()

        for (line in lines) {
            if (!line.contains(placeholder)) {
                updatedLines.add(line)
            }
        }

        val newFileOutput = updatedLines.joinToString(System.lineSeparator())
        gradleFile.writeText(newFileOutput)
    }

    fun insertDependencies(
        featureName: String,
        dependencies: List<String>,
        isInternal: Boolean = false
    ) {
        if(dependencies.isEmpty()) return

        val importStatement = if (isInternal) "internalImplementation" else "implementation"

        val searchString = "dependencies {"
        val linesToAdd = dependencies.map { """$importStatement project(":${featureName}-${it}")""" }

        val lines = gradleFile.readText().lines()
        val updatedLines = mutableListOf<String>()
        var found = false

        for (line in lines) {
            updatedLines.add(line)

            if (line.contains(searchString)) {
                linesToAdd.forEach {
                    if (!lines.contains("\t$it")) {
                        println("Inserting [$it] into [${gradleFile.parentFile.name}/${gradleFile.name}]")
                        updatedLines.add("\t$it")
                    }
                }
                found = true
            }
        }

        if (!found) {
            throw GradleException("Could not insert dependencies into build.gradle because could not locate correct place to insert them.")
        }

        val newFileOutput = updatedLines.joinToString(System.lineSeparator())
        gradleFile.writeText(newFileOutput)
    }

    fun renameModuleNamespace(
        featureName: String,
        moduleType: String,
    ) {
        val lines = gradleFile.readText().lines()
        val updatedLines = mutableListOf<String>()
        var found = false
        val placeholder = moduleNamespacePlaceholder(moduleType)
        val newNamespace = buildNewNamespace(featureName, moduleType)

        for (line in lines) {
            if (line.contains(placeholder)) {
                updatedLines.add(newNamespace)
                found = true
            } else {
                updatedLines.add(line)
            }
        }

        if (!found) {
            throw GradleException("Could not update namespace for [$featureName/$moduleType] because namespace placeholder not found in new module's build.gradle")
        }

        val newFileOutput = updatedLines.joinToString(System.lineSeparator())
        gradleFile.writeText(newFileOutput)
    }

    private fun buildNewNamespace(
        featureName: String,
        moduleType: String
    ): String {
        return """    namespace "com.duckduckgo.$featureName.${moduleType}""""
    }

    private fun moduleNamespacePlaceholder(moduleType: String): String {
        return """namespace "com.duckduckgo.examplefeature.${moduleType}""""
    }
}
