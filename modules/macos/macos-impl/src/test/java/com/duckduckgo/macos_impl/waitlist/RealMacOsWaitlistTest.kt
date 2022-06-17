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

package com.duckduckgo.macos_impl.waitlist

import com.duckduckgo.macos_store.MacOsWaitlistState
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealMacOsWaitlistTest {
    private val mockMacOsWaitlistManager: MacOsWaitlistManager = mock()
    lateinit var testee: RealMacOsWaitlist

    @Before
    fun setup() {
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(MacOsWaitlistState.NotJoinedQueue)
        testee = RealMacOsWaitlist(mockMacOsWaitlistManager)
    }

    @Test
    fun whenGetWaitlistStateThenGetStateCalled() {
        testee.getWaitlistState()
        verify(mockMacOsWaitlistManager).getState()
    }
}
