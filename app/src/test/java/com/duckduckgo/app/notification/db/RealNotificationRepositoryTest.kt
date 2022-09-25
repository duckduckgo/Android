/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.notification.db

import com.duckduckgo.app.notification.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class RealNotificationRepositoryTest {

    private lateinit var testee: NotificationRepository
    private val mockNotificationDao: NotificationDao = mock()

    @Before
    fun before() {
        testee = RealNotificationRepository(mockNotificationDao)
    }

    @Test
    fun whenExistsCallThenNotificationDaoCalled() = runTest {
        testee.exists("id")
        verify(mockNotificationDao).exists("id")
    }
}
