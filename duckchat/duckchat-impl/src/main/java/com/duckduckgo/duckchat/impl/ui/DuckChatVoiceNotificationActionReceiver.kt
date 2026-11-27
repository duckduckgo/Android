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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.extensions.registerNotExportedReceiver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import javax.inject.Inject

@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
@SingleIn(AppScope::class)
class DuckChatVoiceNotificationActionReceiver @Inject constructor(
    private val context: Context,
    private val voiceSessionStateManager: VoiceSessionStateManager,
    private val duckChatPixels: DuckChatPixels,
) : BroadcastReceiver(), MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        context.registerNotExportedReceiver(this, IntentFilter(INTENT_VOICE_NOTIFICATION_ACTION))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        context.unregisterReceiver(this)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != INTENT_VOICE_NOTIFICATION_ACTION) return
        val tabId = intent.getStringExtra(EXTRA_TAB_ID)?.takeIf { it.isNotBlank() } ?: return

        duckChatPixels.reportVoiceNotificationEndChatTapped()
        voiceSessionStateManager.triggerVoiceSessionEnd(tabId)
    }

    companion object {
        private const val INTENT_VOICE_NOTIFICATION_ACTION = "com.duckduckgo.duckchat.voice.notification.action"
        private const val EXTRA_TAB_ID = "EXTRA_TAB_ID"

        fun endSessionIntent(
            context: Context,
            tabId: String,
        ): Intent =
            Intent(INTENT_VOICE_NOTIFICATION_ACTION).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_TAB_ID, tabId)
            }
    }
}
