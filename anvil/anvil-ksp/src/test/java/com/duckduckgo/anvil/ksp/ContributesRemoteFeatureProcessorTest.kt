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
class ContributesRemoteFeatureProcessorTest {

    // Stubs for types used in the generated code
    private val appScopeStub = SourceFile.kotlin(
        "AppScope.kt",
        """
        package com.duckduckgo.di.scopes
        abstract class AppScope private constructor()
        """.trimIndent(),
    )

    private val toggleStub = SourceFile.kotlin(
        "Toggle.kt",
        """
        package com.duckduckgo.feature.toggles.api
        interface Toggle {
            annotation class DefaultValue(val value: DefaultFeatureValue)
            annotation class Experiment
            annotation class InternalAlwaysEnabled
            interface Store {
                fun set(key: String, state: State)
                fun get(key: String): State?
            }
            data class State(
                val remoteEnableState: Boolean? = null,
                val enable: Boolean? = null,
                val minSupportedVersion: Int? = null,
                val targets: List<Target> = emptyList(),
                val cohorts: List<Cohort> = emptyList(),
                val settings: String? = null,
                val exceptions: List<Any> = emptyList(),
                val rollout: List<Double>? = null,
                val rolloutThreshold: Double? = null,
                val assignedCohort: Any? = null,
            ) {
                data class Target(
                    val variantKey: String? = null,
                    val localeCountry: String? = null,
                    val localeLanguage: String? = null,
                    val isReturningUser: Boolean? = null,
                    val isPrivacyProEligible: Boolean? = null,
                    val entitlement: String? = null,
                    val minSdkVersion: Int? = null,
                )
                data class Cohort(
                    val name: String? = null,
                    val weight: Int? = null,
                )
            }
            fun isEnabled(): Boolean
            fun setRawStoredState(state: State)
            fun getRawStoredState(): State?
        }
        enum class DefaultFeatureValue { TRUE, FALSE, INTERNAL }
        """.trimIndent(),
    )

    private val featureSettingsStub = SourceFile.kotlin(
        "FeatureSettings.kt",
        """
        package com.duckduckgo.feature.toggles.api
        object FeatureSettings {
            interface Store {
                fun store(jsonString: String)
            }
            val EMPTY_STORE = object : Store {
                override fun store(jsonString: String) {}
            }
        }
        """.trimIndent(),
    )

    private val featureExceptionStub = SourceFile.kotlin(
        "FeatureException.kt",
        """
        package com.duckduckgo.feature.toggles.api
        data class FeatureException(val domain: String, val reason: String?)
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

    private val featureTogglesStub = SourceFile.kotlin(
        "FeatureToggles.kt",
        """
        package com.duckduckgo.feature.toggles.api
        class FeatureToggles {
            class Builder {
                fun store(store: Toggle.Store) = this
                fun appVersionProvider(p: () -> Int) = this
                fun flavorNameProvider(p: () -> String) = this
                fun featureName(n: String) = this
                fun appVariantProvider(p: () -> String?) = this
                fun callback(c: Any) = this
                fun forceDefaultVariantProvider(p: () -> Unit) = this
                fun build() = this
                fun <T> create(clazz: Class<T>): T = TODO()
            }
        }
        """.trimIndent(),
    )

    private val featureTogglesInventoryStub = SourceFile.kotlin(
        "FeatureTogglesInventory.kt",
        """
        package com.duckduckgo.feature.toggles.api
        interface FeatureTogglesInventory {
            suspend fun getAll(): List<Toggle>
        }
        """.trimIndent(),
    )

    @Test
    fun `basic feature with one toggle generates RemoteFeature and ProxyModule`() {
        val source = SourceFile.kotlin(
            "TestFeature.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
            import com.duckduckgo.di.scopes.AppScope
            import com.duckduckgo.feature.toggles.api.Toggle
            import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
            import com.duckduckgo.feature.toggles.api.DefaultFeatureValue

            @ContributesRemoteFeature(
                scope = AppScope::class,
                featureName = "testFeature",
            )
            interface TestFeature {
                @DefaultValue(DefaultFeatureValue.FALSE)
                fun self(): Toggle

                @DefaultValue(DefaultFeatureValue.FALSE)
                fun fooFeature(): Toggle
            }
            """.trimIndent(),
        )

        val result = compile(
            source,
            appScopeStub,
            toggleStub,
            featureSettingsStub,
            featureExceptionStub,
            remoteFeatureStoreNamedStub,
            featureTogglesStub,
            featureTogglesInventoryStub,
        )
        val remoteFeature = result.findGeneratedSource("TestFeature_RemoteFeature.kt")
        val goldenRemoteFeature = loadGolden("RemoteFeature_Basic.kt")
        assertEquals(goldenRemoteFeature, remoteFeature)

        val proxyModule = result.findGeneratedSource("TestFeature_ProxyModule.kt")
        val goldenProxyModule = loadGolden("RemoteFeature_Basic_ProxyModule.kt")
        assertEquals(goldenProxyModule, proxyModule)
    }

    @Test
    fun `error when not an interface`() {
        val source = SourceFile.kotlin(
            "BadFeature.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
            import com.duckduckgo.di.scopes.AppScope

            @ContributesRemoteFeature(
                scope = AppScope::class,
                featureName = "badFeature",
            )
            class BadFeature
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, toggleStub, featureSettingsStub, remoteFeatureStoreNamedStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("must be an interface"))
    }

    @Test
    fun `error when function without DefaultValue annotation`() {
        val source = SourceFile.kotlin(
            "MissingAnnotation.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
            import com.duckduckgo.di.scopes.AppScope
            import com.duckduckgo.feature.toggles.api.Toggle
            import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
            import com.duckduckgo.feature.toggles.api.DefaultFeatureValue

            @ContributesRemoteFeature(
                scope = AppScope::class,
                featureName = "testFeature",
            )
            interface MissingAnnotationFeature {
                @DefaultValue(DefaultFeatureValue.FALSE)
                fun self(): Toggle

                fun missingAnnotation(): Toggle
            }
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, toggleStub, featureSettingsStub, remoteFeatureStoreNamedStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("must be annotated with [Toggle.DefaultValue]"))
    }

    @Test
    fun `error when missing self function`() {
        val source = SourceFile.kotlin(
            "NoSelfFeature.kt",
            """
            package com.test
            import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
            import com.duckduckgo.di.scopes.AppScope
            import com.duckduckgo.feature.toggles.api.Toggle
            import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
            import com.duckduckgo.feature.toggles.api.DefaultFeatureValue

            @ContributesRemoteFeature(
                scope = AppScope::class,
                featureName = "testFeature",
            )
            interface NoSelfFeature {
                @DefaultValue(DefaultFeatureValue.FALSE)
                fun fooFeature(): Toggle
            }
            """.trimIndent(),
        )

        val result = compile(source, appScopeStub, toggleStub, featureSettingsStub, remoteFeatureStoreNamedStub)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("must have a function self()"))
    }

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp {
                symbolProcessorProviders += ContributesRemoteFeatureProcessorProvider()
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
