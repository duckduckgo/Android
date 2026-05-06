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
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.utils.extensions.registerNotExportedReceiver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class DuckChatVoiceNotificationActionReceiver @Inject constructor(
    private val context: Context,
    private val browserNav: BrowserNav,
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

        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_OPEN_CHAT -> openExistingTab(context, tabId)
            ACTION_END_SESSION -> Unit // intentional no-op for now
        }
    }

    private fun openExistingTab(context: Context, tabId: String) {
        val intent = browserNav.openExistingTab(context, tabId).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    companion object {
        private const val INTENT_VOICE_NOTIFICATION_ACTION = "com.duckduckgo.duckchat.voice.notification.action"
        private const val EXTRA_ACTION = "EXTRA_ACTION"
        private const val EXTRA_TAB_ID = "EXTRA_TAB_ID"
        private const val ACTION_OPEN_CHAT = "ACTION_OPEN_CHAT"
        private const val ACTION_END_SESSION = "ACTION_END_SESSION"

        fun openChatIntent(
            context: Context,
            tabId: String,
        ): Intent =
            Intent(INTENT_VOICE_NOTIFICATION_ACTION).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ACTION, ACTION_OPEN_CHAT)
                putExtra(EXTRA_TAB_ID, tabId)
            }

        fun endSessionIntent(
            context: Context,
            tabId: String,
        ): Intent =
            Intent(INTENT_VOICE_NOTIFICATION_ACTION).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ACTION, ACTION_END_SESSION)
                putExtra(EXTRA_TAB_ID, tabId)
            }
    }
}
