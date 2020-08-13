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

    // Notification Drip Experiment

    @Test
    fun notificationDripTestControlGroupVariantIsNotActive() {
        val variant = variants.firstOrNull { it.key == "za" }
        assertEqualsDouble(0.0, variant!!.weight)
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
    fun notificationDripTestNullVariantIsNotActive() {
        val variant = variants.firstOrNull { it.key == "zb" }
        assertEqualsDouble(0.0, variant!!.weight)
    }

    @Test
    fun notificationDripTestNullVariantHasDripNotification() {
        val variant = variants.firstOrNull { it.key == "zb" }
        assertEquals(1, variant!!.features.size)
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripA1TestVariantIsNotActive() {
        val variant = variants.firstOrNull { it.key == "zc" }
        assertEqualsDouble(0.0, variant!!.weight)
    }

    @Test
    fun notificationDripA1TestVariantHasDay1DripA1NotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "zc" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1DripA1Notification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripA2TestVariantIsNotActive() {
        val variant = variants.firstOrNull { it.key == "zd" }
        assertEqualsDouble(0.0, variant!!.weight)
    }

    @Test
    fun notificationDripA2TestVariantHasDay1DripA2NotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "zd" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1DripA2Notification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripB1TestVariantIsNotActive() {
        val variant = variants.firstOrNull { it.key == "ze" }
        assertEqualsDouble(0.0, variant!!.weight)
    }

    @Test
    fun notificationDripB1TestVariantHasDay1DripB1NotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "ze" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1DripB1Notification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
    }

    @Test
    fun notificationDripB2TestVariantIsNotActive() {
        val variant = variants.firstOrNull { it.key == "zf" }
        assertEqualsDouble(0.0, variant!!.weight)
    }

    @Test
    fun notificationDripB2TestVariantHasDay1DripB2NotificationAndDay3ClearDataNotificationAndDripNotification() {
        val variant = variants.firstOrNull { it.key == "zf" }
        assertEquals(3, variant!!.features.size)
        assertTrue(variant.hasFeature(Day1DripB2Notification))
        assertTrue(variant.hasFeature(Day3ClearDataNotification))
        assertTrue(variant.hasFeature(DripNotification))
    }

    // Single Search Bar Experiments
    @Test
    fun serpHeaderControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "zg" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun serpHeaderVariantHasExpectedWeightAndSERPHeaderRemovalFeature() {
        val variant = variants.first { it.key == "zi" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertEquals(SerpHeaderRemoval, variant.features[0])
    }

    @Test
    fun serpHeaderVariantHasExpectedWeightAndSERPHeaderQueryReplacementFeature() {
        val variant = variants.first { it.key == "zh" }
        assertEqualsDouble(0.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertEquals(SerpHeaderQueryReplacement, variant.features[0])
    }

    @Test
    fun inBrowserControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "zj" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(0, variant.features.size)
    }

    @Test
    fun inBrowserSecondControlVariantHasExpectedWeightAndRemoveDay1And3NotificationsAndKillOnboardingFeatures() {
        val variant = variants.first { it.key == "zk" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(2, variant.features.size)
        assertTrue(variant.hasFeature(KillOnboarding))
        assertTrue(variant.hasFeature(RemoveDay1AndDay3Notifications))
    }

    @Test
    fun inBrowserInAppUsageVariantHasExpectedWeightAndRemoveDay1And3NotificationsAndKillOnboardingAndInAppUsageFeatures() {
        val variant = variants.first { it.key == "zl" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(3, variant.features.size)
        assertTrue(variant.hasFeature(KillOnboarding))
        assertTrue(variant.hasFeature(RemoveDay1AndDay3Notifications))
        assertTrue(variant.hasFeature(InAppUsage))
    }

    // Fire button education
    @Test
    fun fireButtonEducationControlGroupVariantIsActive() {
        val variant = variants.first { it.key == "zm" }
        assertEqualsDouble(1.0, variant.weight)
    }

    @Test
    fun fireButtonEducationVariantHasExpectedWeightAndFeatures() {
        val variant = variants.first { it.key == "zr" }
        assertEqualsDouble(1.0, variant.weight)
        assertEquals(1, variant.features.size)
        assertTrue(variant.hasFeature(FireButtonEducation))
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
