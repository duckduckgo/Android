/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.statistics

import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.*
import org.junit.Assert.*
import org.junit.Test

class VariantManagerTest {

    private val variants = VariantManager.ACTIVE_VARIANTS +
        VariantManager.REFERRER_VARIANTS +
        DEFAULT_VARIANT

    // SERP Experiment(s)

    @Test
    fun serpControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "sc" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun serpExperimentalVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "se" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    // Use our app experiment
    @Test
    fun inBrowserControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "ma" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun inBrowserSecondControlVariantHasExpectedWeightAndRemoveDay1And3NotificationsAndKillOnboardingFeatures() {
        val variant = variants.first { it.key == "mb" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(2, variant.features.size)
        assertTrue(variant.hasFeature(KillOnboarding))
        assertTrue(variant.hasFeature(RemoveDay1AndDay3Notifications))
    }

    @Test
    fun inBrowserInAppUsageVariantHasExpectedWeightAndRemoveDay1And3NotificationsAndKillOnboardingAndInAppUsageFeatures() {
        val variant = variants.first { it.key == "mc" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(3, variant.features.size)
        assertTrue(variant.hasFeature(KillOnboarding))
        assertTrue(variant.hasFeature(RemoveDay1AndDay3Notifications))
        assertTrue(variant.hasFeature(InAppUsage))
    }

    @Test
    fun newInBrowserControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "zx" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun newInBrowserInAppUsageVariantHasExpectedWeightAndKillOnboardingAndInAppUsageFeatures() {
        val variant = variants.first { it.key == "zy" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(2, variant.features.size)
        assertTrue(variant.hasFeature(KillOnboarding))
        assertTrue(variant.hasFeature(InAppUsage))
    }

    @Test
    fun verifyNoDuplicateVariantNames() {
        val existingNames = mutableSetOf<String>()
        variants.forEach {
            if (!existingNames.add(it.key)) {
                fail("Duplicate variant name found: ${it.key}")
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun assertEqualsDouble(expected: Double, actual: Double) {
        val comparison = expected.compareTo(actual)
        if (comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}
