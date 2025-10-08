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

package com.duckduckgo.privacyprotectionspopup.impl

import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacyprotectionspopup.impl.db.PopupDismissDomainRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.only
import org.mockito.kotlin.verify
import java.time.Instant

@ExperimentalCoroutinesApi
class PrivacyProtectionsPopupDomainsCleanupWorkerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val popupDismissDomainRepository: PopupDismissDomainRepository = mock()
    private val timeProvider = FakeTimeProvider()

    private val subject = TestListenableWorkerBuilder<PrivacyProtectionsPopupDomainsCleanupWorker>(context = mock())
        .build()
        .also { worker ->
            worker.popupDismissDomainRepository = popupDismissDomainRepository
            worker.timeProvider = timeProvider
        }

    @Test
    fun whenDoWorkThenCleanUpOldEntriesFromPopupDismissDomainRepository() = runTest {
        timeProvider.time = Instant.parse("2023-11-29T10:15:30.000Z")

        val result = subject.doWork()

        verify(popupDismissDomainRepository, only())
            .removeEntriesOlderThan(Instant.parse("2023-10-30T10:15:30.000Z"))

        assertEquals(Result.success(), result)
    }
}
