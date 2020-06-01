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

    // Bottom Bar Navigation Experiment

    @Test
    fun bottomBarNavigationControlVariantIsActiveAndHasNoFeatures() {
        val variant = variants.first { it.key == "mb" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun bottomBarNavigationVariantIsActiveAndHasBottomBarNavigationFeature() {
        val variant = variants.first { it.key == "mk" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(BottomBarNavigation))
    }

    // Notification Drip Experiment

    @Test
    fun notificationDripTestControlGroupVariantActive() {
        val variant = variants.firstOrNull { it.key == "za" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun notificationDripTestControlGroupVariantHasDay1PrivacyNotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "za" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1PrivacyNotification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripTestNullVariantActive() {
        val variant = variants.firstOrNull { it.key == "zb" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun notificationDripTestNullVariantHasDripNotification() {
        val variant = variants.firstOrNull { it.key == "zb" }
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripTestArticleVariantActive() {
        val variant = variants.firstOrNull { it.key == "zc" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun notificationDripTestArticleVariantHasDay1ArticleNotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "zc" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1ArticleNotification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripTestBlogVariantActive() {
        val variant = variants.firstOrNull { it.key == "zd" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun notificationDripTestBlogVariantHasDay1BlogNotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "zd" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1BlogNotification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripTestAppFeatureVariantActive() {
        val variant = variants.firstOrNull { it.key == "ze" }
        assertEqualsDouble(1.0, variant!!.weight)
    }

    @Test
    fun notificationDripTestAppFeatureVariantHasDay1AppFeatureNotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "ze" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1AppFeatureNotification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
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
