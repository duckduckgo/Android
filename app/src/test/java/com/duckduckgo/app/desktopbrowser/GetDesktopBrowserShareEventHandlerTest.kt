/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.desktopbrowser

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GetDesktopBrowserShareEventHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee: GetDesktopBrowserShareEventHandler = GetDesktopBrowserShareEventHandlerImpl()

    @Test
    fun whenOnLinkSharedCalledThenLinkSharedEmitsTrue() = runTest {
        testee.linkShared.test {
            assertFalse(awaitItem())

            testee.onLinkShared()

            assertTrue(awaitItem())
        }
    }

    @Test
    fun whenConsumeEventCalledThenLinkSharedEmitsFalse() = runTest {
        testee.linkShared.test {
            assertFalse(awaitItem())

            testee.onLinkShared()
            assertTrue(awaitItem())

            testee.consumeEvent()
            assertFalse(awaitItem())
        }
    }
}
