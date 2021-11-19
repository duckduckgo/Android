/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.exception

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.device.DeviceInfo
import com.nhaarman.mockitokotlin2.*
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UncaughtExceptionRepositoryDbTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: UncaughtExceptionRepositoryDb

    private var uncaughtExceptionDao: UncaughtExceptionDao = mock()
    private var rootExceptionFinder = RootExceptionFinder()
    private var deviceInfo: DeviceInfo = mock()

    private val entity = UncaughtExceptionEntity(exceptionSource = UncaughtExceptionSource.GLOBAL, message = "message", version = "version", timestamp = 1000)

    @Before
    fun before() {
        testee = UncaughtExceptionRepositoryDb(uncaughtExceptionDao, rootExceptionFinder, deviceInfo)
        whenever(deviceInfo.appVersion).thenReturn("version")
        whenever(uncaughtExceptionDao.getLatestException()).thenReturn(entity)
    }

    @Test
    fun whenLatestExceptionIsNullThenReturnTrue() = runBlocking {
        whenever(uncaughtExceptionDao.getLatestException()).thenReturn(null)
        assertTrue(testee.isOverTimeThreshold(entity.copy(timestamp = 1500)))
    }

    @Test
    fun whenIsBelowTimeThresholdAndSameExceptionThenReturnFalseAndUpdateTimestamp() = runBlocking {
        assertFalse(testee.isOverTimeThreshold(entity.copy(timestamp = 1500)))
        verify(uncaughtExceptionDao).update(entity.copy(timestamp = 1500))
    }

    @Test
    fun whenIsAboveTimeThresholdAndSameExceptionThenReturnTrue() = runBlocking {
        assertTrue(testee.isOverTimeThreshold(entity.copy(timestamp = 2001)))
    }

    @Test
    fun whenIsDifferentExceptionThenReturnTrue() = runBlocking {
        assertTrue(testee.isOverTimeThreshold(entity.copy(message = "different message", timestamp = 1500)))
        assertTrue(testee.isOverTimeThreshold(entity.copy(version = "different version", timestamp = 1500)))
        assertTrue(testee.isOverTimeThreshold(entity.copy(exceptionSource = UncaughtExceptionSource.HIDE_CUSTOM_VIEW, timestamp = 1500)))

        assertTrue(testee.isOverTimeThreshold(entity.copy(message = "different message", version = "different version", timestamp = 1500)))
        assertTrue(testee.isOverTimeThreshold(entity.copy(message = "different message", exceptionSource = UncaughtExceptionSource.HIDE_CUSTOM_VIEW, timestamp = 1500)))
        assertTrue(testee.isOverTimeThreshold(entity.copy(version = "different version", exceptionSource = UncaughtExceptionSource.HIDE_CUSTOM_VIEW, timestamp = 1500)))

        assertTrue(testee.isOverTimeThreshold(entity.copy(message = "different message", version = "different version", exceptionSource = UncaughtExceptionSource.HIDE_CUSTOM_VIEW, timestamp = 1500)))
    }
}
