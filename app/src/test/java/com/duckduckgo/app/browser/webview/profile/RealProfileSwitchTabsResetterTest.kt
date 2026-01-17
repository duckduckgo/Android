/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.webview.profile

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealProfileSwitchTabsResetterTest {

    private val testee = RealProfileSwitchTabsResetter()

    @Test
    fun whenRequestResetWithClearTabsTrueThenEventEmittedWithClearTabsTrue() = runTest {
        testee.resetEvent.test {
            testee.requestReset(clearTabs = true)

            val event = awaitItem()
            assertTrue(event.clearTabs)
        }
    }

    @Test
    fun whenRequestResetWithClearTabsFalseThenEventEmittedWithClearTabsFalse() = runTest {
        testee.resetEvent.test {
            testee.requestReset(clearTabs = false)

            val event = awaitItem()
            assertFalse(event.clearTabs)
        }
    }

    @Test
    fun whenMultipleResetRequestsThenAllEventsEmitted() = runTest {
        testee.resetEvent.test {
            testee.requestReset(clearTabs = true)
            testee.requestReset(clearTabs = false)

            assertEquals(true, awaitItem().clearTabs)
            assertEquals(false, awaitItem().clearTabs)
        }
    }
}
