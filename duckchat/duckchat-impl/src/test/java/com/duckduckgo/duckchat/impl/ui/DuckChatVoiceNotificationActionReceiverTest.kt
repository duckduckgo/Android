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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.ui.DuckChatVoiceNotificationActionReceiver.Companion.endSessionIntent
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class DuckChatVoiceNotificationActionReceiverTest {

    private val context: Context = mock()
    private val voiceSessionStateManager: VoiceSessionStateManager = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private lateinit var receiver: DuckChatVoiceNotificationActionReceiver

    @Before
    fun setUp() {
        receiver = DuckChatVoiceNotificationActionReceiver(context, voiceSessionStateManager)
    }

    @Test
    fun whenOnReceiveWithValidIntentThenTriggersVoiceSessionEnd() {
        val intent = endSessionIntent(context, TAB_ID)

        receiver.onReceive(context, intent)

        verify(voiceSessionStateManager).triggerVoiceSessionEnd(TAB_ID)
    }

    @Test
    fun whenOnReceiveWithWrongActionThenDoesNothing() {
        val intent = Intent("com.duckduckgo.some.other.action").apply {
            putExtra("EXTRA_TAB_ID", TAB_ID)
        }

        receiver.onReceive(context, intent)

        verifyNoInteractions(voiceSessionStateManager)
    }

    @Test
    fun whenOnReceiveWithoutTabIdThenDoesNothing() {
        val action = endSessionIntent(context, TAB_ID).action
        val intent = Intent(action)

        receiver.onReceive(context, intent)

        verify(voiceSessionStateManager, never()).triggerVoiceSessionEnd(org.mockito.kotlin.any())
    }

    @Test
    fun whenOnReceiveWithBlankTabIdThenDoesNothing() {
        val intent = endSessionIntent(context, "   ")

        receiver.onReceive(context, intent)

        verify(voiceSessionStateManager, never()).triggerVoiceSessionEnd(org.mockito.kotlin.any())
    }

    @Test
    fun whenOnReceiveWithEmptyTabIdThenDoesNothing() {
        val intent = endSessionIntent(context, "")

        receiver.onReceive(context, intent)

        verify(voiceSessionStateManager, never()).triggerVoiceSessionEnd(org.mockito.kotlin.any())
    }

    @Test
    fun whenEndSessionIntentThenIntentTargetsCurrentPackage() {
        val intent = endSessionIntent(context, TAB_ID)

        assertEquals(context.packageName, intent.`package`)
    }

    companion object {
        private const val TAB_ID = "tab-123"
    }
}
