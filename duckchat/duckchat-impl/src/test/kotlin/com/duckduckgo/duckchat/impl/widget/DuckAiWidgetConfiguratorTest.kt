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
package com.duckduckgo.duckchat.impl.widget

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.DuckChatLaunchAction
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetConfiguratorTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val duckChat: DuckChatInternal = mock()
    private val remoteViews: RemoteViews = mock()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private val testee = DuckAiWidgetConfigurator(
        duckChat = duckChat,
        intents = fakeDuckAiWidgetIntents(),
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenPromptBarTappedThenLaunchesNewChatFromWidget() = runTest {
        stubDuckAi(enabled = true)

        val launched = populateAndCaptureClickIntent(R.id.promptBarContainer)

        assertLaunch(launched, url = NEW_CHAT_URL)
    }

    @Test
    fun whenVoiceActionTappedThenLaunchesVoiceChatUrl() = runTest {
        stubDuckAi(enabled = true)

        val launched = populateAndCaptureClickIntent(R.id.actionVoice)

        assertLaunch(launched, url = VOICE_URL)
    }

    @Test
    fun whenCreateImageTappedThenLaunchesImageGeneration() = runTest {
        stubDuckAi(enabled = true)

        val launched = populateAndCaptureClickIntent(R.id.actionCreateImage)

        assertLaunch(launched, url = NEW_CHAT_URL, action = DuckChatLaunchAction.ImageGeneration)
    }

    @Test
    fun whenTakePhotoTappedThenLaunchesCameraCapture() = runTest {
        stubDuckAi(enabled = true)

        val launched = populateAndCaptureClickIntent(R.id.actionTakePhoto)

        assertLaunch(launched, url = NEW_CHAT_URL, action = DuckChatLaunchAction.CameraCapture)
    }

    @Test
    fun whenDuckAiDisabledThenNoClickTargetsWired() = runTest {
        whenever(duckChat.isEnabled()).thenReturn(false)

        testee.populateRemoteViews(activity, remoteViews)

        verify(remoteViews, never()).setOnClickPendingIntent(any(), any())
    }

    @Test
    fun whenAllTargetsBuiltThenRequestCodesAreDistinct() {
        val requestCodes = DuckAiWidgetTarget.entries.map { it.requestCode }

        assertEquals(requestCodes.size, requestCodes.toSet().size)
    }

    private fun stubDuckAi(enabled: Boolean) {
        whenever(duckChat.isEnabled()).thenReturn(enabled)
        whenever(duckChat.getDuckChatUrl("", false)).thenReturn(NEW_CHAT_URL)
        whenever(duckChat.getVoiceDuckChatUrl()).thenReturn(VOICE_URL)
    }

    private suspend fun populateAndCaptureClickIntent(viewId: Int): Intent {
        testee.populateRemoteViews(activity, remoteViews)
        val captor = argumentCaptor<PendingIntent>()
        verify(remoteViews).setOnClickPendingIntent(eq(viewId), captor.capture())
        return shadowOf(captor.firstValue).savedIntent
    }

    private companion object {
        const val NEW_CHAT_URL = "https://duck.ai/"
        const val VOICE_URL = "https://duck.ai/?mode=voice-mode"
    }
}
