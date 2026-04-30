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
class ContributesSubComponentProcessorTest {

    private val appScopeStub = SourceFile.kotlin(
        "AppScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class AppScope private constructor()
        """.trimIndent(),
    )

    private val activityScopeStub = SourceFile.kotlin(
        "ActivityScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class ActivityScope private constructor()
        """.trimIndent(),
    )

    private val fragmentScopeStub = SourceFile.kotlin(
        "FragmentScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class FragmentScope private constructor()
        """.trimIndent(),
    )

    private val serviceScopeStub = SourceFile.kotlin(
        "ServiceScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class ServiceScope private constructor()
        """.trimIndent(),
    )

    private val vpnScopeStub = SourceFile.kotlin(
        "VpnScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class VpnScope private constructor()
        """.trimIndent(),
    )

    @Test
    fun `ActivityScope generates Injector interface`() {
        val source = SourceFile.kotlin(
            "MyActivity.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.InjectWith
            import com.duckduckgo.di.scopes.ActivityScope

            @InjectWith(ActivityScope::class)
            class MyActivity
            """.trimIndent(),
        )

        val result = compile(source, activityScopeStub)
        val generated = result.findGeneratedSource("MyActivity_Injector.kt")
        val golden = loadGolden("SubComponent_ActivityScope_Injector.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `FragmentScope generates SubComponent and Module`() {
        val source = SourceFile.kotlin(
            "MyFragment.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.InjectWith
            import com.duckduckgo.di.scopes.FragmentScope

            @InjectWith(FragmentScope::class)
            class MyFragment
            """.trimIndent(),
        )

        val result = compile(source, fragmentScopeStub, activityScopeStub)
        val generatedSubComponent = result.findGeneratedSource("MyFragment_SubComponent.kt")
        val goldenSubComponent = loadGolden("SubComponent_FragmentScope.kt")
        assertEquals(goldenSubComponent, generatedSubComponent)

        val generatedModule = result.findGeneratedSource("MyFragment_SubComponent_Module.kt")
        val goldenModule = loadGolden("SubComponent_FragmentScope_Module.kt")
        assertEquals(goldenModule, generatedModule)
    }

    @Test
    fun `FragmentScope with delayGeneration true fails because parent is ActivityScope`() {
        val source = SourceFile.kotlin(
            "MyFragment.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.InjectWith
            import com.duckduckgo.di.scopes.FragmentScope

            @InjectWith(scope = FragmentScope::class, delayGeneration = true)
            class MyFragment
            """.trimIndent(),
        )

        val result = compile(source, fragmentScopeStub, activityScopeStub, appScopeStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("delayGeneration = true"))
        assertTrue(result.messages.contains("AppScope"))
    }

    @Test
    fun `ServiceScope with delayGeneration true uses ContributesSubcomponent`() {
        val source = SourceFile.kotlin(
            "MyService.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.InjectWith
            import com.duckduckgo.di.scopes.ServiceScope

            @InjectWith(scope = ServiceScope::class, delayGeneration = true)
            class MyService
            """.trimIndent(),
        )

        val result = compile(source, serviceScopeStub, appScopeStub)
        val generatedSubComponent = result.findGeneratedSource("MyService_SubComponent.kt")
        val goldenSubComponent = loadGolden("SubComponent_ServiceScope_Delayed.kt")
        assertEquals(goldenSubComponent, generatedSubComponent)

        val generatedModule = result.findGeneratedSource("MyService_SubComponent_Module.kt")
        val goldenModule = loadGolden("SubComponent_ServiceScope_Delayed_Module.kt")
        assertEquals(goldenModule, generatedModule)
    }

    @Test
    fun `custom bindingKey uses specified class in ClassKey`() {
        val source = SourceFile.kotlin(
            "MyService.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.InjectWith
            import com.duckduckgo.di.scopes.ServiceScope

            class CustomKey
            @InjectWith(scope = ServiceScope::class, bindingKey = CustomKey::class)
            class MyService
            """.trimIndent(),
        )

        val result = compile(source, serviceScopeStub, appScopeStub)
        val generatedModule = result.findGeneratedSource("MyService_SubComponent_Module.kt")
        val goldenModule = loadGolden("SubComponent_CustomBindingKey_Module.kt")
        assertEquals(goldenModule, generatedModule)
    }

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp {
                symbolProcessorProviders += ContributesSubComponentProcessorProvider()
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
