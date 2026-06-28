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

package com.duckduckgo.translations.impl

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.translations.api.ModelDownloadStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealTranslationModelManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val modelStore: ModelStore = mock()
    private val modelDownloader: ModelDownloader = mock()

    private val testee = RealTranslationModelManager(
        modelStore = modelStore,
        modelDownloader = modelDownloader,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenModelPresentThenIsModelDownloadedReturnsTrue() = runTest {
        whenever(modelStore.isDownloaded("en", "es")).thenReturn(true)

        assertTrue(testee.isModelDownloaded("en", "es"))
    }

    @Test
    fun whenModelAbsentThenIsModelDownloadedReturnsFalse() = runTest {
        whenever(modelStore.isDownloaded("en", "es")).thenReturn(false)

        assertFalse(testee.isModelDownloaded("en", "es"))
    }

    @Test
    fun whenModelAlreadyDownloadedThenDownloadModelEmitsOnlyCompleted() = runTest {
        whenever(modelStore.isDownloaded("en", "es")).thenReturn(true)

        testee.downloadModel("en", "es").test {
            assertEquals(ModelDownloadStatus.Completed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenModelAlreadyDownloadedThenDownloaderIsNeverInvoked() = runTest {
        whenever(modelStore.isDownloaded("en", "es")).thenReturn(true)

        testee.downloadModel("en", "es").test {
            awaitItem()
            awaitComplete()
        }

        verify(modelDownloader, never()).download(any(), any())
    }

    @Test
    fun whenModelNotDownloadedThenDownloadModelEmitsDownloaderProgress() = runTest {
        whenever(modelStore.isDownloaded("en", "es")).thenReturn(false)
        whenever(modelDownloader.download("en", "es")).thenReturn(
            flowOf(
                ModelDownloadStatus.InProgress(0),
                ModelDownloadStatus.InProgress(50),
                ModelDownloadStatus.Completed,
            ),
        )

        testee.downloadModel("en", "es").test {
            assertEquals(ModelDownloadStatus.InProgress(0), awaitItem())
            assertEquals(ModelDownloadStatus.InProgress(50), awaitItem())
            assertEquals(ModelDownloadStatus.Completed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenModelNotDownloadedAndDownloaderFailsThenDownloadModelEmitsFailed() = runTest {
        whenever(modelStore.isDownloaded("en", "es")).thenReturn(false)
        whenever(modelDownloader.download("en", "es")).thenReturn(
            flowOf(ModelDownloadStatus.Failed("boom")),
        )

        testee.downloadModel("en", "es").test {
            assertEquals(ModelDownloadStatus.Failed("boom"), awaitItem())
            awaitComplete()
        }
    }
}
