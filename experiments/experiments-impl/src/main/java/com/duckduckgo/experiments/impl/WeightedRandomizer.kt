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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution
import javax.inject.Inject

interface IndexRandomizer {
    fun random(items: List<Probabilistic>): Int
}

interface Probabilistic {
    val weight: Double
}

@ContributesBinding(AppScope::class)
class WeightedRandomizer @Inject constructor() : IndexRandomizer {

    override fun random(items: List<Probabilistic>): Int {
        val indexArray = arrayPopulatedWithIndexes(items)
        val probabilitiesArray = arrayPopulatedWithProbabilities(items)

        val intDistribution = EnumeratedIntegerDistribution(indexArray, probabilitiesArray)
        return intDistribution.sample()
    }

    private fun arrayPopulatedWithIndexes(items: List<Probabilistic>): IntArray {
        return IntArray(items.size) { i -> i }
    }

    private fun arrayPopulatedWithProbabilities(items: List<Probabilistic>): DoubleArray {
        return items.map { it.weight }.toDoubleArray()
    }
}
