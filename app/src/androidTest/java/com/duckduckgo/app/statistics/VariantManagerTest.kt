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
import org.junit.Assert.*
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
        assertEquals(2, variant!!.features.size)
        assertTrue(variant.hasFeature(SuppressHomeTabWidgetCta))
        assertTrue(variant.hasFeature(SuppressOnboardingDefaultBrowserCta))
    }

    @Test
    fun conceptTestExperimentalVariantIsInactiveAndHasConceptTestAndSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "me" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(ConceptTest))
        assertTrue(variant.hasFeature(SuppressHomeTabWidgetCta))
        assertTrue(variant.hasFeature(SuppressOnboardingDefaultBrowserCta))
    }

    // CTA Validation experiments

    @Test
    fun ctaControlVariantIsInactiveAndHasNoFeatures() {
        val variant = variants.firstOrNull { it.key == "mq" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant!!.features.size)
    }

    @Test
    fun ctaSuppressOnboardingDefaultBrowserVariantIsInactiveAndHasSuppressDefaultBrowserFeature() {
        val variant = variants.firstOrNull { it.key == "mr" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(SuppressOnboardingDefaultBrowserCta))
    }

    @Test
    fun ctaSuppressHomeWidgetVariantIsInactiveAndHasSuppressWidgetCtaFeature() {
        val variant = variants.firstOrNull { it.key == "ms" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(SuppressHomeTabWidgetCta))
    }

    @Test
    fun ctaSuppressAllVariantIsInactiveAndHasSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "mt" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(2, variant!!.features.size)
        assertTrue(variant.hasFeature(SuppressOnboardingDefaultBrowserCta))
        assertTrue(variant.hasFeature(SuppressHomeTabWidgetCta))
    }

    // CTA on Concept Test experiments

    @Test
    fun insertCtaConceptControlVariantIsActiveAndHasConceptTestAndSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "mj" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(ConceptTest))
        assertTrue(variant.hasFeature(SuppressHomeTabWidgetCta))
        assertTrue(variant.hasFeature(SuppressOnboardingDefaultBrowserCta))
    }

    @Test
    fun insertCtaConceptTestWithAllCtaExperimentalVariantIsActiveAndHasConceptTestAndSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "ml" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(2, variant!!.features.size)
        assertTrue(variant.hasFeature(ConceptTest))
        assertTrue(variant.hasFeature(SuppressOnboardingDefaultBrowserContinueScreen))
    }

    @Test
    fun insertCtaConceptTestWithCtasAsDaxDialogsExperimentalVariantIsActiveAndHasConceptTestAndSuppressCtaFeatures() {
        val variant = variants.firstOrNull { it.key == "mh" }
        assertEqualsDouble(1.0, variant!!.weight)
        assertEquals(5, variant!!.features.size)
        assertTrue(variant.hasFeature(ConceptTest))
        assertTrue(variant.hasFeature(SuppressHomeTabWidgetCta))
        assertTrue(variant.hasFeature(SuppressOnboardingDefaultBrowserCta))
        assertTrue(variant.hasFeature(DefaultBrowserDaxCta))
        assertTrue(variant.hasFeature(SearchWidgetDaxCta))
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