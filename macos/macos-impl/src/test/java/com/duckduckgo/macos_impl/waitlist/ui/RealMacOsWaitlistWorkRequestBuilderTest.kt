/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.macos_impl.waitlist.ui

import org.junit.Assert.*
import org.junit.Test

class RealMacOsWaitlistWorkRequestBuilderTest {

    private val testee = RealMacOsWaitlistWorkRequestBuilder()

    @Test
    fun whenWithBigDelayIsFalseThenDelayIsFiveMins() {
        val request = testee.waitlistRequestWork(withBigDelay = false)
        val delay = request.workSpec.initialDelay

        assertEquals(FIVE_MINS_IN_MS, delay)
    }

    @Test
    fun whenWithDelayIsTrueThenDelayIsOneDay() {
        val request = testee.waitlistRequestWork(withBigDelay = true)
        val delay = request.workSpec.initialDelay

        assertEquals(ONE_DAY_IN_MS, delay)
    }

    companion object {
        const val ONE_DAY_IN_MS: Long = 86_400_000
        const val FIVE_MINS_IN_MS: Long = 300_000
    }
}
