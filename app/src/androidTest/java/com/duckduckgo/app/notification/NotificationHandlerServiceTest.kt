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
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.notification.NotificationHandlerService.Companion.PIXEL_SUFFIX_EXTRA
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCH
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import org.mockito.kotlin.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class NotificationHandlerServiceTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: NotificationHandlerService
    private lateinit var mockPixel: Pixel
    private val plugin = FakeSchedulablePlugin()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun before() {
        mockPixel = mock()
        val mockTaskStackBuilderFactory: TaskStackBuilderFactory = mock()
        val taskStackBuilder: TaskStackBuilder = mock()

        whenever(taskStackBuilder.addNextIntentWithParentStack(any())).thenReturn(taskStackBuilder)
        whenever(mockTaskStackBuilderFactory.createTaskBuilder()).thenReturn(taskStackBuilder)

        testee = NotificationHandlerService().apply {
            pixel = mockPixel
            this.context = appContext
            notificationManager = NotificationManagerCompat.from(context)
            dispatcher = coroutinesTestRule.testDispatcherProvider
            taskStackBuilderFactory = mockTaskStackBuilderFactory
            schedulableNotificationPluginPoint = FakeSchedulablePluginPoint(plugin)
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

    @Test
    fun whenOnHandleIntentInPluginWithCancelTypeThenExecuteOnNotificationCancelled() {
        val intent = Intent("test").apply {
            type = "test.cancel"
            putExtra("PIXEL_SUFFIX_EXTRA", "test")
        }
        testee.onHandleIntent(intent)

        assertEquals(3, plugin.schedulable)
    }

    @Test
    fun whenOnHandleIntentInPluginWithLaunchTypeThenExecuteOnNotificationLaunched() {
        val intent = Intent("test").apply {
            type = "test.launch"
            putExtra("PIXEL_SUFFIX_EXTRA", "test")
        }
        testee.onHandleIntent(intent)

        assertEquals(2, plugin.schedulable)
    }

    class FakeSchedulablePluginPoint(private val plugin: SchedulableNotificationPlugin) : PluginPoint<SchedulableNotificationPlugin> {
        override fun getPlugins(): Collection<SchedulableNotificationPlugin> {
            return listOf(plugin)
        }
    }

    class FakeSchedulablePlugin : SchedulableNotificationPlugin {
        var schedulable = 0

        override fun getSchedulableNotification(): SchedulableNotification {
            return TestNotification()
        }

        override fun onNotificationLaunched() {
            schedulable = 2
        }

        override fun onNotificationCancelled() {
            schedulable = 3
        }

        override fun onNotificationShown() {
            schedulable = 4
        }

        override fun getSpecification(): NotificationSpec {
            return TestSpec()
        }
    }

    class TestNotification : SchedulableNotification {
        override val id: String = "id"
        override val launchIntent: String = "test.launch"
        override val cancelIntent: String = "test.cancel"
        override suspend fun canShow(): Boolean = true
        override suspend fun buildSpecification(): NotificationSpec = TestSpec()
    }

    class TestSpec : NotificationSpec {
        override val channel: Channel = Channel("id", 1, 1)
        override val systemId: Int = 1
        override val name: String = "test"
        override val icon: Int = 1
        override val title: String = "test"
        override val description: String = "test"
        override val launchButton: String = "test"
        override val closeButton: String = "test"
        override val pixelSuffix: String = "test"
        override val autoCancel: Boolean = true
        override val bundle: Bundle = Bundle()
        override val color: Int = 1
    }
}
