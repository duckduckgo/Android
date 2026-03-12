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

import android.annotation.SuppressLint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.sync.api.engine.DeletableType
import com.duckduckgo.sync.api.engine.FeatureSyncError
import com.duckduckgo.sync.api.engine.SyncDeletionResponse
import com.duckduckgo.sync.api.engine.SyncErrorResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class DuckChatSyncDataManagerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val duckChatSyncRepository: DuckChatSyncRepository = mock()

    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()

    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var testee: DuckChatSyncDataManager

    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)

    @Before
    fun setUp() = runTest {
        whenever(duckChatFeatureRepository.isAIChatHistoryEnabled()).thenReturn(true)
        testee = DuckChatSyncDataManager(
            duckChatSyncRepository = duckChatSyncRepository,
            duckChatFeatureRepository = duckChatFeatureRepository,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appBuildConfig = appBuildConfig,
            duckChatFeature = duckChatFeature,
            appCoroutineScope = coroutineTestRule.testScope,
        )
    }

    @Test
    fun whenGetTypeThenReturnsDuckAiChats() {
        assertEquals(DeletableType.DUCK_AI_CHATS, testee.getType())
    }

    @Test
    fun whenGetDeletionsAndFeatureDisabledThenReturnsNull() = runTest {
        duckChatFeature.supportsSyncChatsDeletion().setRawStoredState(Toggle.State(enable = false))
        val result = testee.getDeletions()
        assertNull(result)
    }

    @Test
    fun whenGetDeletionsAndChatHistoryDisabledThenReturnsNull() = runTest {
        duckChatFeature.supportsSyncChatsDeletion().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChatFeatureRepository.isAIChatHistoryEnabled()).thenReturn(false)

        val result = testee.getDeletions()

        assertNull(result)
    }

    @Test
    fun whenGetDeletionsAndFeatureEnabledWithTimestampThenReturnsDeletionRequest() = runTest {
        duckChatFeature.supportsSyncChatsDeletion().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChatFeatureRepository.isAIChatHistoryEnabled()).thenReturn(true)
        whenever(duckChatSyncRepository.getLastDuckAiChatDeletionTimestamp()).thenReturn("2025-01-01T12:00:00Z")

        val result = testee.getDeletions()

        assertEquals(DeletableType.DUCK_AI_CHATS, result?.type)
        assertEquals("2025-01-01T12:00:00Z", result?.untilTimestamp)
    }

    @Test
    fun whenGetDeletionsAndFeatureEnabledWithoutTimestampThenReturnsNull() = runTest {
        duckChatFeature.supportsSyncChatsDeletion().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChatFeatureRepository.isAIChatHistoryEnabled()).thenReturn(true)
        whenever(duckChatSyncRepository.getLastDuckAiChatDeletionTimestamp()).thenReturn(null)
        val result = testee.getDeletions()
        assertNull(result)
    }

    @Test
    fun whenOnSuccessWithTimestampThenRepositoryIsCalledToClearTimestamp() = runTest {
        val response = SyncDeletionResponse(
            type = DeletableType.DUCK_AI_CHATS,
            untilTimestamp = "2025-01-01T12:00:00Z",
        )

        testee.onSuccess(response)

        verify(duckChatSyncRepository).clearDeletionTimestampIfMatches("2025-01-01T12:00:00Z")
    }

    @Test
    fun whenOnSuccessWithoutTimestampThenRepositoryIsNotCalled() = runTest {
        val response = SyncDeletionResponse(
            type = DeletableType.DUCK_AI_CHATS,
            untilTimestamp = null,
        )

        testee.onSuccess(response)

        verify(duckChatSyncRepository, never()).clearDeletionTimestampIfMatches(org.mockito.kotlin.any())
    }

    @Test
    fun whenOnErrorThenRepositoryIsNotCalled() = runTest {
        val error = SyncErrorResponse(
            type = DeletableType.DUCK_AI_CHATS,
            featureSyncError = FeatureSyncError.INVALID_REQUEST,
        )

        testee.onError(error)

        verify(duckChatSyncRepository, never()).clearDeletionTimestampIfMatches(org.mockito.kotlin.any())
    }
}
