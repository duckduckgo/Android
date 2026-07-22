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
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class ContributesWorkerProcessorTest {

    private val appScopeStub = SourceFile.kotlin(
        "AppScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class AppScope private constructor()
        """.trimIndent(),
    )

    private val listenableWorkerStub = SourceFile.kotlin(
        "ListenableWorker.kt",
        """
        package androidx.work
        open class ListenableWorker
        open class CoroutineWorker : ListenableWorker()
        """.trimIndent(),
    )

    private val workerInjectorPluginStub = SourceFile.kotlin(
        "WorkerInjectorPlugin.kt",
        """
        package com.duckduckgo.common.utils.plugins.worker
        import androidx.work.ListenableWorker
        interface WorkerInjectorPlugin {
            fun inject(worker: ListenableWorker): Boolean
        }
        """.trimIndent(),
    )

    @Test
    fun `basic worker with two inject properties generates correct plugin`() {
        val source = SourceFile.kotlin(
            "TestWorker.kt",
            """
            package com.test
            import androidx.work.CoroutineWorker
            import com.duckduckgo.anvil.annotations.ContributesWorker
            import com.duckduckgo.di.scopes.AppScope
            import javax.inject.Inject

            interface SettingsDataStore
            interface ClearDataAction

            @ContributesWorker(AppScope::class)
            class TestWorker : CoroutineWorker() {
                @Inject lateinit var settingsDataStore: SettingsDataStore
                @Inject lateinit var clearDataAction: ClearDataAction
            }
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, listenableWorkerStub, workerInjectorPluginStub)
        val generated = result.findGeneratedSource("TestWorker_WorkerInjectorPlugin.kt")
        val golden = loadGolden("WorkerInjectorPlugin_Basic.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `worker with qualified property generates annotation passthrough`() {
        val source = SourceFile.kotlin(
            "QualifiedWorker.kt",
            """
            package com.test
            import androidx.work.CoroutineWorker
            import com.duckduckgo.anvil.annotations.ContributesWorker
            import com.duckduckgo.di.scopes.AppScope
            import javax.inject.Inject
            import javax.inject.Named

            interface MyService

            @ContributesWorker(AppScope::class)
            class QualifiedWorker : CoroutineWorker() {
                @Named("foo") @Inject lateinit var myService: MyService
            }
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, listenableWorkerStub, workerInjectorPluginStub)
        val generated = result.findGeneratedSource("QualifiedWorker_WorkerInjectorPlugin.kt")
        val golden = loadGolden("WorkerInjectorPlugin_Qualified.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `worker with no inject properties generates plugin with empty inject body`() {
        val source = SourceFile.kotlin(
            "EmptyWorker.kt",
            """
            package com.test
            import androidx.work.CoroutineWorker
            import com.duckduckgo.anvil.annotations.ContributesWorker
            import com.duckduckgo.di.scopes.AppScope

            @ContributesWorker(AppScope::class)
            class EmptyWorker : CoroutineWorker()
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, listenableWorkerStub, workerInjectorPluginStub)
        val generated = result.findGeneratedSource("EmptyWorker_WorkerInjectorPlugin.kt")
        val golden = loadGolden("WorkerInjectorPlugin_Empty.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `annotation on interface compiles successfully`() {
        val source = SourceFile.kotlin(
            "InterfaceWorker.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesWorker
            import com.duckduckgo.di.scopes.AppScope
            import javax.inject.Inject

            interface SomeDep

            @ContributesWorker(AppScope::class)
            interface InterfaceWorker {
                @get:Inject var someDep: SomeDep
            }
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, listenableWorkerStub, workerInjectorPluginStub)
        // Should compile — @ContributesWorker doesn't restrict to classes only
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp {
                symbolProcessorProviders += ContributesWorkerProcessorProvider()
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
