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

package com.duckduckgo.httpsupgrade.impl

import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class HttpsUpgraderImplTest {
    private val dao: PrivacyProtectionCountDao = mock()

    private class WriteFailure(val marker: Any) : RuntimeException()

    @Test
    fun whenRecordingUpgradeThenOnlyUpgradeCountIsIncrementedOnIo() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val testee = HttpsUpgraderImpl(
            mock(),
            mock(),
            mock(),
            mock(),
            dao,
            object : DispatcherProvider {
                override fun io() = ioDispatcher
            },
        )
        val recording = async(start = CoroutineStart.UNDISPATCHED) { testee.recordUpgrade() }
        verify(dao, never()).incrementUpgradeCount()

        runCurrent()
        recording.await()

        verify(dao).incrementUpgradeCount()
        verify(dao, never()).incrementBlockedTrackerCount()
    }

    @Test
    fun whenDaoFailsThenRecordUpgradePropagatesFailure() = runTest {
        val failure = WriteFailure(Any())
        doThrow(failure).`when`(dao).incrementUpgradeCount()
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val testee = HttpsUpgraderImpl(
            mock(),
            mock(),
            mock(),
            mock(),
            dao,
            object : DispatcherProvider {
                override fun io() = ioDispatcher
            },
        )
        val recording = async(start = CoroutineStart.UNDISPATCHED) { runCatching { testee.recordUpgrade() } }
        runCurrent()

        assertSame(failure, recording.await().exceptionOrNull() ?: fail("Expected DAO failure"))
    }
}
