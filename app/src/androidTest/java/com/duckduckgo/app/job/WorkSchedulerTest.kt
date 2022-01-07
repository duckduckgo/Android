/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.job

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.AndroidNotificationScheduler
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class WorkSchedulerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val notificationScheduler: AndroidNotificationScheduler = mock()
    private val jobCleaner: JobCleaner = mock()

    private lateinit var testee: AndroidWorkScheduler

    @Before
    fun before() {
        testee = AndroidWorkScheduler(TestScope(), notificationScheduler, jobCleaner, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun schedulesNextNotificationAndCleansDeprecatedJobs() = runTest {
        testee.scheduleWork()

        verify(notificationScheduler).scheduleNextNotification()
        verify(jobCleaner).cleanDeprecatedJobs()
    }
}
