/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.notification

import android.app.TaskStackBuilder
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.NotificationHandlerService.Companion.PIXEL_SUFFIX_EXTRA
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCH
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class NotificationHandlerServiceTest {

    @get:Rule var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: NotificationHandlerService
    private lateinit var mockPixel: Pixel

    private val appContext =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun before() {
        mockPixel = mock()
        val mockTaskStackBuilderFactory: TaskStackBuilderFactory = mock()
        val taskStackBuilder: TaskStackBuilder = mock()

        whenever(taskStackBuilder.addNextIntentWithParentStack(any())).thenReturn(taskStackBuilder)
        whenever(mockTaskStackBuilderFactory.createTaskBuilder()).thenReturn(taskStackBuilder)

        testee =
            NotificationHandlerService().apply {
                pixel = mockPixel
                this.context = appContext
                notificationManager = NotificationManagerCompat.from(context)
                dispatcher = coroutinesTestRule.testDispatcherProvider
                taskStackBuilderFactory = mockTaskStackBuilderFactory
            }
    }

    @Test
    fun whenIntentIsClearDataLaunchedThenCorrespondingPixelIsFired() {
        val intent = Intent(appContext, NotificationHandlerService::class.java)
        intent.type = CLEAR_DATA_LAUNCH
        intent.putExtra(PIXEL_SUFFIX_EXTRA, "abc")
        testee.onHandleIntent(intent)
        verify(mockPixel).fire(eq("mnot_l_abc"), any(), any())
    }

    @Test
    fun whenIntentIsClearDataCancelledThenCorrespondingPixelIsFired() {
        val intent = Intent(appContext, NotificationHandlerService::class.java)
        intent.type = CANCEL
        intent.putExtra(PIXEL_SUFFIX_EXTRA, "abc")
        testee.onHandleIntent(intent)
        verify(mockPixel).fire(eq("mnot_c_abc"), any(), any())
    }
}
