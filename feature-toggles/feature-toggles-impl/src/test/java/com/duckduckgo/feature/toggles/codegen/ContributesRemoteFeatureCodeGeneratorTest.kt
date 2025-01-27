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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.*
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureExceptions
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
    fun `the class is generated`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
        assertNotNull(generatedClass)
    }

    @Test
    fun `the class is generated implements Toggle Store and PrivacyFeaturePlugin`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        assertEquals(2, generatedClass.java.interfaces.size)
        assertTrue(generatedClass.java.interfaces.contains(Toggle.Store::class.java))
        assertTrue(generatedClass.java.interfaces.contains(PrivacyFeaturePlugin::class.java))
    }

    @Test
    fun `the class factory is generated`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature_Factory")
        assertNotNull(generatedClass)
    }

    @Test
    fun `the generated class is singleInstance annotated in the right scope`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(SingleInstanceIn::class.java)!!
        assertEquals(TriggerTestScope::class, annotation.scope)
    }

    @Test
    fun `the generated class is RemoteFeatureStoreNamed annotated in the right scope`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(RemoteFeatureStoreNamed::class.java)!!
        assertEquals(TestTriggerFeature::class, annotation.value)
    }

    @Test
    @Ignore("ContributesBinding is only present in kotlin metadata now, we need to fix")
    fun `the generated class contributes the toggle store binding`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(ContributesBinding::class.java)!!
        assertEquals(TriggerTestScope::class, annotation.scope)
        assertEquals(Toggle.Store::class, annotation.boundType)
    }

    @Test
    @Ignore("ContributesMultibinding is only present in kotlin metadata now, we need to fix")
    fun `the generated class contributes the privacy plugin multibinding`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(ContributesMultibinding::class.java)!!
        assertEquals(TriggerTestScope::class, annotation.scope)
        assertEquals(PrivacyFeaturePlugin::class, annotation.boundType)
        assertTrue(annotation.ignoreQualifier)
    }

    @Test
    fun `re-evaluate feature state when feature hash is null`() {
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
    fun `do not re-evaluate feature state if hash hasn't changed`() {
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
    fun `re-evaluate feature state if hash changed`() {
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
    fun `re-evaluate feature when already preset in remote config but just added to client`() {
        fun createAnotherFooFeature(): Any {
            return Class
                .forName("com.duckduckgo.feature.toggles.codegen.AnotherTestTriggerFeature_RemoteFeature")
                .getConstructor(
                    FeatureExceptions.Store::class.java,
                    FeatureSettings.Store::class.java,
                    dagger.Lazy::class.java as Class<*>,
                    AppBuildConfig::class.java,
                    VariantManager::class.java,
                    Context::class.java,
                ).newInstance(
                    FeatureExceptions.EMPTY_STORE,
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
    fun `when sub-feature is present remotely but missing locally continue without error`() {
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
    fun `fresh install and later update returns correct feature values`() {
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
    fun `test internal always enabled annotation`() {
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
    fun `test internal always enabled truth table`() {
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

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertFalse(testFeature.defaultFalse().isEnabled())

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

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertTrue(testFeature.defaultFalse().isEnabled())

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

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertFalse(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertFalse(testFeature.defaultFalse().isEnabled())

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

        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertTrue(privacyPlugin.store("testFeature", jsonString))
        assertTrue(testFeature.internalDefaultTrue().isEnabled())
        assertTrue(testFeature.defaultTrue().isEnabled())
        assertTrue(testFeature.internalDefaultFalse().isEnabled())
        assertTrue(testFeature.defaultFalse().isEnabled())
    }

    @Test
    fun `test staged rollout for default-enabled feature flag`() {
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
    fun `the disable state of the feature always wins`() {
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
    fun `the rollout step set to 0 disables the feature`() {
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
    fun `the parent feature disabled doesn't interfer with the sub-feature state`() {
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
    fun `the features have the right state for internal builds`() {
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
    fun `the feature incremental steps are ignored when feature disabled`() {
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
    fun `the feature incremental steps are executed when feature is enabled`() {
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
    fun `the invalid rollout steps are ignored and not executed`() {
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
    fun `disable a previously enabled incremental rollout`() {
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
    fun `re-enable a previously disabled incremental rollout`() {
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
    fun `feature was enabled remains enabled and rollout threshold is set`() {
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
    fun `backwards compatibility test - feature was disabled set rollout threshold`() {
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
    fun `backwards compatibility test - feature was null set rollout threshold`() {
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
    fun `full feature lifecycle`() {
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
    fun `test feature with multiple targets matching`() {
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
            listOf(Toggle.State.Target("mc", "US", "fr", null, null)),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test multiple languages`() {
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
                Toggle.State.Target(null, "US", "en", null, null),
                Toggle.State.Target(null, "FR", "fr", null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )

        featureTogglesCallback.locale = Locale.US
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target(null, "US", "en", null, null),
                Toggle.State.Target(null, "FR", "fr", null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )

        featureTogglesCallback.locale = Locale.FRANCE
        assertTrue(testFeature.self().isEnabled())
        assertTrue(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target(null, "US", "en", null, null),
                Toggle.State.Target(null, "FR", "fr", null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple targets not matching`() {
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
            listOf(Toggle.State.Target("mc", "US", "fr", null, null)),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets matching`() {
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
                Toggle.State.Target("mc", null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null),
                Toggle.State.Target(null, null, "fr", null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature with multiple separate targets not matching`() {
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
                Toggle.State.Target("mc", null, null, null, null),
                Toggle.State.Target(null, "US", null, null, null),
                Toggle.State.Target(null, null, "zh", null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test variant parsing when no remote variant provided`() {
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
    fun `test variant parsing`() {
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
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
        // false because it is an experiment and so variants are considered
        assertFalse(testFeature.experimentFooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.experimentFooFeature().getRawStoredState()!!.targets,
        )

        assertFalse(testFeature.variantFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.variantFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test variant when assigned variant key is null`() {
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
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.experimentFooFeature().getRawStoredState()!!.targets,
        )
        assertTrue(testFeature.variantFeature().isEnabled())
        assertEquals("", variantManager.variant)
        assertEquals(1, variantManager.saveVariantsCallCounter)
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.variantFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test variant when assigned variant key is not null`() {
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
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null),
                Toggle.State.Target("mb", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.experimentFooFeature().getRawStoredState()!!.targets,
        )
        assertTrue(testFeature.variantFeature().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals("na", variantManager.variant)
        assertEquals(
            listOf(
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.variantFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature disabled and forces variant when variant is null`() {
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
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature enabled and forces variant when variant is null`() {
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
                Toggle.State.Target("", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test feature does not force variant when already assigned`() {
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
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test experiment feature with ignored targets`() {
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
                Toggle.State.Target("mc", localeCountry = "US", localeLanguage = null, null, null),
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
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = "US", null, null),
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
                Toggle.State.Target("mc", localeCountry = null, localeLanguage = null, null, null),
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
                Toggle.State.Target("ma", localeCountry = null, localeLanguage = null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test experiment feature with targets matching`() {
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
                Toggle.State.Target("mc", localeCountry = "US", localeLanguage = null, null, null),
            ),
            testFeature.experimentDisabledByDefault().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test rollout roll back`() {
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
    fun `test cohorts json parsing`() {
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
    fun `test cohort only assigned when calling isEnabled(cohort)`() {
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

        // we call isEnabled() without cohort, cohort should not be assigned either
        testFeature.fooFeature().isEnabled()
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNull(rawState?.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())

        // we call isEnabled(cohort), then we should assign cohort
        testFeature.fooFeature().isEnabled(BLUE)
        rawState = testFeature.fooFeature().getRawStoredState()
        assertNotEquals(emptyList<Cohort>(), rawState?.cohorts)
        assertNotNull(rawState?.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())
        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
    }

    @Test
    fun `test remove all cohorts remotely removes assigned cohort`() {
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

        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
        assertNotNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)
        assertNotNull(testFeature.fooFeature().getCohort())

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
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
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
        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
        assertNull(testFeature.fooFeature().getRawStoredState()!!.assignedCohort)
        assertNull(testFeature.fooFeature().getCohort())
        assertTrue(testFeature.fooFeature().isEnabled())
    }

    @Test
    fun `test disabling feature disables cohort`() {
        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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

        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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

        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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

        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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

        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
    }

    @Test
    fun `test cohort targets`() {
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

        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

        featureTogglesCallback.locale = Locale(Locale.US.language, Locale.FRANCE.country)
        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

        featureTogglesCallback.locale = Locale.US
        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

        featureTogglesCallback.locale = Locale.FRANCE
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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

        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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

        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
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

        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnabled(BLUE))
    }

    @Test
    fun `test change remote cohorts after assignment should noop`() {
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

        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
    }

    @Test
    fun `test enrollment date`() {
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
        testFeature.fooFeature().isEnabled(CONTROL)

        val date = testFeature.fooFeature().getRawStoredState()!!.assignedCohort?.enrollmentDateET
        val parsedDate = ZonedDateTime.parse(date).truncatedTo(ChronoUnit.DAYS)
        val now = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS)
        assertEquals(now, parsedDate)
    }

    @Test
    fun `test rollback cohort experiments`() {
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
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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

        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
    }

    @Test
    fun `test cohort enabled and stop enrollment and then roll-back`() {
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
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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
        // when weight of assigned cohort goes down to "0" we just stop the enrollment, but keep the cohort assignment
        assertTrue(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))

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
        // when weight of assigned cohort goes down to "0" we just stop the enrollment, but keep the cohort assignment
        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertTrue(testFeature.fooFeature().isEnabled(BLUE))

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
        assertFalse(testFeature.fooFeature().isEnabled(CONTROL))
        assertFalse(testFeature.fooFeature().isEnabled(BLUE))
    }

    @Test
    fun `test config parsed correctly`() {
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
        assertTrue(topLevelStateConfig.size == 2)
        assertEquals("foo/value", topLevelStateConfig["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), topLevelStateConfig["bar"])
        assertTrue(topLevelConfig.size == 2)
        assertEquals("foo/value", topLevelConfig["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), topLevelConfig["bar"])

        var stateConfig = testFeature.fooFeature().getRawStoredState()?.settings?.let { adapter.fromJson(it) } ?: emptyMap()
        var config = testFeature.fooFeature().getSettings()?.let { adapter.fromJson(it) } ?: emptyMap()
        assertTrue(stateConfig.size == 2)
        assertEquals("foo/value", stateConfig["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), stateConfig["bar"])
        assertTrue(config.size == 2)
        assertEquals("foo/value", config["foo"])
        assertEquals(mapOf("key" to "value", "number" to 2.0, "boolean" to true, "complex" to mapOf("boolean" to true)), config["bar"])

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
        assertTrue(stateConfig.size == 1)
        assertEquals("foo/value", stateConfig["foo"])
        assertNull(stateConfig["bar"])
        assertTrue(config.size == 1)
        assertEquals("foo/value", config["foo"])
        assertNull(config["bar"])

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
        assertTrue(stateConfig.isEmpty())
        assertTrue(config.isEmpty())

        // re-add config different values
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
    }

    private fun generatedFeatureNewInstance(): Any {
        return Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .getConstructor(
                FeatureExceptions.Store::class.java,
                FeatureSettings.Store::class.java,
                dagger.Lazy::class.java as Class<*>,
                AppBuildConfig::class.java,
                VariantManager::class.java,
                Context::class.java,
            ).newInstance(
                FeatureExceptions.EMPTY_STORE,
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
