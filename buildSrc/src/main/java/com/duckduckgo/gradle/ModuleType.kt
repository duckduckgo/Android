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

sealed class ModuleType {

    data object ApiPureKotlin : ModuleType()
    data object ApiAndroid : ModuleType()
    data object Impl : ModuleType()
    data object Internal : ModuleType()

    companion object {

        const val INPUT_API_KOTLIN = "api"
        const val INPUT_API_ANDROID = "apiandroid"
        const val INPUT_API_IMPL = "impl"
        const val INPUT_API_INTERNAL = "internal"

        fun moduleTypeFromInput(input: String): ModuleType {
            return when (input) {
                INPUT_API_KOTLIN -> ApiPureKotlin
                INPUT_API_ANDROID -> ApiAndroid
                INPUT_API_IMPL -> Impl
                INPUT_API_INTERNAL -> Internal
                else -> throw IllegalArgumentException("Invalid module type [$input]")
            }
        }

        fun ModuleType.namespaceSuffix(): String {
            return when (this) {
                ApiAndroid -> "api"
                Impl -> "impl"
                Internal -> "internal"
                else -> throw IllegalArgumentException("Module type [${javaClass.simpleName} does not have a module namespace suffix")
            }
        }

        fun ModuleType.destinationDirectorySuffix(): String {
            return when (this) {
                ApiAndroid -> "api"
                ApiPureKotlin -> "api"
                Impl -> "impl"
                Internal -> "internal"
                else -> throw IllegalArgumentException("Module type [${javaClass.simpleName} does not have a destination directory")
            }
        }

        fun ModuleType.exampleSubdirectorySuffix(): String {
            return when (this) {
                ApiAndroid -> "api-android"
                ApiPureKotlin -> "api"
                Impl -> "impl"
                Internal -> "internal"
                else -> throw IllegalArgumentException("Module type [${javaClass.simpleName} does not have a template")
            }
        }

        fun validInputTypes(): List<String> {
            return listOf(
                INPUT_API_KOTLIN,
                INPUT_API_ANDROID,
                INPUT_API_IMPL,
                INPUT_API_INTERNAL,
            )
        }
    }
}
