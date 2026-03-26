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
import com.duckduckgo.gradle.ModuleType.Companion.INPUT_API_ANDROID
import com.duckduckgo.gradle.ModuleType.Companion.INPUT_API_IMPL
import com.duckduckgo.gradle.ModuleType.Companion.INPUT_API_INTERNAL
import com.duckduckgo.gradle.ModuleType.Companion.INPUT_API_KOTLIN
import com.duckduckgo.gradle.ModuleType.Companion.destinationDirectorySuffix
import com.duckduckgo.gradle.ModuleType.Companion.exampleSubdirectorySuffix
import com.duckduckgo.gradle.ModuleType.Companion.namespaceSuffix
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ModuleCreator : DefaultTask() {

    @get:Optional
    @get:Input
    abstract val feature: Property<String>

    @get:InputDirectory
    abstract val repoRootDirectory: DirectoryProperty

    @get:InputFile
    abstract val appBuildGradleFile: RegularFileProperty

    @TaskAction
    fun performAction() {
        val featureName = feature.orNull?.trim() ?: throw GradleException(ERROR_MESSAGE_MISSING_FEATURE.trim())
        println("Running module creation task, with input: [$featureName]")

        val (feature, moduleType) = extractFeatureAndModuleType(featureName)

        val rootDirFile = repoRootDirectory.asFile.get()
        val newFeatureDestination = File(rootDirFile, feature)
        val newModuleDestination = File(newFeatureDestination, "${feature}-${moduleType.destinationDirectorySuffix()}")

        newModuleDestination.ensureModuleDoesNotExist()
        newModuleDestination.createDirectory()
        newModuleDestination.copyTemplateFiles(getExampleSubDirectory(moduleType))
        newModuleDestination.renameModuleNamespace(feature, moduleType)

        copyTopLevelExampleFiles(newFeatureDestination)

        with(IntermoduleDependencyManager()) {
            wireUpIntermoduleDependencies(newFeatureDestination)
            wireUpAppModule(feature, moduleType, appBuildGradleFile.asFile.get())
        }
    }

    private fun extractFeatureAndModuleType(featureName: String): Pair<String, ModuleType> {
        if (!featureName.contains("/")) {
            throw GradleException(ERROR_MESSAGE_MISSING_FEATURE)
        }

        val inputExtractor = InputExtractor()
        return inputExtractor.extractFeatureNameAndTypes(featureName)
    }

    private fun copyTopLevelExampleFiles(newFeatureDestination: File) {
        getExampleDir().listFiles()?.filter { it.isFile }?.forEach {
            if (!File(newFeatureDestination, it.name).exists()) {
                it.copyTo(File(newFeatureDestination, it.name))
            }
        }
    }

    private fun File.ensureModuleDoesNotExist() {
        val root = repoRootDirectory.asFile.get()
        if (exists()) throw GradleException("Feature [${relativeTo(root)}] already exists")
    }

    private fun File.createDirectory() {
        if (!mkdirs()) throw GradleException("Failed to create directory at $path")
        println("Created new directory at $path")
    }

    private fun File.copyTemplateFiles(exampleDirectory: File) {
        println("Using example files from ${exampleDirectory.path}")

        exampleDirectory.listFiles()
            ?.filterNot { it.name == "build" }
            ?.forEach {
                val newFile = File(this, it.name)
                it.copyRecursively(newFile)
            }
    }

    private fun getExampleDir(): File = File(repoRootDirectory.asFile.get(), EXAMPLE_FEATURE_NAME)

    private fun getExampleSubDirectory(type: ModuleType): File {
        val exampleDir = getExampleDir()
        val subDirectory = "$EXAMPLE_FEATURE_NAME-${type.exampleSubdirectorySuffix()}"
        val exampleDirectory = File(exampleDir, subDirectory)
        if (!exampleDirectory.exists()) throw GradleException("Invalid module type [$type]. ${exampleDirectory.path} does not exist")
        return exampleDirectory
    }

    private fun File.renameModuleNamespace(
        featureName: String,
        moduleType: ModuleType
    ) {

        // Pure kotlin modules don't need a namespace
        if (moduleType == ApiPureKotlin) return

        val gradleModifier = BuildGradleModifier(File(this, BUILD_GRADLE))
        val formattedFeature = featureName.replace("-", "")
        gradleModifier.renameModuleNamespace(formattedFeature, moduleType.namespaceSuffix())
    }

    companion object {

        private const val EXAMPLE_FEATURE_NAME = "example-feature"
        private const val BUILD_GRADLE = "build.gradle"

        private const val ERROR_MESSAGE_MISSING_FEATURE =
            "Feature name and module type not provided correctly. These must be provided as a command line argument in the format `-Pfeature=FEATURE/TYPE`" +
                "\n" +
                "\nTo create a pure Kotlin API module (preferred API type):" +
                "\n\t./gradlew newModule -Pfeature=my-new-feature/${INPUT_API_KOTLIN}" +
                "\n" +
                "\nTo create an Android-aware API module:" +
                "\n\t./gradlew newModule -Pfeature=my-new-feature/${INPUT_API_ANDROID}" +
                "\n" +
                "\nTo create an impl module:" +
                "\n\t./gradlew newModule -Pfeature=my-new-feature/${INPUT_API_IMPL}" +
                "\n" +
                "\nTo create an internal module:" +
                "\n\t./gradlew newModule -Pfeature=my-new-feature/${INPUT_API_INTERNAL}"
    }
}



