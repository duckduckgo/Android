/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.experiments.impl

import org.junit.Assert
import org.junit.Test

class VariantManagerTest {

    private val variants = listOf(
        Variant("sc", 0.0) { true },
        Variant("se", 0.0) { true },
        Variant("ma", 1.0) { true },
        Variant("mb", 1.0) { false },
    )

    // SERP Experiment(s)

    @Test
    fun serpControlVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "sc" }
        assertEqualsDouble(0.0, variant.weight)
    }

    @Test
    fun serpExperimentalVariantHasExpectedWeightAndNoFeatures() {
        val variant = variants.first { it.key == "se" }
        assertEqualsDouble(0.0, variant.weight)
    }

    @Test
    fun verifyNoDuplicateVariantNames() {
        val existingNames = mutableSetOf<String>()
        variants.forEach {
            if (!existingNames.add(it.key)) {
                Assert.fail("Duplicate variant name found: ${it.key}")
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun assertEqualsDouble(
        expected: Double,
        actual: Double,
    ) {
        val comparison = expected.compareTo(actual)
        if (comparison != 0) {
            Assert.fail("Doubles are not equal. Expected $expected but was $actual")
        }
    }
}
