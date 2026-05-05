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
class ContributeToActivityStarterProcessorTest {

    private val appScopeStub = SourceFile.kotlin(
        "AppScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class AppScope private constructor()
        """.trimIndent(),
    )

    private val duckDuckGoActivityStub = SourceFile.kotlin(
        "DuckDuckGoActivity.kt",
        """
        package com.duckduckgo.common.ui
        import androidx.appcompat.app.AppCompatActivity
        open class DuckDuckGoActivity : AppCompatActivity()
        """.trimIndent(),
    )

    private val appCompatActivityStub = SourceFile.kotlin(
        "AppCompatActivity.kt",
        """
        package androidx.appcompat.app
        open class AppCompatActivity
        """.trimIndent(),
    )

    private val activityParamsStub = SourceFile.kotlin(
        "GlobalActivityStarter.kt",
        """
        package com.duckduckgo.navigation.api
        import androidx.appcompat.app.AppCompatActivity
        object GlobalActivityStarter {
            interface ActivityParams
            data class DeeplinkActivityParams(
                val screenName: String?,
                val jsonArguments: String,
            )
            interface ParamToActivityMapper {
                fun map(activityParams: ActivityParams): Class<out AppCompatActivity>?
                fun map(deeplinkActivityParams: DeeplinkActivityParams): ActivityParams?
            }
        }
        """.trimIndent(),
    )

    private val moshiStubs = SourceFile.kotlin(
        "MoshiStubs.kt",
        """
        package com.squareup.moshi
        class Moshi {
            class Builder {
                fun add(factory: Any): Builder = this
                fun build(): Moshi = Moshi()
            }
            fun <T> adapter(clazz: Class<T>): JsonAdapter<T> = JsonAdapter()
        }
        class JsonAdapter<T> {
            fun fromJson(json: String): T? = null
        }
        class Types {
            companion object {
                @JvmStatic fun getRawType(type: Any): Class<*> = Any::class.java
            }
        }
        """.trimIndent(),
    )

    private val kotlinJsonAdapterFactoryStub = SourceFile.kotlin(
        "KotlinJsonAdapterFactory.kt",
        """
        package com.squareup.moshi.kotlin.reflect
        class KotlinJsonAdapterFactory
        """.trimIndent(),
    )

    private val commonStubs = arrayOf(
        appScopeStub,
        duckDuckGoActivityStub,
        appCompatActivityStub,
        activityParamsStub,
        moshiStubs,
        kotlinJsonAdapterFactoryStub,
    )

    @Test
    fun `basic mapper without screenName generates correct code`() {
        val source = SourceFile.kotlin(
            "TestActivity.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
            import com.duckduckgo.common.ui.DuckDuckGoActivity
            import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

            data class TestParams(val id: String) : ActivityParams

            @ContributeToActivityStarter(TestParams::class)
            class TestActivity : DuckDuckGoActivity()
            """.trimIndent(),
        )

        val result = compile(source, *commonStubs)
        val generated = result.findGeneratedSource("TestActivity_ActivityMapper.kt")
        val golden = loadGolden("ActivityMapper_NoScreenName.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `mapper with screenName generates deeplink handling code`() {
        val source = SourceFile.kotlin(
            "TestActivity.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
            import com.duckduckgo.common.ui.DuckDuckGoActivity
            import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

            data class TestParams(val id: String) : ActivityParams

            @ContributeToActivityStarter(TestParams::class, screenName = "example")
            class TestActivity : DuckDuckGoActivity()
            """.trimIndent(),
        )

        val result = compile(source, *commonStubs)
        val generated = result.findGeneratedSource("TestActivity_ActivityMapper.kt")
        val golden = loadGolden("ActivityMapper_WithScreenName.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `class not extending DuckDuckGoActivity fails compilation`() {
        val source = SourceFile.kotlin(
            "BadActivity.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
            import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

            data class TestParams(val id: String) : ActivityParams

            @ContributeToActivityStarter(TestParams::class)
            class BadActivity
            """.trimIndent(),
        )

        val result = compile(source, *commonStubs)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("must extend"))
    }

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp {
                symbolProcessorProviders += ContributeToActivityStarterProcessorProvider()
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
