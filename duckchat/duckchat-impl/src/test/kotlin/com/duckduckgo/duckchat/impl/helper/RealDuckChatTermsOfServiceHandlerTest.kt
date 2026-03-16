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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealDuckChatTermsOfServiceHandlerTest {

    private val mockDataStore: DuckChatDataStore = mock()
    private val mockDuckChat: DuckChatInternal = mock()
    private val testee = RealDuckChatTermsOfServiceHandler(
        dataStore = mockDataStore,
        duckChat = mockDuckChat,
    )

    @Test
    fun whenUserAcceptsTermsForFirstTimeThenResultIsNotDuplicate() = runTest {
        whenever(mockDataStore.hasUserAcceptedTerms()).thenReturn(false)
        whenever(mockDuckChat.isChatSyncFeatureEnabled()).thenReturn(false)

        val result = testee.userAcceptedTerms()

        assertEquals(false, result.isDuplicate)
        verify(mockDataStore).setUserAcceptedTerms()
    }

    @Test
    fun whenUserReAcceptsTermsAndSyncEnabledThenResultIsDuplicateWithSyncOn() = runTest {
        whenever(mockDataStore.hasUserAcceptedTerms()).thenReturn(true)
        whenever(mockDuckChat.isChatSyncFeatureEnabled()).thenReturn(true)

        val result = testee.userAcceptedTerms()

        assertEquals(true, result.isDuplicate)
        assertEquals(true, result.isSyncEnabled)
        verify(mockDataStore).setUserAcceptedTerms()
    }

    @Test
    fun whenUserReAcceptsTermsAndSyncDisabledThenResultIsDuplicateWithSyncOff() = runTest {
        whenever(mockDataStore.hasUserAcceptedTerms()).thenReturn(true)
        whenever(mockDuckChat.isChatSyncFeatureEnabled()).thenReturn(false)

        val result = testee.userAcceptedTerms()

        assertEquals(true, result.isDuplicate)
        assertEquals(false, result.isSyncEnabled)
        verify(mockDataStore).setUserAcceptedTerms()
    }
}
