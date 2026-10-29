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

package com.duckduckgo.privacy.config.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.android.AndroidInjection
import com.duckduckgo.di.scopes.ReceiverScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class PrivacyConfigOverrideReceiver : BroadcastReceiver() {
    @Inject lateinit var loader: PrivacyConfigInternalLoader

    @Inject @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override fun onReceive(context: Context, intent: Intent) {
        val command = when (intent.action) {
            ACTION_SET_URL -> OverrideCommand.SetUrl(intent.getStringExtra(EXTRA_URL))
            ACTION_RESET -> OverrideCommand.Reset
            else -> return
        }
        AndroidInjection.inject(this, context)
        // Keep the broadcast pending until the background command finishes.
        val pendingResult = goAsync()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            try {
                loader.handleCommand(command)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_URL = "com.duckduckgo.privacy.config.internal.SET_URL"
        const val ACTION_RESET = "com.duckduckgo.privacy.config.internal.RESET"
        const val EXTRA_URL = "url"
    }
}
