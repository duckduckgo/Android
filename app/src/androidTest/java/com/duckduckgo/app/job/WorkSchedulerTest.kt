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

import com.duckduckgo.app.notification.AndroidNotificationScheduler
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Before
import org.junit.Test

class WorkSchedulerTest {

    private val notificationScheduler: AndroidNotificationScheduler = mock()
    private val jobCleaner: JobCleaner = mock()

    private lateinit var testee: AndroidWorkScheduler

    @Before
    fun before() {
        testee = AndroidWorkScheduler(
            TestCoroutineScope(),
            notificationScheduler,
            jobCleaner
        )
    }

    @Test
    fun schedulesNextNotificationAndCleansDeprecatedJobs() = runBlocking<Unit> {
        testee.scheduleWork()

        verify(notificationScheduler).scheduleNextNotification()
        verify(jobCleaner).cleanDeprecatedJobs()
    }
}
