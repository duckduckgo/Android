/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacy.config.internal

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PrivacyConfigOverrideWorkerTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    @Test
    fun whenDownloadFailsThenCompleteCommandSoQueuedResetCanRun() = runTest {
        val downloader = mock<PrivacyConfigDownloader>()
        whenever(downloader.download(force = true)).thenReturn(PrivacyConfigDownloader.ConfigDownloadResult.Error("offline"))
        val loader = PrivacyConfigInternalLoader(downloader, mock<DevPrivacyConfigSettingsDataStore>())
        val worker = TestListenableWorkerBuilder<PrivacyConfigOverrideWorker>(
            context = mock(),
            inputData = workDataOf("action" to "com.duckduckgo.privacy.config.internal.RESET"),
        ).build()
        worker.loader = loader
        worker.dispatcherProvider = coroutineRule.testDispatcherProvider
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
