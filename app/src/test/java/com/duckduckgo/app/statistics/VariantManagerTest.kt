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
    private fun assertEqualsDouble(
        expected: Double,
        actual: Double
    ) {
        val comparison = expected.compareTo(actual)
        if (comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }

    // AUTOMATIC FIREPROOFING EXPERIMENT
    @Test
    fun automaticFireproofingControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "mi" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun automaticFireproofingExperimentalVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "mj" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(FireproofExperiment))
    }

    // RETURNING USERS - SECOND EXPERIMENT
    @Test
    fun returningUsersControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "zd" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun returningUsersExperimentalFirstVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "zg" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(ReturningUsersContinueWithoutPrivacyTips))
    }

    @Test
    fun returningUsersExperimentalSecondVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "zh" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(ReturningUsersSkipTutorial))
    }

    // TRACKING PARAMETERS - INITIAL ROLLOUT TO 25%
    @Test
    fun trackingParameterRemovalVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "my" }
        assertEqualsDouble(0.25, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(TrackingParameterRemoval))
    }

    @Test
    fun trackingParameterRemovalEmptyVariant2HasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "me" }
        assertEqualsDouble(0.75, variant.weight)
        assertEquals(0, variant.features.size)
        assertFalse(variant.hasFeature(TrackingParameterRemoval))
    }
}
