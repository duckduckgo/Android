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
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.NotificationHandlerService.Companion.PIXEL_SUFFIX_EXTRA
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.USE_OUR_APP
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class NotificationHandlerServiceTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var mockPixel: Pixel = mock()
    private var mockUserStageStore: UserStageStore = mock()
    private var testee = NotificationHandlerService()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun before() {
        testee.pixel = mockPixel
        testee.context = context
        testee.notificationManager = NotificationManagerCompat.from(context)
        testee.userStageStore = mockUserStageStore
        testee.dispatcher = coroutinesTestRule.testDispatcherProvider
        testee.appCoroutineScope = TestCoroutineScope()
    }

    @Test
    fun whenIntentIsClearDataLaunchedThenCorrespondingPixelIsFired() {
        val intent = Intent(context, NotificationHandlerService::class.java)
        intent.type = CLEAR_DATA_LAUNCH
        intent.putExtra(PIXEL_SUFFIX_EXTRA, "abc")
        testee.onHandleIntent(intent)
        verify(mockPixel).fire(eq("mnot_l_abc"), any(), any())
    }

    @Test
    fun whenIntentIsClearDataCancelledThenCorrespondingPixelIsFired() {
        val intent = Intent(context, NotificationHandlerService::class.java)
        intent.type = CANCEL
        intent.putExtra(PIXEL_SUFFIX_EXTRA, "abc")
        testee.onHandleIntent(intent)
        verify(mockPixel).fire(eq("mnot_c_abc"), any(), any())
    }

    @Test
    fun whenIntentIsUseOurAppThenCorrespondingPixelIsFired() {
        val intent = Intent(context, NotificationHandlerService::class.java)
        intent.type = USE_OUR_APP
        intent.putExtra(PIXEL_SUFFIX_EXTRA, "abc")
        testee.onHandleIntent(intent)
        verify(mockPixel).fire(eq("mnot_l_abc"), any(), any())
    }

    @Test
    fun whenIntentIsUseOurAppThenRegisterInUseOurAppOnboardingStage() = coroutinesTestRule.runBlocking {
        val intent = Intent(context, NotificationHandlerService::class.java)
        intent.type = USE_OUR_APP
        intent.putExtra(PIXEL_SUFFIX_EXTRA, "abc")
        testee.onHandleIntent(intent)
        verify(mockUserStageStore).moveToStage(AppStage.USE_OUR_APP_ONBOARDING)
    }
}
