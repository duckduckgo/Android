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

import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class VariantManagerTest {

    private val variants = VariantManager.ACTIVE_VARIANTS
    private val totalWeight = variants.sumByDouble { it.weight }

    @Test
    fun whenChanceOfControlVariantCalculatedThenOddsAreOneInTwo() {
        val variant = variants.firstOrNull { it.key == "my" }
        assertNotNull(variant)
        assertEqualsDouble( 0.5, variant!!.weight / totalWeight)
    }

    @Test
    fun whenChanceOfOnboardingOnlyVariantCalculatedThenOddsAreOneInFour() {
        val variant = variants.firstOrNull { it.key == "mw" }
        assertNotNull(variant)
        assertEqualsDouble( 0.25, variant!!.weight / totalWeight)
    }

    @Test
    fun whenChanceOfOnboardingAndReminderVariantCalculatedThenOddsAreOneInFour() {
        val variant = variants.firstOrNull { it.key == "mx" }
        assertNotNull(variant)
        assertEqualsDouble( 0.25, variant!!.weight / totalWeight)
    }

    private fun assertEqualsDouble(expected: Double, actual: Double) {
        val comparison = expected.compareTo(actual)
        if(comparison != 0) {
            fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}