/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.anvil.ksp

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class ContributesServiceApiProcessorTest {

    private val appScopeStub = SourceFile.kotlin(
        "AppScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class AppScope private constructor()
        """.trimIndent(),
    )

    @Test
    fun `basic ContributesServiceApi generates correct module`() {
        val source = SourceFile.kotlin(
            "TestService.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesServiceApi
            import com.duckduckgo.di.scopes.AppScope
            @ContributesServiceApi(AppScope::class)
            interface TestService
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub)
        val generated = result.findGeneratedSource("TestService_Module.kt")
        val golden = loadGolden("ServiceApi_Module.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `ContributesNonCachingServiceApi generates correct module`() {
        val source = SourceFile.kotlin(
            "TestNonCachingService.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi
            import com.duckduckgo.di.scopes.AppScope
            @ContributesNonCachingServiceApi(AppScope::class)
            interface TestNonCachingService
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub)
        val generated = result.findGeneratedSource("TestNonCachingService_Module.kt")
        val golden = loadGolden("NonCachingServiceApi_Module.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `ContributesServiceApi with boundType generates correct module`() {
        val source = SourceFile.kotlin(
            "TestBoundTypeService.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesServiceApi
            import com.duckduckgo.di.scopes.AppScope
            interface MyBoundType
            @ContributesServiceApi(scope = AppScope::class, boundType = MyBoundType::class)
            interface TestBoundTypeService
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub)
        val generated = result.findGeneratedSource("TestBoundTypeService_Module.kt")
        val golden = loadGolden("BoundTypeServiceApi_Module.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `ContributesServiceApi on class fails compilation`() {
        val source = SourceFile.kotlin(
            "TestClass.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesServiceApi
            import com.duckduckgo.di.scopes.AppScope
            @ContributesServiceApi(AppScope::class)
            class TestClass
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("must be an interface"))
    }

    @Test
    fun `both annotations on same interface fails compilation`() {
        val source = SourceFile.kotlin(
            "TestBothAnnotations.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesServiceApi
            import com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi
            import com.duckduckgo.di.scopes.AppScope
            @ContributesServiceApi(AppScope::class)
            @ContributesNonCachingServiceApi(AppScope::class)
            interface TestBothAnnotations
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Only one of"))
    }

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp {
                symbolProcessorProviders += ContributesServiceApiProcessorProvider()
            }
            inheritClassPath = true
        }.compile()
    }

    private fun JvmCompilationResult.findGeneratedSource(fileName: String): String {
        val file = sourcesGeneratedBySymbolProcessor.find { it.name == fileName }
            ?: error(
                "Generated file $fileName not found. " +
                    "Available files: ${sourcesGeneratedBySymbolProcessor.map { it.name }.toList()}",
            )
        return file.readText()
    }

    private fun loadGolden(fileName: String): String {
        return javaClass.classLoader.getResource("golden/$fileName")?.readText()
            ?: error("Golden file golden/$fileName not found in test resources")
    }
}
