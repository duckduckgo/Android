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

package com.duckduckgo.downloads.impl

import app.cash.turbine.test
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealNewDownloadStateTest {

    private val newDownloadState = RealNewDownloadState(FakeSharedPreferencesProvider())

    @Test
    fun whenNothingHasHappenedThenHasNewDownloadIsFalse() {
        assertFalse(newDownloadState.hasNewDownload())
    }

    @Test
    fun whenOnDownloadCompleteThenHasNewDownloadIsTrue() {
        newDownloadState.onDownloadComplete()

        assertTrue(newDownloadState.hasNewDownload())
    }

    @Test
    fun whenOnDownloadsScreenViewedAfterDownloadCompleteThenHasNewDownloadIsFalse() {
        newDownloadState.onDownloadComplete()

        newDownloadState.onDownloadsScreenViewed()

        assertFalse(newDownloadState.hasNewDownload())
    }

    @Test
    fun whenOnDownloadCompleteThenFlowEmitsTrue() = runTest {
        newDownloadState.hasNewDownloadFlow.test {
            assertFalse(awaitItem())

            newDownloadState.onDownloadComplete()

            assertTrue(awaitItem())
        }
    }

    @Test
    fun whenOnDownloadsScreenViewedThenFlowEmitsFalse() = runTest {
        newDownloadState.onDownloadComplete()

        newDownloadState.hasNewDownloadFlow.test {
            assertTrue(awaitItem())

            newDownloadState.onDownloadsScreenViewed()

            assertFalse(awaitItem())
        }
    }
}
