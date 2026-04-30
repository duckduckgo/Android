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
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ContributesActivePluginPointProcessorTest {

    // ========================================================================
    // Stubs
    // ========================================================================

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

    private val toggleStub = SourceFile.kotlin(
        "Toggle.kt",
        """
        package com.duckduckgo.feature.toggles.api
        interface Toggle {
            annotation class DefaultValue(val defaultValue: DefaultFeatureValue)
            annotation class Experiment
            annotation class InternalAlwaysEnabled
            interface Store {
                fun set(key: String, state: State)
                fun get(key: String): State?
            }
            data class State(
                val remoteEnableState: Boolean? = null,
                val enable: Boolean? = null,
            )
            fun isEnabled(): Boolean
            fun setRawStoredState(state: State)
            fun getRawStoredState(): State?
        }
        enum class DefaultFeatureValue { TRUE, FALSE, INTERNAL }
        """.trimIndent(),
    )

    private val remoteFeatureStoreNamedStub = SourceFile.kotlin(
        "RemoteFeatureStoreNamed.kt",
        """
        package com.duckduckgo.feature.toggles.api
        import kotlin.reflect.KClass
        annotation class RemoteFeatureStoreNamed(val value: KClass<*>)
        """.trimIndent(),
    )

    private val activePluginStub = SourceFile.kotlin(
        "ActivePlugin.kt",
        """
        package com.duckduckgo.common.utils.plugins
        interface ActivePlugin {
            suspend fun isActive(): Boolean = true
        }
        interface PluginPoint<T> {
            fun getPlugins(): Collection<T>
        }
        interface InternalActivePluginPoint<out T : @JvmSuppressWildcards ActivePlugin> {
            suspend fun getPlugins(): Collection<T>
        }
        typealias ActivePluginPoint<T> = InternalActivePluginPoint<@JvmSuppressWildcards T>
        """.trimIndent(),
    )

    private val dispatcherProviderStub = SourceFile.kotlin(
        "DispatcherProvider.kt",
        """
        package com.duckduckgo.common.utils
        import kotlinx.coroutines.CoroutineDispatcher
        interface DispatcherProvider {
            fun io(): CoroutineDispatcher
        }
        """.trimIndent(),
    )

    private val sharedPreferencesProviderStub = SourceFile.kotlin(
        "SharedPreferencesProvider.kt",
        """
        package com.duckduckgo.data.store.api
        import android.content.SharedPreferences
        interface SharedPreferencesProvider {
            fun getSharedPreferences(name: String, multiprocess: Boolean = false, migrate: Boolean = false): SharedPreferences
        }
        """.trimIndent(),
    )

    private val appCoroutineScopeStub = SourceFile.kotlin(
        "AppCoroutineScope.kt",
        """
        package com.duckduckgo.app.di
        import javax.inject.Qualifier
        @Qualifier
        @Retention(AnnotationRetention.RUNTIME)
        annotation class AppCoroutineScope
        """.trimIndent(),
    )

    private val daggerSetStub = SourceFile.kotlin(
        "DaggerSet.kt",
        """
        package com.duckduckgo.di
        typealias DaggerSet<T> = Set<@JvmSuppressWildcards T>
        typealias DaggerMap<K, V> = Map<@JvmSuppressWildcards K, @JvmSuppressWildcards V>
        """.trimIndent(),
    )

    private val allStubs get() = arrayOf(
        appScopeStub,
        activityScopeStub,
        toggleStub,
        remoteFeatureStoreNamedStub,
        activePluginStub,
        dispatcherProviderStub,
        sharedPreferencesProviderStub,
        appCoroutineScopeStub,
        daggerSetStub,
    )

    // ========================================================================
    // @ContributesActivePluginPoint tests
    // ========================================================================

    @Test
    fun `basic active plugin point generates all files`() {
        val source = SourceFile.kotlin(
            "TestActivePlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.AppScope

            @ContributesActivePluginPoint(
                scope = AppScope::class,
                featureName = "pluginPointTestPlugin",
            )
            interface TestActivePlugin : ActivePlugin
            """.trimIndent(),
        )

        val result = compile(source, *allStubs)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedMain = result.findGeneratedSource("TestActivePlugin_ActivePluginPoint.kt")
        val goldenMain = loadGolden("ActivePluginPoint_Basic.kt")
        assertEquals(goldenMain, generatedMain)

        val generatedSentinel = result.findGeneratedSource("ActivePluginPointRegistry_pluginPointTestPlugin.kt")
        val goldenSentinel = loadGolden("ActivePluginPoint_Basic_Sentinel.kt")
        assertEquals(goldenSentinel, generatedSentinel)
    }

    @Test
    fun `active plugin point with boundType generates correct wrapper`() {
        val source = SourceFile.kotlin(
            "TestActivePlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.AppScope

            interface MyBoundPlugin : ActivePlugin {
                fun doSomething()
            }

            @ContributesActivePluginPoint(
                scope = AppScope::class,
                boundType = MyBoundPlugin::class,
                featureName = "pluginPointMyBoundPlugin",
            )
            interface TestPluginPointTrigger
            """.trimIndent(),
        )

        val result = compile(source, *allStubs)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedMain = result.findGeneratedSource("TestPluginPointTrigger_ActivePluginPoint.kt")
        val goldenMain = loadGolden("ActivePluginPoint_BoundType.kt")
        assertEquals(goldenMain, generatedMain)
    }

    @Test
    fun `error when featureName does not start with pluginPoint`() {
        val source = SourceFile.kotlin(
            "TestActivePlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.AppScope

            @ContributesActivePluginPoint(
                scope = AppScope::class,
                featureName = "badFeatureName",
            )
            interface TestActivePlugin : ActivePlugin
            """.trimIndent(),
        )

        val result = compile(source, *allStubs)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("featureName must start with \"pluginPoint\""))
    }

    @Test
    fun `error when scope is not AppScope`() {
        val source = SourceFile.kotlin(
            "TestActivePlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.ActivityScope

            @ContributesActivePluginPoint(
                scope = ActivityScope::class,
                featureName = "pluginPointTestPlugin",
            )
            interface TestActivePlugin : ActivePlugin
            """.trimIndent(),
        )

        val result = compile(source, *allStubs)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Active plugins can only be used in 'AppScope'"))
    }

    // ========================================================================
    // @ContributesActivePlugin tests
    // ========================================================================

    @Test
    fun `basic active plugin generates wrapper and remote feature`() {
        val pluginPoint = SourceFile.kotlin(
            "TestActivePluginPoint.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.AppScope

            @ContributesActivePluginPoint(
                scope = AppScope::class,
                featureName = "pluginPointTestPlugin",
            )
            interface TestActivePluginBase : ActivePlugin
            """.trimIndent(),
        )

        val plugin = SourceFile.kotlin(
            "MyPlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePlugin
            import com.duckduckgo.di.scopes.AppScope
            import javax.inject.Inject

            @ContributesActivePlugin(
                scope = AppScope::class,
                boundType = TestActivePluginBase::class,
                featureName = "pluginMyPlugin",
                parentFeatureName = "pluginPointTestPlugin",
            )
            class MyPlugin @Inject constructor() : TestActivePluginBase {
                override suspend fun isActive(): Boolean = true
            }
            """.trimIndent(),
        )

        val result = compile(pluginPoint, plugin, *allStubs)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = result.findGeneratedSource("MyPlugin_ActivePlugin.kt")
        val golden = loadGolden("ActivePlugin_Basic.kt")
        assertEquals(golden, generated)
    }

    @Test
    fun `error when plugin featureName starts with pluginPoint`() {
        val pluginPoint = SourceFile.kotlin(
            "TestActivePluginPoint.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.AppScope

            @ContributesActivePluginPoint(
                scope = AppScope::class,
                featureName = "pluginPointTestPlugin",
            )
            interface TestActivePluginBase : ActivePlugin
            """.trimIndent(),
        )

        val plugin = SourceFile.kotlin(
            "MyPlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePlugin
            import com.duckduckgo.di.scopes.AppScope
            import javax.inject.Inject

            @ContributesActivePlugin(
                scope = AppScope::class,
                boundType = TestActivePluginBase::class,
                featureName = "pluginPointBadName",
                parentFeatureName = "pluginPointTestPlugin",
            )
            class MyPlugin @Inject constructor() : TestActivePluginBase {
                override suspend fun isActive(): Boolean = true
            }
            """.trimIndent(),
        )

        val result = compile(pluginPoint, plugin, *allStubs)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("featureName must start with \"plugin\" but not \"pluginPoint\""))
    }

    @Test
    fun `error when plugin scope is not AppScope`() {
        val plugin = SourceFile.kotlin(
            "MyPlugin.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesActivePlugin
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.ActivityScope
            import javax.inject.Inject

            interface TestActivePluginBase : ActivePlugin

            @ContributesActivePlugin(
                scope = ActivityScope::class,
                boundType = TestActivePluginBase::class,
                featureName = "pluginMyPlugin",
                parentFeatureName = "pluginPointTestPlugin",
            )
            class MyPlugin @Inject constructor() : TestActivePluginBase {
                override suspend fun isActive(): Boolean = true
            }
            """.trimIndent(),
        )

        val result = compile(plugin, *allStubs)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Active plugins can only be used in 'AppScope'"))
    }

    // ========================================================================
    // Cross-module validation tests
    // ========================================================================

    @Test
    fun `cross-module valid parentFeatureName succeeds`() {
        // Simulate Module A having produced a sentinel for "pluginPointFoo".
        // In a real build, @ContributesActivePluginPoint generates this sentinel object.
        // We compile it as a standalone Kotlin source and package it as a JAR,
        // because kotlin-compile-testing doesn't produce .class files when KSP-generated
        // code references Android APIs not on the test classpath.
        val sentinelSource = SourceFile.kotlin(
            "ActivePluginPointRegistry_pluginPointFoo.kt",
            """
            package com.duckduckgo.anvil.generated
            @Suppress("unused")
            internal object ActivePluginPointRegistry_pluginPointFoo
            """.trimIndent(),
        )
        val sentinelJar = compileToJar(sentinelSource)

        // Module B: defines a plugin referencing Module A's plugin point.
        // The sentinel JAR is on Module B's classpath, so the processor should
        // find it and NOT emit a deferred marker.
        val moduleBSource = SourceFile.kotlin(
            "ModuleBPlugin.kt",
            """
            package com.test.moduleb
            import com.duckduckgo.anvil.annotations.ContributesActivePlugin
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.AppScope
            import javax.inject.Inject

            interface FooPlugin : ActivePlugin

            @ContributesActivePlugin(
                scope = AppScope::class,
                boundType = FooPlugin::class,
                featureName = "pluginMyPlugin",
                parentFeatureName = "pluginPointFoo",
            )
            class MyPlugin @Inject constructor() : FooPlugin {
                override suspend fun isActive(): Boolean = true
            }
            """.trimIndent(),
        )

        val resultB = compileWithClasspath(
            sources = arrayOf(moduleBSource, *allStubs),
            additionalClasspaths = listOf(sentinelJar),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, resultB.exitCode)

        // Verify no deferred marker was emitted (sentinel was found on classpath)
        val kspDir = resultB.outputDirectory.resolve("../ksp/sources/kotlin")
        val deferredFiles = kspDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("ActivePluginDeferredValidation_") }
            .toList()
        assertTrue(
            "Expected no deferred markers but found: ${deferredFiles.map { it.name }}",
            deferredFiles.isEmpty(),
        )
    }

    @Test
    fun `cross-module invalid parentFeatureName fails at app module`() {
        // Step 1: Simulate Module B having produced a deferred marker for a
        // non-existent parent "pluginPointWRONG". In a real build, the KSP processor
        // emits this when it can't find the sentinel on the classpath.
        val deferredMarkerSource = SourceFile.kotlin(
            "ActivePluginDeferredValidation_pluginPointWRONG__MyPlugin.kt",
            """
            package com.duckduckgo.anvil.generated.deferred
            @Suppress("unused")
            internal object ActivePluginDeferredValidation_pluginPointWRONG__MyPlugin
            """.trimIndent(),
        )

        // Step 2: Simulate Module A having produced a sentinel for "pluginPointFoo"
        // (the correct one that Module B SHOULD have referenced).
        val sentinelSource = SourceFile.kotlin(
            "ActivePluginPointRegistry_pluginPointFoo.kt",
            """
            package com.duckduckgo.anvil.generated
            @Suppress("unused")
            internal object ActivePluginPointRegistry_pluginPointFoo
            """.trimIndent(),
        )

        // Compile both into JARs
        val deferredMarkerJar = compileToJar(deferredMarkerSource)
        val sentinelJar = compileToJar(sentinelSource)

        // Step 3: "App" module has its own plugin point (so Phase 2 fires) and sees
        // both Module A's sentinel and Module B's deferred marker on the classpath.
        // Phase 2 should find the deferred marker for "pluginPointWRONG", look for
        // a matching sentinel, NOT find it, and emit an error.
        val appSource = SourceFile.kotlin(
            "AppPluginPoint.kt",
            """
            package com.test.app
            import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
            import com.duckduckgo.common.utils.plugins.ActivePlugin
            import com.duckduckgo.di.scopes.AppScope

            @ContributesActivePluginPoint(
                scope = AppScope::class,
                featureName = "pluginPointApp",
            )
            interface AppPlugin : ActivePlugin
            """.trimIndent(),
        )

        val resultApp = compileWithClasspath(
            sources = arrayOf(appSource, *allStubs),
            additionalClasspaths = listOf(deferredMarkerJar, sentinelJar),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, resultApp.exitCode)
        assertTrue(
            "Expected error about pluginPointWRONG but got: ${resultApp.messages}",
            resultApp.messages.contains("pluginPointWRONG"),
        )
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun compile(vararg sources: SourceFile): KotlinCompilation.Result {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            symbolProcessorProviders = listOf(
                ContributesActivePluginPointProcessorProvider(),
                ContributesPluginPointProcessorProvider(),
                ContributesRemoteFeatureProcessorProvider(),
            )
            inheritClassPath = true
        }.compile()
    }

    private fun compileWithClasspath(
        sources: Array<SourceFile>,
        additionalClasspaths: List<java.io.File>,
    ): KotlinCompilation.Result {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            symbolProcessorProviders = listOf(
                ContributesActivePluginPointProcessorProvider(),
                ContributesPluginPointProcessorProvider(),
                ContributesRemoteFeatureProcessorProvider(),
            )
            inheritClassPath = true
            classpaths += additionalClasspaths
        }.compile()
    }

    /**
     * Compiles standalone Kotlin sources (no KSP) and packages the output as a JAR.
     *
     * KSP's `getDeclarationsFromPackage` only discovers classes from JARs,
     * not from loose .class directories on the classpath. This helper compiles
     * lightweight sentinel/marker objects and packages them for cross-module tests.
     */
    private fun compileToJar(vararg sources: SourceFile): File {
        val result = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
        }.compile()
        assertEquals("Pre-compilation for JAR packaging failed", KotlinCompilation.ExitCode.OK, result.exitCode)

        val jarFile = File.createTempFile("module-output", ".jar")
        jarFile.deleteOnExit()
        JarOutputStream(jarFile.outputStream()).use { jar ->
            result.outputDirectory.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(result.outputDirectory).path.replace(File.separatorChar, '/')
                jar.putNextEntry(JarEntry(entryName))
                file.inputStream().use { it.copyTo(jar) }
                jar.closeEntry()
            }
        }
        return jarFile
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
