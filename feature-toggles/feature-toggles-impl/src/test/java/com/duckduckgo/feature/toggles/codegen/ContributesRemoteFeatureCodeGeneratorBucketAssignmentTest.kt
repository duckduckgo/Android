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
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.PLAY
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import dagger.Lazy
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import kotlin.math.abs

private var enables = 0
private var disables = 0

@RunWith(ParameterizedRobolectricTestRunner::class)
class ContributesRemoteFeatureCodeGeneratorBucketAssignmentTest(private val testCase: TestCase) {

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
            forceDefaultVariant = { variantManager.updateVariants(emptyList()) },
        ).build().create(TestTriggerFeature::class.java)
    }

    @Test
    fun `test probability`() {
        fun assertWithinRange() {
            if (testCase.run < 9000) return // small sample sizes will have high variance
            val allowableDeviation = (enables + disables) / 2 * 6.0f / 100 // allowable deviation of 6%

            // Check if the absolute difference between the two numbers is within the allowable deviation
            val difference = abs(enables - disables)
            assertTrue(
                "Given sample size of ${enables + disables}, the allowable deviation (6%) is: $allowableDeviation, " +
                    "but actual difference was $difference " +
                    "(enables=$enables" +
                    ", disables=$disables)",
                difference <= allowableDeviation,
            )
        }

        val feature = generatedFeatureNewInstance()

        val privacyPlugin = (feature as PrivacyFeaturePlugin)

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
                                        "percent": ${testCase.rollout}
                                    }
                                ]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ),
        )

        val isEnabled = testFeature.fooFeature().isEnabled()
        if (isEnabled) {
            enables++
        } else {
            disables++
        }
        assertWithinRange()
    }

    data class TestCase(val rollout: Int, val run: Int)
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters()
        fun parameters(): List<TestCase> {
            val l = mutableListOf<TestCase>()
            repeat(10_000) {
                l.add(TestCase(run = it, rollout = 50))
            }

            return l
        }
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
}
