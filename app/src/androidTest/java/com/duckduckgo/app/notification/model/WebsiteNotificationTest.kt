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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.notification.model

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.notification.db.NotificationDao
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class WebsiteNotificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationsDao: NotificationDao = mock()

    private lateinit var testee: WebsiteNotification

    @Before
    fun before() {
        testee = WebsiteNotification(context, notificationsDao, URL, R.string.yes, R.string.no, PIXEL)
    }

    @Test
    fun whenNotificationNotSeenThenCanShowIsTrue() = runTest {
        whenever(notificationsDao.exists(any())).thenReturn(false)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationAlreadySeenThenCanShowIsFalse() = runTest {
        whenever(notificationsDao.exists(any())).thenReturn(true)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenBuildSpecificationSetCorrectUrl() = runTest {
        val spec = testee.buildSpecification()
        assertEquals(URL, spec.bundle.get(WebsiteNotificationSpecification.WEBSITE_KEY))
    }

    @Test
    fun whenBuildSpecificationSetCorrectPixelSuffix() = runTest {
        val spec = testee.buildSpecification()
        assertEquals(PIXEL, spec.pixelSuffix)
    }

    companion object {
        private const val URL = "test"
        private const val PIXEL = "pixel"
    }
}
