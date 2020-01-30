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

import com.duckduckgo.app.statistics.VariantManager.VariantFeature.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VariantManagerTest {

    private val variants = VariantManager.ACTIVE_VARIANTS

    // SERP Experiment(s)

    @Test
    fun serpControlVariantIsInactiveAndHasNoFeatures() {
        val variant = variants.firstOrNull { it.key == "sc" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun serpExperimentalVariantIsInactiveAndHasNoFeatures() {
        val variant = variants.firstOrNull { it.key == "se" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    // Concept test experiment

    @Test
    fun conceptTestControlVariantIsInactiveAndHasNoFeatures() {
        val variant = variants.firstOrNull { it.key == "mc" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun conceptTestNoCtaVariantIsInactiveAndHasSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "md" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(2, variant.features.size)
        assertTrue(variant.hasFeature(SuppressWidgetCta))
        assertTrue(variant.hasFeature(SuppressDefaultBrowserCta))
    }

    @Test
    fun conceptTestExperimentalVariantIsInactiveAndHasConceptTestAndSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "me" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(3, variant.features.size)
        assertTrue(variant.hasFeature(ConceptTest))
        assertTrue(variant.hasFeature(SuppressWidgetCta))
        assertTrue(variant.hasFeature(SuppressDefaultBrowserCta))
    }

    // CTA Validation experiments

    @Test
    fun ctaControlVariantIsActiveAndHasNoFeatures() {
        val variant = variants.firstOrNull { it.key == "mq" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun ctaSuppressDefaultBrowserVariantIsActiveAndHasSuppressDefaultBrowserFeature() {
        val variant = variants.firstOrNull { it.key == "mr" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(SuppressDefaultBrowserCta))
    }

    @Test
    fun ctaSuppressWidgetVariantIsActiveAndHasSuppressWidgetCtaFeature() {
        val variant = variants.firstOrNull { it.key == "ms" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(SuppressWidgetCta))
    }

    @Test
    fun ctaSuppressAllVariantIsActiveAndHasSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "mt" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(2, variant.features.size)
        assertTrue(variant.hasFeature(SuppressDefaultBrowserCta))
        assertTrue(variant.hasFeature(SuppressWidgetCta))
    }

    @Suppress("SameParameterValue")
    private fun assertEqualsDouble(expected: Double, actual: Double) {
        val comparison = expected.compareTo(actual)
        if (comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}