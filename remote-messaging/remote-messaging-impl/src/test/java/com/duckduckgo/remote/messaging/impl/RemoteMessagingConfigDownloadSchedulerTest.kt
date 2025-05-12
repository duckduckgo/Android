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

package com.duckduckgo.remote.messaging.impl

import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RemoteMessagingConfigDownloadSchedulerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock
    private lateinit var workManager: WorkManager

    @Mock
    private lateinit var downloader: RemoteMessagingConfigDownloader

    @Mock
    private lateinit var remoteMessagingConfigRepository: RemoteMessagingConfigRepository

    @Mock
    private lateinit var remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles

    @Mock
    private lateinit var invalidateRMFAfterPrivacyConfigDownloadedToggleMock: Toggle

    private lateinit var testee: RemoteMessagingConfigDownloadScheduler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(remoteMessagingFeatureToggles.invalidateRMFAfterPrivacyConfigDownloaded()).thenReturn(
            invalidateRMFAfterPrivacyConfigDownloadedToggleMock,
        )

        testee = RemoteMessagingConfigDownloadScheduler(
            workManager = workManager,
            downloader = downloader,
            appCoroutineScope = coroutinesTestRule.testScope,
            dispatcherProvider = coroutinesTestRule.testDispatcherProvider,
            remoteMessagingConfigRepository = remoteMessagingConfigRepository,
            remoteMessagingFeatureToggles = remoteMessagingFeatureToggles,
        )
    }

    @Test
    fun `invalidate on privacy config downloaded`() = runTest {
        whenever(invalidateRMFAfterPrivacyConfigDownloadedToggleMock.isEnabled()).thenReturn(true)

        testee.onPrivacyConfigDownloaded()

        verify(remoteMessagingConfigRepository).invalidate()
        verify(downloader).download()
    }

    @Test
    fun `do not invalidate on privacy config downloaded when kill switch disabled`() = runTest {
        whenever(invalidateRMFAfterPrivacyConfigDownloadedToggleMock.isEnabled()).thenReturn(false)
        testee.onPrivacyConfigDownloaded()

        verify(remoteMessagingConfigRepository, never()).invalidate()
        verify(downloader, never()).download()
    }
}
