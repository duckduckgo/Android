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

import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.common.utils.plugins.PluginPoint
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NotificationHandlerServiceTest {

    private lateinit var testee: NotificationHandlerService
    private val mockSchedulablePluginPoint = mock<PluginPoint<SchedulableNotificationPlugin>>()
    private val mockNotificationPlugin = mock<SchedulableNotificationPlugin>()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun setup() {
        testee = NotificationHandlerService().apply {
            schedulableNotificationPluginPoint = mockSchedulablePluginPoint
        }
    }

    @Test
    fun whenIntentIsNullThenOnNotificationCancelledIsNotCalledOnAnyPlugin() {
        val intent = null

        testee.onHandleIntent(intent)

        testee.schedulableNotificationPluginPoint.getPlugins().forEach {
            verify(it, never()).onNotificationCancelled()
        }
    }

    @Test
    fun whenIntentTypeIsNullThenOnNotificationCancelledIsNotCalledOnAnyPlugin() {
        val intent = Intent(appContext, NotificationHandlerService::class.java).apply {
            type = null
        }

        testee.onHandleIntent(intent)

        testee.schedulableNotificationPluginPoint.getPlugins().forEach {
            verify(it, never()).onNotificationCancelled()
        }
    }

    @Test
    fun whenIntentTypeDoesntMatchAnyPluginThenOnNotificationCancelledIsNotCalledOnAnyPlugin() {
        whenever(mockSchedulablePluginPoint.getPlugins()).thenReturn(listOf(mockNotificationPlugin))
        whenever(mockNotificationPlugin.getSchedulableNotification()).thenReturn(TestNotification())
        val intent = Intent(appContext, NotificationHandlerService::class.java).apply {
            type = "unknown"
        }

        testee.onHandleIntent(intent)

        testee.schedulableNotificationPluginPoint.getPlugins().forEach {
            verify(it, never()).onNotificationCancelled()
        }
    }

    @Test
    fun whenIntentTypeMatchesAnyPluginThenOnNotificationCancelledIsCalledOnThePlugin() {
        whenever(mockSchedulablePluginPoint.getPlugins()).thenReturn(listOf(mockNotificationPlugin))
        whenever(mockNotificationPlugin.getSchedulableNotification()).thenReturn(TestNotification())
        val intent = Intent(appContext, NotificationHandlerService::class.java).apply {
            type = TestNotification::class.java.simpleName
        }

        testee.onHandleIntent(intent)

        testee.schedulableNotificationPluginPoint.getPlugins().forEach {
            verify(it).onNotificationCancelled()
        }
    }

    class TestNotification : SchedulableNotification {
        override val id: String = "id"
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
