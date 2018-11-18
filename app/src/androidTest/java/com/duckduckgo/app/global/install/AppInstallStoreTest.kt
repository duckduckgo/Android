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

package com.duckduckgo.app.global.install

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit


class AppInstallStoreTest {

    var testee: AppInstallStore = mock()

    @Test
    fun whenInstallationToDayThenDaysSinceInstallationIsZero() {
        whenever(testee.installTimestamp).thenReturn(System.currentTimeMillis())
        assertEquals(0, testee.daysSinceInstallation())
    }

    @Test
    fun whenDayAfterInstallationThenDaysSinceInstallationIsOne() {
        val timeSinceInstallation = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        whenever(testee.installTimestamp).thenReturn(timeSinceInstallation)
        assertEquals(1, testee.daysSinceInstallation())
    }

    @Test
    fun whenAWeekAfterInstallationThenDaysSinceInstallationIsSeven() {
        val timeSinceInstallation = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        whenever(testee.installTimestamp).thenReturn(timeSinceInstallation)
        assertEquals(7, testee.daysSinceInstallation())
    }
}