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

import com.duckduckgo.gradle.ModuleType.ApiPureKotlin
import com.duckduckgo.gradle.ModuleType.Companion.destinationDirectorySuffix
import com.duckduckgo.gradle.ModuleType.Impl
import java.io.File

class IntermoduleDependencyManager {

    fun wireUpIntermoduleDependencies(newFeatureDestination: File) {
        val apiModule = File(newFeatureDestination, "${newFeatureDestination.name}-${ApiPureKotlin.destinationDirectorySuffix()}")
        val implModule = File(newFeatureDestination, "${newFeatureDestination.name}-${Impl.destinationDirectorySuffix()}")

        wireUpImplModule(apiModule, implModule, newFeatureDestination)
    }

    private fun wireUpImplModule(
        apiModule: File,
        implModule: File,
        newFeatureDestination: File
    ) {
        if (implModule.exists()) {
            println("Wiring up module: ${implModule.name}")
            val gradleModifier = BuildGradleModifier(File(implModule, BUILD_GRADLE))

            // delete placeholder
            gradleModifier.removeDependency(PLACEHOLDER_API_DEPENDENCY)

            // conditionally insert dependencies
            val modules = mutableListOf<String>()
            if (apiModule.exists()) {
                modules.add(ApiPureKotlin.destinationDirectorySuffix())
            }
            gradleModifier.insertDependencies(newFeatureDestination.name, modules)
        }
    }

    fun wireUpAppModule(
        featureName: String,
        moduleType: ModuleType,
        buildGradleFile: File,
    ) {
        println("Wiring up app module to include feature: name=[$featureName], type=[${moduleType.javaClass.simpleName}]")
        val gradleModifier = BuildGradleModifier(buildGradleFile)
        gradleModifier.insertDependencies(featureName, listOf(moduleType.destinationDirectorySuffix()))
    }

    companion object {
        private const val PLACEHOLDER_API_DEPENDENCY = "implementation project(':example-feature-api')"
        private const val BUILD_GRADLE = "build.gradle"
    }
}
