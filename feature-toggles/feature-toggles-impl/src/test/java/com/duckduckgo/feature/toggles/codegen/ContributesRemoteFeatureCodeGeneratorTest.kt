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
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ContributesRemoteFeatureCodeGeneratorTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var testFeature: TestTriggerFeature
    private val appBuildConfig: AppBuildConfig = mock()
    private lateinit var variantManager: FakeVariantManager

    @Before
    fun setup() {
        variantManager = FakeVariantManager()
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "testFeature",
            appVersionProvider = { appBuildConfig.versionCode },
            flavorNameProvider = { appBuildConfig.flavor.name },
            appVariantProvider = { variantManager.getVariantKey() },
            forceDefaultVariant = { variantManager.saveVariants(emptyList()) },
        ).build().create(TestTriggerFeature::class.java)
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
    fun `the generated class contributes the toggle store binding`() {
        val generatedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TestTriggerFeature_RemoteFeature")
            .kotlin

        val annotation = generatedClass.java.getAnnotation(ContributesBinding::class.java)!!
        assertEquals(TriggerTestScope::class, annotation.scope)
        assertEquals(Toggle.Store::class, annotation.boundType)
    }

    @Test
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
        assertNull(testFeature.fooFeature().rolloutStep())
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
        assertEquals(4, testFeature.fooFeature().rolloutStep())
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
        assertEquals(1, testFeature.fooFeature().rolloutStep())
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
        assertEquals(1, testFeature.fooFeature().rolloutStep())

        assertTrue(privacyPlugin.store("testFeature", jsonDisabled))
        assertTrue(testFeature.self().isEnabled())
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(1, testFeature.fooFeature().rolloutStep())
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
        assertEquals(1, testFeature.fooFeature().rolloutStep())

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
        assertEquals(1, testFeature.fooFeature().rolloutStep())
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
        assertNull(testFeature.fooFeature().rolloutStep())

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
        assertNull(testFeature.fooFeature().rolloutStep())

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
        assertNull(testFeature.fooFeature().rolloutStep())

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
        // cache rollout
        val rolloutStep = testFeature.fooFeature().rolloutStep()
        val wasEnabled = testFeature.fooFeature().isEnabled()

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
        assertEquals(rolloutStep, testFeature.fooFeature().rolloutStep())

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
        assertEquals(rolloutStep, testFeature.fooFeature().rolloutStep())

        // resume rollout and update app version
        whenever(appBuildConfig.versionCode).thenReturn(2)
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
                                            "percent": 10.0
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
        assertEquals(wasEnabled, testFeature.fooFeature().isEnabled())
        assertEquals(rolloutStep, testFeature.fooFeature().rolloutStep())

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
                                            "percent": 10
                                        },
                                        {
                                            "percent": 20
                                        },
                                        {
                                            "percent": 30
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
        if (wasEnabled) {
            assertEquals(rolloutStep, testFeature.fooFeature().rolloutStep())
        } else {
            assertNotEquals(rolloutStep, testFeature.fooFeature().rolloutStep())
            assertEquals(4, testFeature.fooFeature().rolloutStep())
        }

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
        if (wasEnabled) {
            assertEquals(rolloutStep, testFeature.fooFeature().rolloutStep())
        } else {
            assertNotEquals(rolloutStep, testFeature.fooFeature().rolloutStep())
            assertEquals(4, testFeature.fooFeature().rolloutStep())
        }
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
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("ma"),
                Toggle.State.Target("mb"),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
        assertTrue(testFeature.variantFeature().isEnabled())
        assertEquals(
            listOf(
                Toggle.State.Target("mc"),
            ),
            testFeature.variantFeature().getRawStoredState()!!.targets,
        )
    }

    @Test
    fun `test variant when assigned variant key is null`() {
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
        assertFalse(testFeature.fooFeature().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals(
            listOf(
                Toggle.State.Target("ma"),
                Toggle.State.Target("mb"),
            ),
            testFeature.fooFeature().getRawStoredState()!!.targets,
        )
        assertFalse(testFeature.variantFeature().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals(
            listOf(
                Toggle.State.Target("mc"),
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
                            "variantFeatureForcesDefaultVariant": {
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
        assertFalse(testFeature.variantFeatureForcesDefaultVariant().isEnabled())
        assertEquals(1, variantManager.saveVariantsCallCounter)
        assertEquals("", variantManager.getVariantKey())
        assertEquals(
            listOf(
                Toggle.State.Target("mc"),
            ),
            testFeature.variantFeatureForcesDefaultVariant().getRawStoredState()!!.targets,
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
                            "variantFeatureForcesDefaultVariant": {
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
        assertTrue(testFeature.variantFeatureForcesDefaultVariant().isEnabled())
        assertEquals(1, variantManager.saveVariantsCallCounter)
        assertEquals("", variantManager.getVariantKey())
        assertEquals(
            listOf(
                Toggle.State.Target(""),
            ),
            testFeature.variantFeatureForcesDefaultVariant().getRawStoredState()!!.targets,
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
                            "variantFeatureForcesDefaultVariant": {
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
        assertTrue(testFeature.variantFeatureForcesDefaultVariant().isEnabled())
        assertEquals(0, variantManager.saveVariantsCallCounter)
        assertEquals("mc", variantManager.getVariantKey())
        assertEquals(
            listOf(
                Toggle.State.Target("mc"),
            ),
            testFeature.variantFeatureForcesDefaultVariant().getRawStoredState()!!.targets,
        )
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

    private fun Toggle.rolloutStep(): Int? {
        return getRawStoredState()?.rolloutStep
    }
}

private class FakeVariantManager : VariantManager {
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

    override fun saveVariants(variants: List<VariantConfig>) {
        saveVariantsCallCounter++
        variant = ""
    }

    override fun isVariantInitialised(): Boolean {
        TODO("Not yet implemented")
    }
}
