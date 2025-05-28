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

package com.duckduckgo.feature.toggles.codegen

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.*
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.duckduckgo.feature.toggles.codegen.ContributesRemoteFeatureCodeGeneratorTest.Cohorts.BLUE
import com.duckduckgo.feature.toggles.codegen.ContributesRemoteFeatureCodeGeneratorTest.Cohorts.CONTROL
import com.duckduckgo.feature.toggles.fakes.FakeFeatureTogglesCallback
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.Lazy
import dagger.SingleInstanceIn
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class ContributesRemoteFeatureCodeGeneratorTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var testFeature: TestTriggerFeature
    private lateinit var anotherTestFeature: AnotherTestTriggerFeature
    private val appBuildConfig: AppBuildConfig = mock()
    private lateinit var variantManager: FakeVariantManager
    private lateinit var toggleStore: FakeToggleStore
    private val featureTogglesCallback = FakeFeatureTogglesCallback()

    @Before
    fun setup() {
        variantManager = FakeVariantManager()
        toggleStore = FakeToggleStore()
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        testFeature = FeatureToggles.Builder(
            toggleStore,
            featureName = "testFeature",
            appVersionProvider = { appBuildConfig.versionCode },
            flavorNameProvider = { appBuildConfig.flavor.name },
            appVariantProvider = { variantManager.getVariantKey() },
            forceDefaultVariant = { variantManager.updateVariants(emptyList()) },
            callback = featureTogglesCallback,
        ).build().create(TestTriggerFeature::class.java)
        anotherTestFeature = FeatureToggles.Builder(
            toggleStore,
            featureName = "testFeature",
            appVersionProvider = { appBuildConfig.versionCode },
            flavorNameProvider = { appBuildConfig.flavor.name },
            appVariantProvider = { variantManager.getVariantKey() },
            forceDefaultVariant = { variantManager.updateVariants(emptyList()) },
        ).build().create(AnotherTestTriggerFeature::class.java)
    }

    @Test
    fun `the class is generated`() = runTest {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
        assertNotNull(generatedClass)
    }

    @Test
    fun `the class is generated implements Toggle Store and PrivacyFeaturePlugin`() = runTest {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        assertEquals(2, generatedClass.java.interfaces.size)
        assertTrue(generatedClass.java.interfaces.contains(Toggle.Store::class.java))
        assertTrue(generatedClass.java.interfaces.contains(PrivacyFeaturePlugin::class.java))
    }

    @Test
    fun `the class factory is generated`() = runTest {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature_Factory")
        assertNotNull(generatedClass)
    }

    @Test
    fun `the generated class is singleInstance annotated in the right scope`() = runTest {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(SingleInstanceIn::class.java)!!
        assertEquals(TriggerTestScope::class, annotation.scope)
    }

    @Test
    fun `the generated class is RemoteFeatureStoreNamed annotated in the right scope`() = runTest {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(RemoteFeatureStoreNamed::class.java)!!
        assertEquals(TestTriggerFeature::class, annotation.value)
    }

    @Test
    @Ignore("ContributesBinding is only present in kotlin metadata now, we need to fix")
    fun `the generated class contributes the toggle store binding`() = runTest {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(ContributesBinding::class.java)!!
        assertEquals(TriggerTestScope::class, annotation.scope)
        assertEquals(Toggle.Store::class, annotation.boundType)
    }

    @Test
    @Ignore("ContributesMultibinding is only present in kotlin metadata now, we need to fix")
    fun `the generated class contributes the privacy plugin multibinding`() = runTest {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(ContributesMultibinding::class.java)!!
        assertEquals(TriggerTestScope::class, annotation.scope)
        assertEquals(PrivacyFeaturePlugin::class, annotation.boundType)
        assertTrue(annotation.ignoreQualifier)
    }

    @Test
    fun `re-evaluate feature state when feature hash is null`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "disabled",
                    "features": {
                        "missingFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "enabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "enabled",
                    "features": {
                        "missingFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "disabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `do not re-evaluate feature state if hash hasn't changed`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "missingFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "enabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "enabled",
                    "features": {
                        "missingFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "disabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `re-evaluate feature state if hash changed`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "missingFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "enabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "enabled",
                    "features": {
                        "missingFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "disabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `re-evaluate feature when already preset in remote config but just added to client`() = runTest {
        fun createAnotherFooFeature(): Any {
            return Class
                .forName("com.duckduckgo.feature.toggles.codegen.AnotherTestTriggerFeature_RemoteFeature")
                .getConstructor(
                    FeatureSettings.Store::class.java,
                    dagger.Lazy::class.java as Class<*>,
                    AppBuildConfig::class.java,
                    VariantManager::class.java,
                    Context::class.java,
                ).newInstance(
                    FeatureSettings.EMPTY_STORE,
                    Lazy { anotherTestFeature },
                    appBuildConfig,
                    variantManager,
                    context,
                )
        }

        assertFalse(anotherTestFeature.newFooFeature().isEnabled())

        assertTrue(
            (generatedFeatureNewInstance() as PrivacyFeaturePlugin).store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "newFooFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "enabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertFalse(anotherTestFeature.newFooFeature().isEnabled())

        assertTrue(
            (createAnotherFooFeature() as PrivacyFeaturePlugin).store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "newFooFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "enabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(anotherTestFeature.newFooFeature().isEnabled())
    }

    @Test
    fun `when sub-feature is present remotely but missing locally continue without error`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "disabled",
                    "features": {
                        "missingFeature": {
                            "state": "enabled"
                        },
                        "fooFeature": {
                            "state": "enabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `fresh install and later update returns correct feature values`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        // update remote config
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "enabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test internal always enabled annotation`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "disabled",
                    "features": {
                        "internalDefaultFalse": {
                            "state": "disabled"
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.internalDefaultFalse().isEnabled())

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())

        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.internalDefaultFalse().isEnabled())

        whenever(appBuildConfig.flavor).thenReturn(FDROID)
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.internalDefaultFalse().isEnabled())
    }

    @Test // see https://app.asana.com/0/0/1205806409373059/1205806409373112/f
    fun `test internal always enabled truth table`() = runTest {
        val feature = generatedFeatureNewInstance()
        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // Order mattesr, we need to start with the config that does not have any features
        var jsonString = """
                {
                    "state": "disabled",
                    "features": {}
                }
        """.trimIndent()
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertFalse(testFeature.internalDefaultFalse().isEnabled())
        assertFalse(testFeature.defaultFalse().isEnabled())
        assertFalse(testFeature.defaultValueInternal().isEnabled())

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertFalse(testFeature.defaultFalse().isEnabled())
        // the default value doesn't get re-evaluated, it's assigned when toggle is first created. That's why this returns FALSE
        assertFalse(testFeature.defaultValueInternal().isEnabled())

        jsonString = """
                {
                    "state": "disabled",
                    "features": {
                        "internalDefaultTrue": {
                            "state": "enabled"
                        }, 
                        "defaultTrue": {
                            "state": "enabled"
                        },
                        "internalDefaultFalse": {
                            "state": "enabled"
                        }, 
                        "defaultFalse": {
                            "state": "enabled"
                        }, 
                        "defaultValueInternal": {
                            "state": "enabled"
                        }
                    }
                }
        """.trimIndent()
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        assertTrue(privacyPlugin.store("testFeature", jsonString))

        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertTrue(testFeature.defaultFalse().isEnabled())
        assertTrue(testFeature.defaultValueInternal().isEnabled())

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertTrue(testFeature.defaultFalse().isEnabled())
        assertTrue(testFeature.defaultValueInternal().isEnabled())

        jsonString = """
                {
                    "state": "disabled",
                    "features": {
                        "internalDefaultTrue": {
                            "state": "disabled"
                        }, 
                        "defaultTrue": {
                            "state": "disabled"
                        },
                        "internalDefaultFalse": {
                            "state": "disabled"
                        }, 
                        "defaultFalse": {
                            "state": "disabled"
                        }, 
                        "defaultValueInternal": {
                            "state": "disabled"
                        }
                    }
                }
        """.trimIndent()
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertFalse(testFeature.internalDefaultTrue().isEnabled())
        assertFalse(testFeature.defaultTrue().isEnabled())
        assertFalse(testFeature.internalDefaultFalse().isEnabled())
        assertFalse(testFeature.defaultFalse().isEnabled())
        assertFalse(testFeature.defaultValueInternal().isEnabled())

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertFalse(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertFalse(testFeature.defaultFalse().isEnabled())
        assertFalse(testFeature.defaultValueInternal().isEnabled())

        jsonString = """
                {
                    "state": "disabled",
                    "features": {
                        "internalDefaultTrue": {
                            "state": "internal"
                        }, 
                        "defaultTrue": {
                            "state": "internal"
                        },
                        "internalDefaultFalse": {
                            "state": "internal"
                        }, 
                        "defaultFalse": {
                            "state": "internal"
                        }, 
                        "defaultValueInternal": {
                            "state": "internal"
                        }
                    }
                }
        """.trimIndent()
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertFalse(testFeature.internalDefaultTrue().isEnabled())
        assertFalse(testFeature.defaultTrue().isEnabled())
        assertFalse(testFeature.internalDefaultFalse().isEnabled())
        assertFalse(testFeature.defaultFalse().isEnabled())
        assertFalse(testFeature.defaultValueInternal().isEnabled())

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertTrue(testFeature.defaultFalse().isEnabled())
        assertTrue(testFeature.defaultValueInternal().isEnabled())
    }

    @Test
    fun `test default value set to internal`() = runTest {
        val feature = generatedFeatureNewInstance()
        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // Order matters, we need to start with the config that does not have any features
        var jsonString = """
                {
                    "state": "disabled",
                    "features": {}
                }
        """.trimIndent()

        assertTrue(privacyPlugin.store("testFeature", jsonString))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(testFeature.defaultValueInternal().isEnabled())

        jsonString = """
                {
                    "state": "disabled",
                    "features": {
                        "defaultValueInternal": {
                            "state": "enabled"
                        }
                    }
                }
        """.trimIndent()
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.defaultValueInternal().isEnabled())

        jsonString = """
                {
                    "state": "disabled",
                    "features": {
                        "defaultValueInternal": {
                            "state": "disabled"
                        }
                    }
                }
        """.trimIndent()
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertFalse(testFeature.defaultValueInternal().isEnabled())

        jsonString = """
                {
                    "state": "disabled",
                    "features": {
                        "defaultValueInternal": {
                            "state": "internal"
                        }
                    }
                }
        """.trimIndent()
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.defaultValueInternal().isEnabled())
    }

    @Test
    fun `test staged rollout for default-enabled feature flag`() = runTest {
        val feature = generatedFeatureNewInstance()
        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "enabled",
                    "features": {
                        "defaultTrue": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 0.1
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.defaultTrue().isEnabled())
    }

    @Test
    fun `the disable state of the feature always wins`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 0
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `the rollout step set to 0 disables the feature`() = runTest {
        val jsonFeature = """
        {
            "state": "enabled",
            "features": {
                "fooFeature": {
                    "state": "enabled",
                    "rollout": {
                        "steps": [
                            {
                                "percent": 0
                            }                    
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(privacyPlugin.store("testFeature", jsonFeature))
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `the parent feature disabled doesn't interfer with the sub-feature state`() = runTest {
        val jsonFeature = """
        {
            "state": "disabled",
            "features": {
                "fooFeature": {
                    "state": "enabled",
                    "rollout": {
                        "steps": [
                            {
                                "percent": 100
                            }                    
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(privacyPlugin.store("testFeature", jsonFeature))
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `the features have the right state for internal builds`() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        val jsonFeature = """
        {
            "state": "internal",
            "features": {
                "fooFeature": {
                    "state": "internal",
                    "rollout": {
                        "steps": [
                            {
                                "percent": 0
                            }                    
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(privacyPlugin.store("testFeature", jsonFeature))
        assertTrue(testFeature.self().isEnabled())
        // even though the state = internal, it has rollout steps, even if it is '0' that is ignored, which results in disable state
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `the feature incremental steps are ignored when feature disabled`() = runTest {
        val jsonFeature = """
        {
            "state": "enabled",
            "features": {
                "fooFeature": {
                    "state": "disabled",
                    "rollout": {
                        "steps": [
                            {
                                "percent": 1
                            },                    
                            {
                                "percent": 2
                            },                    
                            {
                                "percent": 100
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(privacyPlugin.store("testFeature", jsonFeature))
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertNull(testFeature.fooFeature().rolloutThreshold())
    }

    @Test
    fun `the feature incremental steps are executed when feature is enabled`() = runTest {
        val jsonFeature = """
        {
            "state": "enabled",
            "features": {
                "fooFeature": {
                    "state": "enabled",
                    "rollout": {
                        "steps": [
                            {
                                "percent": 0.5
                            },                    
                            {
                                "percent": 1.5
                            },                    
                            {
                                "percent": 2
                            },                    
                            {
                                "percent": 100
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(privacyPlugin.store("testFeature", jsonFeature))
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().rolloutThreshold()!! < testFeature.fooFeature().getRawStoredState()!!.rollout!!.last())
    }

    @Test
    fun `the invalid rollout steps are ignored and not executed`() = runTest {
        val jsonFeature = """
        {
            "state": "enabled",
            "features": {
                "fooFeature": {
                    "state": "enabled",
                    "rollout": {
                        "steps": [
                            {
                                "percent": -1
                            },
                            {
                                "percent": 100
                            },
                            {
                                "percent": 200
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        assertTrue(privacyPlugin.store("testFeature", jsonFeature))

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().rolloutThreshold()!! < testFeature.fooFeature().getRawStoredState()!!.rollout!!.last())
    }

    @Test
    fun `disable a previously enabled incremental rollout`() = runTest {
        val jsonFeature = """
        {
            "state": "enabled",
            "features": {
                "fooFeature": {
                    "state": "enabled",
                    "rollout": {
                        "steps": [
                            {
                                "percent": 100
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val jsonDisabled = """
        {
            "state": "enabled",
            "features": {
                "fooFeature": {
                    "state": "disabled",
                    "rollout": {
                        "steps": [
                            {
                                "percent": 100
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        assertTrue(privacyPlugin.store("testFeature", jsonFeature))

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().rolloutThreshold()!! < testFeature.fooFeature().getRawStoredState()!!.rollout!!.last())

        assertTrue(privacyPlugin.store("testFeature", jsonDisabled))
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().rolloutThreshold()!! < testFeature.fooFeature().getRawStoredState()!!.rollout!!.last())
    }

    @Test
    fun `re-enable a previously disabled incremental rollout`() = runTest {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        // incremental rollout
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": 100
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        // disable the previously enabled incremental rollout
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "disabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": 100
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().rolloutThreshold()!! < testFeature.fooFeature().getRawStoredState()!!.rollout!!.last())

        // re-enable the incremental rollout
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": 100
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().rolloutThreshold()!! < testFeature.fooFeature().getRawStoredState()!!.rollout!!.last())
    }

    @Test
    fun `feature was enabled remains enabled and rollout threshold is set`() = runTest {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        // set up initial state, incremental rollout and enabled
        toggleStore.set(
            "testFeature_fooFeature",
            Toggle.State(
                remoteEnableState = true,
                enable = true,
                rollout = listOf(50.0),
            ),
        )
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": 50
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        val rolloutThreshold = testFeature.fooFeature().rolloutThreshold()!!
        assertEquals(rolloutThreshold < 50.0, testFeature.fooFeature().isEnabled())
    }

    @Test
    // see https://app.asana.com/0/488551667048375/1206413338208929
    fun `backwards compatibility test - feature was disabled set rollout threshold`() = runTest {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        // set up initial state, incremental rollout and enabled
        toggleStore.set(
            "testFeature_fooFeature",
            Toggle.State(
                remoteEnableState = true,
                enable = false,
                rollout = listOf(50.0),
            ),
        )
        val step = 50.0
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": $step
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        val threshold = testFeature.fooFeature().rolloutThreshold()
        assertNotNull(threshold)
        assertEquals(step >= threshold!!, testFeature.fooFeature().isEnabled())
    }

    @Test
    // see https://app.asana.com/0/488551667048375/1206413338208929
    fun `backwards compatibility test - feature was null set rollout threshold`() = runTest {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        // set up initial state, incremental rollout and enabled
        val step = 50.0
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": $step
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        val threshold = testFeature.fooFeature().rolloutThreshold()
        assertNotNull(threshold)
        assertEquals(step >= threshold!!, testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `full feature lifecycle`() = runTest {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "disabled",
                        "features": {
                            "fooFeature": {
                                "state": "disabled"
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertNull(testFeature.fooFeature().rolloutThreshold())

        // enable parent feature
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "disabled"
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertNull(testFeature.fooFeature().rolloutThreshold())

        // add rollout information to sub-feature, still disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "disabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": 10
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertNull(testFeature.fooFeature().rolloutThreshold())

        // add more rollout information to sub-feature, still disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "disabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": 10
                                        },
                                        {
                                            "percent": 20
                                        },
                                        {
                                            "percent": 30
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertNull(testFeature.fooFeature().rolloutThreshold())

        // enable rollout
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": 0
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        val rolloutThreshold = testFeature.fooFeature().rolloutThreshold()!!
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        // increment rollout but just disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "disabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": ${rolloutThreshold - 1.0}
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        // increment rollout but just disabled, still
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": $rolloutThreshold
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        // increment rollout but just enabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": ${rolloutThreshold + 1.0}
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        // halt rollout
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "disabled",
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": ${rolloutThreshold + 1.0}
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(rolloutThreshold, testFeature.fooFeature().rolloutThreshold())

        // resume rollout just of certain app versions
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "minSupportedVersion": 2,
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": ${rolloutThreshold + 1.0}
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(rolloutThreshold, testFeature.fooFeature().rolloutThreshold())

        // resume rollout and update app version
        whenever(appBuildConfig.versionCode).thenReturn(2)
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "disabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "minSupportedVersion": 2,
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": ${rolloutThreshold + 1.0}
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(rolloutThreshold, testFeature.fooFeature().rolloutThreshold())

        // finish rollout
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "minSupportedVersion": 2,
                                "rollout": {
                                    "steps": [
                                        {
                                            "percent": ${rolloutThreshold + 1.0}
                                        },
                                        {
                                            "percent": 100
                                        }
                                    ]
                                }
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(rolloutThreshold, testFeature.fooFeature().rolloutThreshold())

        // remove steps
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "minSupportedVersion": 2
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test feature with multiple targets matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale(Locale.FRANCE.language, Locale.US.country)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc",
                                        "localeCountry": "US",
                                        "localeLanguage": "fr"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(Toggle.State.Target("mc", "US", "fr", null, null, null)),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test multiple languages`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale(Locale.FRANCE.language, Locale.US.country)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "localeCountry": "US",
                                        "localeLanguage": "en"
                                    },
                                    {
                                        "localeCountry": "FR",
                                        "localeLanguage": "fr"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target(null, "US", "en", null, null, null),
                Toggle.State.Target(null, "FR", "fr", null, null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )

        featureTogglesCallback.locale = Locale.US
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target(null, "US", "en", null, null, null),
                Toggle.State.Target(null, "FR", "fr", null, null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )

        featureTogglesCallback.locale = Locale.FRANCE
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target(null, "US", "en", null, null, null),
                Toggle.State.Target(null, "FR", "fr", null, null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple targets not matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale.FRANCE

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc",
                                        "localeCountry": "US",
                                        "localeLanguage": "fr"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        // foo feature is not an experiment and the target has a variantKey. As this is a mistake, that target is invalidated, hence assertTrue
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(Toggle.State.Target("mc", "US", "fr", null, null, null)),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale(Locale.FRANCE.language, Locale.US.country))

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    },
                                    {
                                        "localeCountry": "US"
                                    },
                                    {
                                        "localeLanguage": "fr"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", null, null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null, null),
                Toggle.State.Target(null, null, "fr", null, null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets not matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale.FRANCE

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    },
                                    {
                                        "localeCountry": "US"
                                    },
                                    {
                                        "localeLanguage": "zh"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", null, null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null, null),
                Toggle.State.Target(null, null, "zh", null, null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets not matching and minSdkVersion not matching as sdkVersion is lower than minSdkVersion`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale.FRANCE
        featureTogglesCallback.sdkVersion = 28

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    },
                                    {
                                        "localeCountry": "US"
                                    },
                                    {
                                        "minSdkVersion": 30
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", null, null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null, null),
                Toggle.State.Target(null, null, null, null, null, 30),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets not matching and minSdkVersion matching as sdkVersion is the same as minSdkVersion`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale.FRANCE
        featureTogglesCallback.sdkVersion = 28

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    },
                                    {
                                        "localeCountry": "US"
                                    },
                                    {
                                        "minSdkVersion": 28
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", null, null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null, null),
                Toggle.State.Target(null, null, null, null, null, 28),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets not matching and minSdkVersion matching as sdkVersion is higher than minSdkVersion`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale.FRANCE
        featureTogglesCallback.sdkVersion = 30

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    },
                                    {
                                        "localeCountry": "US"
                                    },
                                    {
                                        "minSdkVersion": 28
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", null, null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null, null),
                Toggle.State.Target(null, null, null, null, null, 28),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets matching and minSdkVersion matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        featureTogglesCallback.locale = Locale.US
        featureTogglesCallback.sdkVersion = 30

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    },
                                    {
                                        "localeCountry": "US"
                                    },
                                    {
                                        "minSdkVersion": 28
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", null, null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null, null),
                Toggle.State.Target(null, null, null, null, null, 28),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test variant parsing when no remote variant provided`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled"
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(emptyList<Toggle.State.Target>(), testFeature.fooFeature().getRawStoredState()!!.targets)
    }

    @Test
    fun `test variant parsing`() = runTest {
        variantManager.variant = "mc"
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "fooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "ma"
                                    },
                                    {
                                        "variantKey": "mb"
                                    }
                                ]
                            },
                            "experimentFooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "ma"
                                    },
                                    {
                                        "variantKey": "mb"
                                    }
                                ]
                            },
                            "variantFeature": {
                                "state": "disabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        // true because it's not an experiment and so variants are ignored
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
        // false because it is an experiment and so variants are considered
        assertFalse(testFeature.experimentFooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentFooFeature().getRawStoredState()!!.targets,
        )

        assertFalse(testFeature.variantFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.variantFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test variant when assigned variant key is null`() = runTest {
        variantManager.variant = null
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertFalse(testFeature.self().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertNull(variantManager.variant)
        assertFalse(testFeature.experimentFooFeature().isEnabled())
        // variant is null at this moment, feature flag targets variants, we should assign the default variant
        assertEquals(1, variantManager.saveVariantsCallCounter)
        assertEquals("", variantManager.variant)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "experimentFooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "ma"
                                    },
                                    {
                                        "variantKey": "mb"
                                    }
                                ]
                            },
                            "variantFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        // Remains false as assigned variant now is ""
        assertFalse(testFeature.experimentFooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentFooFeature().getRawStoredState()!!.targets,
        )
        assertTrue(testFeature.variantFeature().isEnabled())
        assertEquals("", variantManager.variant)
        assertEquals(1, variantManager.saveVariantsCallCounter)
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.variantFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test variant when assigned variant key is not null`() = runTest {
        variantManager.variant = "na"
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "experimentFooFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "ma"
                                    },
                                    {
                                        "variantKey": "mb"
                                    }
                                ]
                            },
                            "variantFeature": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals("na", variantManager.variant)
        assertFalse(testFeature.experimentFooFeature().isEnabled())
        // variant is null at this moment, feature flag targets variants, we should assign the default variant
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals("na", variantManager.variant)
        assertEquals(
            listOf(
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentFooFeature().getRawStoredState()!!.targets,
        )
        assertTrue(testFeature.variantFeature().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals("na", variantManager.variant)
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.variantFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature disabled and forces variant when variant is null`() = runTest {
        variantManager.variant = null
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.experimentDisabledByDefault().isEnabled())
        assertEquals(1, variantManager.saveVariantsCallCounter)
        assertEquals("", variantManager.getVariantKey())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature enabled and forces variant when variant is null`() = runTest {
        variantManager.variant = null
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": ""
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.experimentDisabledByDefault().isEnabled())
        assertEquals(1, variantManager.saveVariantsCallCounter)
        assertEquals("", variantManager.getVariantKey())
        assertEquals(
            listOf(
                Toggle.State.Target("", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature does not force variant when already assigned`() = runTest {
        variantManager.variant = "mc"
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.experimentDisabledByDefault().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals("mc", variantManager.getVariantKey())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test experiment feature with ignored targets`() = runTest {
        variantManager.variant = "mc"
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.FRANCE)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "hash": "1",
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc",
                                        "localeCountry": "US"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.experimentDisabledByDefault().isEnabled()) // true because experiments only check variantKey
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = "US", localeLanguage = null, null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "hash": "2",
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc",
                                        "localeLanguage": "US"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.experimentDisabledByDefault().isEnabled()) // true because experiments only check variantKey
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = "US", null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "hash": "3",
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.experimentDisabledByDefault().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "hash": "4",
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "ma"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.experimentDisabledByDefault().isEnabled()) // true because experiments only check variantKey
        assertEquals(
            listOf(
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test experiment feature with targets matching`() = runTest {
        variantManager.variant = "mc"
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)

        // all disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                    {
                        "state": "enabled",
                        "features": {
                            "experimentDisabledByDefault": {
                                "state": "enabled",
                                "targets": [
                                    {
                                        "variantKey": "mc",
                                        "localeCountry": "US"
                                    }
                                ]
                            }
                        }
                    }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.experimentDisabledByDefault().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = "US", localeLanguage = null, null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test rollout roll back`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        val fooFeatureRolloutPercentile = testFeature.fooFeature().getRawStoredState()?.rolloutThreshold!!
        val justEnableRollout = (fooFeatureRolloutPercentile + 1).coerceAtMost(100.0)
        val justDisabledRollout = (fooFeatureRolloutPercentile - 1).coerceAtLeast(0.0)

        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        // Roll back to 0% but as fooFeature was enabled before it should remain enabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "enabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 0
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        // Disable fooFeature, should disable the feature
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 0
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        // Roll fooFeature back to 100% with state still disabled, should remain disabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "4",
                    "state": "enabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        // re-enable fooFeature, should be enabled
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "5",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())

        // disable feature
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "6",
                    "state": "enabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        // re-enable but roll back, should disable fooFeature
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "7",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enable",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": $justDisabledRollout
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())

        // roll out just enough, should enable fooFeature
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "8",
                    "state": "enabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": $justEnableRollout
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        // ensure it hasn't change
        assertEquals(
            fooFeatureRolloutPercentile,
            testFeature.fooFeature().getRawStoredState()!!.rolloutThreshold,
        )
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test cohorts json parsing`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                },
                                {
                                    "name": "red",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        val cohorts = testFeature.fooFeature().getRawStoredState()?.cohorts!!
        assertTrue(cohorts.size == 3)
        assertEquals(Cohort("control", 1), cohorts[0])
        assertEquals(Cohort("blue", 1), cohorts[1])
        assertEquals(Cohort("red", 1), cohorts[2])
    }

    @Test
    fun `test cohort only assigned when calling isEnabled(cohort)`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should assign cohort
        assertTrue(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
    }

    @Test
    fun `test cohort not assigned when remote feature is enabled and minSupportedVersion not matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(1)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should NOT assign cohort
        assertFalse(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test cohort not assigned when remote feature is disabled and minSupportedVersion is matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled",
                            "minSupportedVersion": 2,
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should NOT assign cohort
        assertFalse(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test cohort is assigned when remote feature is enabled and minSupportedVersion is matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should assign cohort
        assertTrue(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test cohort is not assigned when flavor not matching is enabled and minSupportedVersion is matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "internal",
                            "minSupportedVersion": 2,
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should NOT assign cohort
        assertFalse(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test cohort is assigned when flavor is matching is enabled and minSupportedVersion is matching`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "internal",
                            "minSupportedVersion": 2,
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should assign cohort
        assertTrue(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test cohort is assigned when feature is rolled-out`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)

        // This is just force setting rolloutThreshold
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        val rolloutThreshold = testFeature.fooFeature().getRawStoredState()?.rolloutThreshold!!

        // roll back just above the threshold
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": ${rolloutThreshold + 1}
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should assign cohort
        assertTrue(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        // should be enrolled
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolled())
        // user in rollout, variant enabled, overall enabled
        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
    }

    @Test
    fun `test cohort is not unassigned when feature is rolled-out and then rolled back`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)

        // This is just force setting rolloutThreshold
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        val rolloutThreshold = testFeature.fooFeature().getRawStoredState()?.rolloutThreshold!!

        // roll back just above the threshold
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": ${rolloutThreshold + 1}
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled(cohort), then we should assign cohort
        assertTrue(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        // should be enrolled
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolled())
        // user in rollout, variant enabled, overall enabled
        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // rolling back the rollout should not re-enroll or undo the experiment enrollment
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": ${rolloutThreshold - 1}
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
    }

    @Test
    fun `test cohort is not assigned when feature is not rolled-out`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)

        // This is just force setting rolloutThreshold
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        val rolloutThreshold = testFeature.fooFeature().getRawStoredState()?.rolloutThreshold!!

        // roll back just below the threshold, aka user doesn't enter rollout
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": ${rolloutThreshold - 1}
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // test the user has not entered rollout
        assertFalse(testFeature.fooFeature().isEnabled())

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        // should NOT be enrolled
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        // user not in rollout, all false
        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        // parent feature remains disabled
        assertFalse(testFeature.fooFeature().isEnabled())

        // we call isEnabled(cohort), then we should NOT assign cohort
        assertFalse(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        // should NOT be enrolled
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        // user not in rollout, all false
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test cohort is only assigned when targets match`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)
        whenever(appBuildConfig.versionCode).thenReturn(2)
        featureTogglesCallback.locale = Locale(Locale.US.language, Locale.FRANCE.country)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeLanguage": "${Locale.FRANCE.language}",
                                    "localeCountry": "${Locale.FRANCE.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // targets don't match, feature disabled
        assertFalse(testFeature.fooFeature().isEnabled())

        // we haven't called isEnabled yet, so cohorts should not be yet assigned
        var rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        // should NOT be enrolled
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        // user not in rollout, all false
        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        // parent feature remains disabled
        assertFalse(testFeature.fooFeature().isEnabled())

        // we call isEnabled(cohort), but targets don't match, cohort should NOT be assigned either
        assertFalse(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        // should NOT be enrolled
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        // user not in rollout, all false
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled())

        featureTogglesCallback.locale = Locale(Locale.US.language, Locale.FRANCE.country)
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeLanguage": "${Locale.US.language}",
                                    "localeCountry": "${Locale.US.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertEquals(2, rawState?.cohorts?.size)
        // should NOT be enrolled
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnrolled())
        // user not in rollout, all false
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled())

        featureTogglesCallback.locale = Locale(Locale.US.language, Locale.US.country)
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "minSupportedVersion": 2,
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeLanguage": "${Locale.US.language}",
                                    "localeCountry": "${Locale.US.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.fooFeature().enroll())
        rawState = testFeature.fooFeature().getRawStoredState()
        assertEquals(2, rawState?.cohorts?.size)
        // targets match, should be enrolled
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test remove all cohorts remotely removes assigned cohort`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.fooFeature().enroll())
        assertNotNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // remove blue cohort
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertNotNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())

        // remove all remaining cohorts
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
    }

    @Test
    fun `test disabling feature disables cohort`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.fooFeature().enroll())
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "disabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "4",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
    }

    @Test
    fun `test cohort targets`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        featureTogglesCallback.locale = Locale(Locale.FRANCE.language, Locale.US.country)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeLanguage": "${Locale.FRANCE.language}",
                                    "localeCountry": "${Locale.FRANCE.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        featureTogglesCallback.locale = Locale(Locale.US.language, Locale.FRANCE.country)
        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        featureTogglesCallback.locale = Locale.US
        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        featureTogglesCallback.locale = Locale.FRANCE
        assertTrue(testFeature.fooFeature().enroll())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // once cohort is assigned, changing targets shall not affect feature state
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeCountry": "${Locale.FRANCE.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().enroll())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // remove all cohorts to clean state
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeCountry": "${Locale.FRANCE.country}"
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)

        featureTogglesCallback.locale = Locale(Locale.FRANCE.language, Locale.FRANCE.country)
        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)

        // re-populate experiment to re-assign new cohort, should not be assigned as it has wrong targets
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "4",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeCountry": "${Locale.FRANCE.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 0
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        featureTogglesCallback.locale = Locale(Locale.FRANCE.language, Locale.US.country)

        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)

        featureTogglesCallback.locale = Locale(Locale.FRANCE.language, Locale.FRANCE.country)
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)

        assertTrue(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())
    }

    @Test
    fun `test change remote cohorts after assignment should noop`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeCountry": "${Locale.US.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(testFeature.fooFeature().enroll())
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // changing cohort targets should not change cohort assignment
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeCountry": "${Locale.FRANCE.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(CONTROL.cohortName, testFeature.fooFeature().getCohort()?.name)
        // False because we check targets for isEnabled
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())

        // changing cohort weight should not change current assignment
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeCountry": "${Locale.US.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 0
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(CONTROL.cohortName, testFeature.fooFeature().getCohort()?.name)
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())

        // adding cohorts should not change current assignment
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "4",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "targets": [
                                {
                                    "localeCountry": "${Locale.US.country}"
                                }
                            ],
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                },
                                {
                                    "name": "red",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(CONTROL.cohortName, testFeature.fooFeature().getCohort()?.name)
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())
    }

    @Test
    fun `test enrollment date`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        // call isEnabled() to force a State
        testFeature.fooFeature().isEnabled()
        assertNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)

        // call isEnabled(cohort) to force cohort assignment
        assertTrue(testFeature.fooFeature().enroll())

        val date = testFeature.fooFeature().getRawStoredState()!!.assignedCohort?.enrollmentDateET
        val parsedDate = ZonedDateTime.parse(date).truncatedTo(ChronoUnit.DAYS)
        val now = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS)
        assertEquals(now, parsedDate)
    }

    @Test
    fun `test calling is enrolled and enabled does not enroll`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertFalse(testFeature.fooFeature().isEnrolled())
        assertTrue(testFeature.fooFeature().enroll())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())
    }

    @Test
    fun `test rollback cohort experiments`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        val rolloutThreshold = testFeature.fooFeature().getRawStoredState()?.rolloutThreshold!!
        assertTrue(testFeature.fooFeature().enroll())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": ${rolloutThreshold - 1}
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
        assertTrue(testFeature.fooFeature().isEnrolled())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
    }

    @Test
    fun `test cohort enabled and stop enrollment and then roll-back`() = runTest {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        var cohorts = testFeature.fooFeature().getRawStoredState()?.cohorts!!
        assertTrue(cohorts.size == 2)
        assertEquals(Cohort("control", 1), cohorts[0])
        assertEquals(Cohort("blue", 0), cohorts[1])

        assertTrue(testFeature.fooFeature().isEnabled())
        assertTrue(testFeature.fooFeature().enroll())
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // Stop enrollment, should keep assigned cohorts
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 0
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        cohorts = testFeature.fooFeature().getRawStoredState()?.cohorts!!
        assertTrue(cohorts.size == 2)
        assertEquals(Cohort("control", 0), cohorts[0])
        assertEquals(Cohort("blue", 1), cohorts[1])

        assertTrue(testFeature.fooFeature().isEnabled())
        assertFalse(testFeature.fooFeature().enroll())
        // when weight of assigned cohort goes down to "0" we just stop the enrollment, but keep the cohort assignment
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // remove control, should re-allocate to blue
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        cohorts = testFeature.fooFeature().getRawStoredState()?.cohorts!!
        assertTrue(cohorts.size == 1)
        assertEquals(Cohort("blue", 1), cohorts[0])

        assertTrue(testFeature.fooFeature().isEnabled())
        assertFalse(testFeature.fooFeature().enroll())
        // when weight of assigned cohort goes down to "0" we just stop the enrollment, but keep the cohort assignment
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))

        // roll-back
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "4",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 0
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 0
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        cohorts = testFeature.fooFeature().getRawStoredState()?.cohorts!!
        assertTrue(cohorts.size == 2)
        assertEquals(Cohort("control", 0), cohorts[0])
        assertEquals(Cohort("blue", 1), cohorts[1])

        assertFalse(testFeature.fooFeature().isEnabled())
        assertFalse(testFeature.fooFeature().enroll())
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnrolledAndEnabled(BLUE))
    }

    @Test
    fun `test config parsed correctly`() = runTest {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter<Map<String, Any>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
        )
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "1",
                    "state": "disabled",
                    "exceptions": [
                        {
                            "domain": "foo.com"
                        },
                        {
                            "domain": "bar.de",
                            "reason": "bar.de"
                        }
                    ],
                    "settings": {
                        "foo": "foo/value",
                        "bar": {
                            "key": "value",
                            "number": 2,
                            "boolean": true,
                            "complex": {
                                "boolean": true
                            }
                        }
                    },
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "exceptions": [
                                {
                                    "domain": "foo.com"
                                },
                                {
                                    "domain": "baz.de",
                                    "reason": "baz.de"
                                }
                            ],
                            "settings": {
                                "foo": "foo/value",
                                "bar": {
                                    "key": "value",
                                    "number": 2,
                                    "boolean": true,
                                    "complex": {
                                        "boolean": true
                                    }
                                }
                            },
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 1
                                },
                                {
                                    "name": "blue",
                                    "weight": 0
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        val topLevelStateConfig = testFeature.self().getRawStoredState()?.settings?.let { adapter.fromJson(it) } ?: emptyMap()
        val topLevelConfig = testFeature.self().getSettings()?.let { adapter.fromJson(it) } ?: emptyMap()
        val topLevelExceptions = testFeature.self().getExceptions()
        assertTrue(topLevelStateConfig.size == 2)
        assertEquals("foo/value", topLevelStateConfig["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), topLevelStateConfig["bar"])
        assertTrue(topLevelConfig.size == 2)
        assertEquals("foo/value", topLevelConfig["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), topLevelConfig["bar"])
        assertEquals(
            listOf(FeatureException("foo.com", null), FeatureException("bar.de", "bar.de")),
            topLevelExceptions,
        )

        var stateConfig = testFeature.fooFeature().getRawStoredState()?.settings?.let { adapter.fromJson(it) } ?: emptyMap()
        var config = testFeature.fooFeature().getSettings()?.let { adapter.fromJson(it) } ?: emptyMap()
        var exceptions = testFeature.fooFeature().getExceptions()
        assertTrue(stateConfig.size == 2)
        assertEquals("foo/value", stateConfig["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), stateConfig["bar"])
        assertTrue(config.size == 2)
        assertEquals("foo/value", config["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), config["bar"])
        assertEquals(
            listOf(FeatureException("foo.com", null), FeatureException("baz.de", "baz.de")),
            exceptions,
        )

        // Delete config key, should remove
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "2",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "settings": {
                                "foo": "foo/value"                                
                            },
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 0
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        stateConfig = testFeature.fooFeature().getRawStoredState()?.settings?.let { adapter.fromJson(it) } ?: emptyMap()
        config = testFeature.fooFeature().getSettings()?.let { adapter.fromJson(it) } ?: emptyMap()
        exceptions = testFeature.fooFeature().getExceptions()
        assertTrue(stateConfig.size == 1)
        assertEquals("foo/value", stateConfig["foo"])
        assertNull(stateConfig["bar"])
        assertTrue(config.size == 1)
        assertEquals("foo/value", config["foo"])
        assertNull(config["bar"])
        assertTrue(exceptions.isEmpty())

        // delete config, returns empty
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "3",
                    "state": "disabled",
                    "features": {
                        "fooFeature": {
                            "state": "enabled",                           
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 100
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        stateConfig = testFeature.fooFeature().getRawStoredState()?.settings?.let { adapter.fromJson(it) } ?: emptyMap()
        config = testFeature.fooFeature().getSettings()?.let { adapter.fromJson(it) } ?: emptyMap()
        exceptions = testFeature.fooFeature().getExceptions()
        assertTrue(stateConfig.isEmpty())
        assertTrue(config.isEmpty())
        assertTrue(exceptions.isEmpty())

        // re-add config different values
        assertTrue(
            privacyPlugin.store(
                "testFeature",
                """
                {
                    "hash": "4",
                    "state": "disabled",
                    "exceptions": [
                        {
                            "domain": "foo.com"
                        }
                    ],
                    "features": {
                        "fooFeature": {
                            "state": "enabled",
                            "exceptions": [
                                {
                                    "domain": "bar.com"
                                }
                            ],
                            "settings": {
                                "x": "x/value",
                                "y": "y/value"
                            },
                            "rollout": {
                                "steps": [
                                    {
                                        "percent": 0
                                    }                    
                                ]
                            },
                            "cohorts": [
                                {
                                    "name": "control",
                                    "weight": 0
                                },
                                {
                                    "name": "blue",
                                    "weight": 1
                                }
                            ]
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        stateConfig = testFeature.fooFeature().getRawStoredState()?.settings?.let { adapter.fromJson(it) } ?: emptyMap()
        config = testFeature.fooFeature().getSettings()?.let { adapter.fromJson(it) } ?: emptyMap()
        assertTrue(stateConfig.size == 2)
        assertEquals("x/value", stateConfig["x"])
        assertEquals("y/value", stateConfig["y"])
        assertTrue(config.size == 2)
        assertEquals("x/value", config["x"])
        assertEquals("y/value", config["y"])
        assertEquals(
            listOf(FeatureException("foo.com", null)),
            testFeature.self().getExceptions(),
        )
        assertEquals(
            listOf(FeatureException("bar.com", null)),
            testFeature.fooFeature().getExceptions(),
        )
    }

    private fun generatedFeatureNewInstance(): Any {
        return Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .getConstructor(
                FeatureSettings.Store::class.java,
                dagger.Lazy::class.java as Class<*>,
                AppBuildConfig::class.java,
                VariantManager::class.java,
                Context::class.java,
            ).newInstance(
                FeatureSettings.EMPTY_STORE,
                Lazy { testFeature },
                appBuildConfig,
                variantManager,
                context,
            )
    }

    private fun Toggle.rolloutThreshold(): Double? {
        return getRawStoredState()?.rolloutThreshold
    }

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        BLUE("blue"),
    }
}

internal class FakeVariantManager : VariantManager {
    var saveVariantsCallCounter = 0
    var variant: String? = null

    override fun defaultVariantKey(): String {
        TODO("Not yet implemented")
    }

    override fun getVariantKey(): String? {
        return variant
    }

    override fun updateAppReferrerVariant(variant: String) {
        TODO("Not yet implemented")
    }

    override fun updateVariants(variants: List<VariantConfig>) {
        saveVariantsCallCounter++
        variant = ""
    }
}
