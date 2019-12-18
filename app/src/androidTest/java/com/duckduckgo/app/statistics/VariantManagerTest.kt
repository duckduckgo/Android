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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VariantManagerTest {

    private val variants = VariantManager.ACTIVE_VARIANTS

    @Test
    fun serpAndSharedControlVariantActive() {
        val variant = variants.firstOrNull { it.key == "sc" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun serpExperimentalVariantActive() {
        val variant = variants.firstOrNull { it.key == "se" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun conceptTestControlGroupVariantActive() {
        val variant = variants.firstOrNull { it.key == "mc" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun conceptTestControlGroupVariantHasNoExperimentFeatures() {
        val variant = variants.firstOrNull { it.key == "mc" }
        assertEquals(0, variant!!.features.size)
    }

    @Test
    fun conceptTestExistingNoCtaExperimentVariantActive() {
        val variant = variants.firstOrNull { it.key == "md" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun conceptTestExistingNoCtaExperimentVariantHasExperimentalExistingNoCta() {
        val variant = variants.firstOrNull { it.key == "md" }
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(VariantManager.VariantFeature.ExistingNoCta))
    }

    @Test
    fun conceptTestExperimentVariantActive() {
        val variant = variants.firstOrNull { it.key == "me" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun conceptTestExperimentVariantHasExperimentalExistingNoCta() {
        val variant = variants.firstOrNull { it.key == "me" }
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(VariantManager.VariantFeature.ConceptTest))
    }

    @Suppress("SameParameterValue")
    private fun assertEqualsDouble(expected: Double, actual: Double) {
        val comparison = expected.compareTo(actual)
        if (comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}