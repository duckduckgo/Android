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

import org.junit.Assert.*
import org.junit.Test

class VariantManagerTest {

    private val variants = VariantManager.ACTIVE_VARIANTS

    @Test
    fun serpAndSharedControlVariantInactive() {
        val variant = variants.firstOrNull { it.key == "sc" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun serpExperimentalVariantInactive() {
        val variant = variants.firstOrNull { it.key == "se" }
        assertEqualsDouble(0.0, variant!!.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun tabUxExperimentVariantActive() {
        val variant = variants.firstOrNull { it.key == "mx" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun tabUxExperimentVariantHasExperimentalFeatureForGridTabSwitcher() {
        val variant = variants.firstOrNull { it.key == "mx" }
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(VariantManager.VariantFeature.TabSwitcherGrid))
    }

    @Test
    fun tabUxControlGroupVariantActive() {
        val variant = variants.firstOrNull { it.key == "mw" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun tabUxControlGroupVariantHasNoExperimentFeatures() {
        val variant = variants.firstOrNull { it.key == "mw" }
        assertEquals(0, variant!!.features.size)
    }

    @Test
    fun defaultBrowserDialogExperimentVariantActive() {
        val variant = variants.firstOrNull { it.key == "mo" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun defaultBrowserDialogExperimentVariantHasExperimentalFeatureForOnboardingExperiment() {
        val variant = variants.firstOrNull { it.key == "mo" }
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(VariantManager.VariantFeature.OnboardingExperiment))
    }

    @Test
    fun defaultBrowserDialogControlGroupVariantActive() {
        val variant = variants.firstOrNull { it.key == "mp" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun defaultBrowserDialogControlGroupVariantHasNoExperimentFeatures() {
        val variant = variants.firstOrNull { it.key == "mp" }
        assertEquals(0, variant!!.features.size)
    }

    @Suppress("SameParameterValue")
    private fun assertEqualsDouble(expected: Double, actual: Double) {
        val comparison = expected.compareTo(actual)
        if (comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}