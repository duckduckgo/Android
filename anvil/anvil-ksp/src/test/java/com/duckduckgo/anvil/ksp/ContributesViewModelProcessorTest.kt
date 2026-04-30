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

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributesViewModelProcessorTest {

    private val activityScopeStub = SourceFile.kotlin(
        "ActivityScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class ActivityScope private constructor()
        """.trimIndent(),
    )

    private val viewModelStub = SourceFile.kotlin(
        "ViewModel.kt",
        """
        package androidx.lifecycle
        open class ViewModel
        """.trimIndent(),
    )

    private val viewModelFactoryPluginStub = SourceFile.kotlin(
        "ViewModelFactoryPlugin.kt",
        """
        package com.duckduckgo.common.utils.plugins.view_model
        import androidx.lifecycle.ViewModel
        interface ViewModelFactoryPlugin {
            fun <T : ViewModel?> create(modelClass: Class<T>): T?
        }
        """.trimIndent(),
    )

    private val singleInstanceInStub = SourceFile.kotlin(
        "SingleInstanceIn.kt",
        """
        package dagger
        import kotlin.reflect.KClass
        annotation class SingleInstanceIn(val scope: KClass<*>)
        """.trimIndent(),
    )

    @Test
    fun `basic ViewModel with Inject constructor generates correct factory`() {
        val source = SourceFile.kotlin(
            "MyViewModel.kt",
            """
            package com.test
            import androidx.lifecycle.ViewModel
            import com.duckduckgo.anvil.annotations.ContributesViewModel
            import com.duckduckgo.di.scopes.ActivityScope
            import javax.inject.Inject

            interface Repository

            @ContributesViewModel(ActivityScope::class)
            class MyViewModel @Inject constructor(private val repo: Repository) : ViewModel()
            """.trimIndent(),
        )

        val result = compile(source, activityScopeStub, viewModelStub, viewModelFactoryPluginStub)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = result.findGeneratedSource("MyViewModel_ViewModelFactory.kt")
        val golden = loadGolden("ViewModelFactory_Basic.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `error when no Inject constructor`() {
        val source = SourceFile.kotlin(
            "BadViewModel.kt",
            """
            package com.test
            import androidx.lifecycle.ViewModel
            import com.duckduckgo.anvil.annotations.ContributesViewModel
            import com.duckduckgo.di.scopes.ActivityScope

            @ContributesViewModel(ActivityScope::class)
            class BadViewModel : ViewModel()
            """.trimIndent(),
        )

        val result = compile(source, activityScopeStub, viewModelStub, viewModelFactoryPluginStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("must have an @Inject constructor"))
    }

    @Test
    fun `error when annotated with SingleInstanceIn`() {
        val source = SourceFile.kotlin(
            "SingletonViewModel.kt",
            """
            package com.test
            import androidx.lifecycle.ViewModel
            import com.duckduckgo.anvil.annotations.ContributesViewModel
            import com.duckduckgo.di.scopes.ActivityScope
            import dagger.SingleInstanceIn
            import javax.inject.Inject

            @SingleInstanceIn(ActivityScope::class)
            @ContributesViewModel(ActivityScope::class)
            class SingletonViewModel @Inject constructor() : ViewModel()
            """.trimIndent(),
        )

        val result = compile(source, activityScopeStub, viewModelStub, viewModelFactoryPluginStub, singleInstanceInStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("cannot be annotated with @SingleInstanceIn"))
    }

    @Test
    fun `error when constructor has default parameter values`() {
        val source = SourceFile.kotlin(
            "DefaultParamViewModel.kt",
            """
            package com.test
            import androidx.lifecycle.ViewModel
            import com.duckduckgo.anvil.annotations.ContributesViewModel
            import com.duckduckgo.di.scopes.ActivityScope
            import javax.inject.Inject

            @ContributesViewModel(ActivityScope::class)
            class DefaultParamViewModel @Inject constructor(private val name: String = "default") : ViewModel()
            """.trimIndent(),
        )

        val result = compile(source, activityScopeStub, viewModelStub, viewModelFactoryPluginStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("constructor parameters must not have default values"))
    }

    private fun compile(vararg sources: SourceFile): KotlinCompilation.Result {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            symbolProcessorProviders = listOf(ContributesViewModelProcessorProvider())
            inheritClassPath = true
        }.compile()
    }

    private fun KotlinCompilation.Result.findGeneratedSource(fileName: String): String {
        val kspDir = outputDirectory.resolve("../ksp/sources/kotlin")
        val file = kspDir.walkTopDown().find { it.name == fileName }
            ?: error(
                "Generated file $fileName not found in ${kspDir.absolutePath}. " +
                    "Available files: ${kspDir.walkTopDown().filter { it.isFile }.map { it.name }.toList()}",
            )
        return file.readText()
    }

    private fun loadGolden(fileName: String): String {
        return javaClass.classLoader.getResource("golden/$fileName")?.readText()
            ?: error("Golden file golden/$fileName not found in test resources")
    }
}
