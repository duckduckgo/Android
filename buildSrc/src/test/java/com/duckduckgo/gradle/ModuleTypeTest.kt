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

@file:Suppress("KotlinConstantConditions")

package com.duckduckgo.gradle

import com.duckduckgo.gradle.ModuleType.ApiAndroid
import com.duckduckgo.gradle.ModuleType.ApiPureKotlin
import com.duckduckgo.gradle.ModuleType.Companion
import com.duckduckgo.gradle.ModuleType.Companion.INPUT_API_KOTLIN
import com.duckduckgo.gradle.ModuleType.Companion.destinationDirectorySuffix
import com.duckduckgo.gradle.ModuleType.Companion.exampleSubdirectorySuffix
import com.duckduckgo.gradle.ModuleType.Companion.namespaceSuffix
import com.duckduckgo.gradle.ModuleType.Impl
import com.duckduckgo.gradle.ModuleType.Internal
import org.gradle.internal.impldep.junit.framework.TestCase.assertEquals
import org.gradle.internal.impldep.junit.framework.TestCase.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class ModuleTypeTest {

    @Test
    fun whenInputIsApiThenTypeIsKotlinApi() {
        assertTrue(ModuleType.moduleTypeFromInput("api") is ApiPureKotlin)
    }

    @Test
    fun whenInputIsApiAndroidThenTypeIsAndroidApi() {
        assertTrue(ModuleType.moduleTypeFromInput("apiandroid") is ApiAndroid)
    }

    @Test
    fun whenInputIsImplThenTypeIsImpl() {
        assertTrue(ModuleType.moduleTypeFromInput("impl") is Impl)
    }

    @Test
    fun whenInputIsInternalThenTypeIsInternal() {
        assertTrue(ModuleType.moduleTypeFromInput("internal") is Internal)
    }

    @Test
    fun whenInputIsStoreThenExceptionIsThrown() {
        assertThrows<IllegalArgumentException> {  ModuleType.moduleTypeFromInput("store") }
    }

    @Test
    fun whenInputIsUnknownThenExceptionIsThrown() {
        assertThrows<IllegalArgumentException> {  ModuleType.moduleTypeFromInput("unknown") }
    }

    @Test
    fun whenKotlinApiThenNamespaceSuffixThrowsException() {
        assertThrows<IllegalArgumentException> { ApiPureKotlin.namespaceSuffix() }
    }

    @Test
    fun whenAndroidApiThenNamespaceSuffixIsApi() {
        assertEquals("api", ApiAndroid.namespaceSuffix())
    }

    @Test
    fun whenImplThenNamespaceSuffixIsImpl() {
        assertEquals("impl", Impl.namespaceSuffix())
    }

    @Test
    fun whenInternalThenNamespaceSuffixIsInternal() {
        assertEquals("internal", Internal.namespaceSuffix())
    }

    @Test
    fun whenKotlinApiThenDestinationDirectorySuffixIsApi() {
        assertEquals("api", ApiPureKotlin.destinationDirectorySuffix())
    }

    @Test
    fun whenAndroidApiThenDestinationDirectorySuffixIsApi() {
        assertEquals("api", ApiAndroid.destinationDirectorySuffix())
    }

    @Test
    fun whenImplThenDestinationDirectorySuffixIsImpl() {
        assertEquals("impl", Impl.destinationDirectorySuffix())
    }

    @Test
    fun whenInternalThenDestinationDirectorySuffixIsInternal() {
        assertEquals("internal", Internal.destinationDirectorySuffix())
    }

    @Test
    fun whenKotlinApiThenExampleDirectorySuffixIsApi() {
        assertEquals("api", ApiPureKotlin.exampleSubdirectorySuffix())
    }

    @Test
    fun whenAndroidApiThenExampleDirectorySuffixIsApi() {
        assertEquals("api-android", ApiAndroid.exampleSubdirectorySuffix())
    }

    @Test
    fun whenImplThenExampleDirectorySuffixIsImpl() {
        assertEquals("impl", Impl.exampleSubdirectorySuffix())
    }

    @Test
    fun whenInternalThenExampleDirectorySuffixIsInternal() {
        assertEquals("internal", Internal.exampleSubdirectorySuffix())
    }
}
