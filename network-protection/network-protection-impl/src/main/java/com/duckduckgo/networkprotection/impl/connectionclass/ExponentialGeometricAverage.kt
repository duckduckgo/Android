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

package com.duckduckgo.networkprotection.impl.connectionclass

import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln

class ExponentialGeometricAverage @Inject constructor() {
    private val decayConstant = DEFAULT_DECAY_CONSTANT
    private val cutover = ceil(1 / decayConstant)

    private var count = 0
    private var value: Double = -1.0

    internal val average: Double
        get() = value

    internal fun addMeasurement(measurement: Double) {
        val keepConstant = 1 - decayConstant

        value = if (count > cutover) {
            exp(keepConstant * ln(value) + decayConstant * ln(measurement))
        } else if (count > 0) {
            val retained: Double = keepConstant * count / (count + 1.0)
            val newcomer = 1.0 - retained
            exp(retained * ln(value) + newcomer * ln(measurement))
        } else {
            measurement
        }
        count++
    }

    internal fun reset() {
        value = -1.0
        count = 0
    }
}

/**
 * The factor used to calculate the moving average depending upon the previous calculated value.
 * The smaller this value is, the less responsive to new samples the moving average becomes.
 */
private const val DEFAULT_DECAY_CONSTANT = 0.1
