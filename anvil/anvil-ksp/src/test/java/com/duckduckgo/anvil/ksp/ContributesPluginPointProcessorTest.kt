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
class ContributesPluginPointProcessorTest {

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

    private val pluginPointStub = SourceFile.kotlin(
        "PluginPoint.kt",
        """
        package com.duckduckgo.common.utils.plugins
        interface PluginPoint<T> {
            fun getPlugins(): Collection<T>
        }
        """.trimIndent(),
    )

    private val daggerSetStub = SourceFile.kotlin(
        "DaggerSet.kt",
        """
        package com.duckduckgo.di
        typealias DaggerSet<T> = Set<@JvmSuppressWildcards T>
        """.trimIndent(),
    )

    private val daggerMapStub = SourceFile.kotlin(
        "DaggerMap.kt",
        """
        package com.duckduckgo.di
        typealias DaggerMap<K, V> = Map<@JvmSuppressWildcards K, @JvmSuppressWildcards V>
        """.trimIndent(),
    )

    @Test
    fun `basic plugin point generates PluginPoint and Module`() {
        val source = SourceFile.kotlin(
            "TestPlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesPluginPoint
            import com.duckduckgo.di.scopes.AppScope

            @ContributesPluginPoint(AppScope::class)
            interface TestPlugin {
                fun doSomething()
            }
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, pluginPointStub, daggerSetStub, daggerMapStub)
        val generatedPluginPoint = result.findGeneratedSource("TestPlugin_PluginPoint.kt")
        val goldenPluginPoint = loadGolden("PluginPoint_Basic.kt")
        assertEquals(goldenPluginPoint, generatedPluginPoint)

        val generatedModule = result.findGeneratedSource("TestPlugin_PluginPoint_Module.kt")
        val goldenModule = loadGolden("PluginPoint_Basic_Module.kt")
        assertEquals(goldenModule, generatedModule)
    }

    @Test
    fun `plugin point with boundType uses bound type as plugin type`() {
        val source = SourceFile.kotlin(
            "TriggerInterface.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesPluginPoint
            import com.duckduckgo.di.scopes.AppScope

            interface MyBoundPlugin {
                fun execute()
            }

            @ContributesPluginPoint(scope = AppScope::class, boundType = MyBoundPlugin::class)
            interface TriggerInterface
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, pluginPointStub, daggerSetStub, daggerMapStub)
        val generatedPluginPoint = result.findGeneratedSource("TriggerInterface_PluginPoint.kt")
        val goldenPluginPoint = loadGolden("PluginPoint_BoundType.kt")
        assertEquals(goldenPluginPoint, generatedPluginPoint)

        val generatedModule = result.findGeneratedSource("TriggerInterface_PluginPoint_Module.kt")
        val goldenModule = loadGolden("PluginPoint_BoundType_Module.kt")
        assertEquals(goldenModule, generatedModule)
    }

    @Test
    fun `error when not an interface`() {
        val source = SourceFile.kotlin(
            "NotAnInterface.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesPluginPoint
            import com.duckduckgo.di.scopes.AppScope

            @ContributesPluginPoint(AppScope::class)
            class NotAnInterface
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, pluginPointStub, daggerSetStub, daggerMapStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("must be an interface"))
    }

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp {
                symbolProcessorProviders += ContributesPluginPointProcessorProvider()
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
