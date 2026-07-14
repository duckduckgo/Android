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

package com.duckduckgo.adblocking.impl.store

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdBlockingSessionStoreTest {

    private val store = RealAdBlockingSessionStore()

    @Test
    fun whenNewThenNotDisabled() {
        assertFalse(store.isDisabledUntilRelaunch())
    }

    @Test
    fun whenSetDisabledThenReportsDisabled() {
        store.setDisabledUntilRelaunch()

        assertTrue(store.isDisabledUntilRelaunch())
    }

    @Test
    fun whenClearedThenResetsToNotDisabled() {
        store.setDisabledUntilRelaunch()

        store.clear()

        assertFalse(store.isDisabledUntilRelaunch())
    }

    @Test
    fun whenSetDisabledThenObserveEmitsTrue() = runTest {
        store.setDisabledUntilRelaunch()

        assertTrue(store.observe().first())
    }

    @Test
    fun whenClearedThenObserveEmitsFalse() = runTest {
        store.setDisabledUntilRelaunch()
        store.clear()

        assertFalse(store.observe().first())
    }
}
