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

package com.duckduckgo.duckchat.impl.sync

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckAiChatDeletionListenerImplTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val mockDuckChatSyncRepository: DuckChatSyncRepository = mock()
    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()

    private lateinit var testee: DuckAiChatDeletionListenerImpl

    @Before
    fun setUp() {
        testee = DuckAiChatDeletionListenerImpl(
            duckChatSyncRepository = mockDuckChatSyncRepository,
            duckChatFeatureRepository = mockDuckChatFeatureRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )
    }

    @Test
    fun whenOnStopThenStoresBackgroundTimestamp() = runTest {
        val timestamp = 1234567890L
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(timestamp)
        testee.onStop(mockLifecycleOwner)
        verify(mockDuckChatFeatureRepository).setAppBackgroundTimestamp(timestamp)
    }

    @Test
    fun whenOnStartThenClearsBackgroundTimestamp() = runTest {
        testee.onStart(mockLifecycleOwner)
        verify(mockDuckChatFeatureRepository).setAppBackgroundTimestamp(null)
    }

    @Test
    fun whenOnDuckAiChatsDeletedAndBackgroundTimestampExistsThenUsesBackgroundTimestamp() = runTest {
        val backgroundTimestamp = 1111111111L
        whenever(mockDuckChatFeatureRepository.getAppBackgroundTimestamp()).thenReturn(backgroundTimestamp)
        testee.onDuckAiChatsDeleted()
        verify(mockDuckChatSyncRepository).recordDuckAiChatsDeleted(backgroundTimestamp)
    }

    @Test
    fun whenOnDuckAiChatsDeletedAndNoBackgroundTimestampThenUsesCurrentTime() = runTest {
        val currentTime = 2222222222L
        whenever(mockDuckChatFeatureRepository.getAppBackgroundTimestamp()).thenReturn(null)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)
        testee.onDuckAiChatsDeleted()
        verify(mockDuckChatSyncRepository).recordDuckAiChatsDeleted(currentTime)
    }
}
