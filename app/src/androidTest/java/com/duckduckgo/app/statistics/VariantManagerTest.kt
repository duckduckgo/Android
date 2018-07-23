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

import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DefaultBrowserFeature.*
import org.junit.Assert.*
import org.junit.Test

class VariantManagerTest {

    private val variants = VariantManager.ACTIVE_VARIANTS
    private val totalWeight = variants.sumByDouble { it.weight }

    @Test
    fun onboardingOnlyVariantConfiguredCorrectly() {
        val variant = variants.firstOrNull { it.key == "ms" }
        assertEqualsDouble( 0.20, variant!!.weight / totalWeight)
        assertTrue(variant.hasFeature(ShowInOnboarding))
        assertEquals(1, variant.features.size)
    }

    @Test
    fun homeScreenCallToActionVariantConfiguredCorrectly() {
        val variant = variants.firstOrNull { it.key == "mt" }
        assertEqualsDouble( 0.20, variant!!.weight / totalWeight)
        assertTrue(variant.hasFeature(ShowHomeScreenCallToAction))
        assertEquals(1, variant.features.size)
    }

    @Test
    fun showBannerVariantConfiguredCorrectly() {
        val variant = variants.firstOrNull { it.key == "mu" }
        assertEqualsDouble( 0.20, variant!!.weight / totalWeight)
        assertTrue(variant.hasFeature(ShowBanner))
        assertEquals(1, variant.features.size)
    }

    @Test
    fun showBannerAndShowHomeScreenCallToActionVariantConfiguredCorrectly() {
        val variant = variants.firstOrNull { it.key == "mv" }
        assertEqualsDouble( 0.20, variant!!.weight / totalWeight)
        assertTrue(variant.hasFeature(ShowBanner))
        assertTrue(variant.hasFeature(ShowHomeScreenCallToAction))
        assertEquals(2, variant.features.size)
    }

    @Test
    fun controlVariantConfiguredCorrectly() {
        val variant = variants.firstOrNull { it.key == "my" }
        assertEqualsDouble( 0.2, variant!!.weight / totalWeight)
        assertEquals(0, variant.features.size)
    }

    private fun assertEqualsDouble(expected: Double, actual: Double) {
        val comparison = expected.compareTo(actual)
        if(comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}