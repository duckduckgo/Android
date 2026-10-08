/*
 * Copyright (c) 2023 DuckDuckGo
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader.Companion.ACTION_RESET
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader.Companion.ACTION_SET_URL
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader.Companion.EXTRA_URL
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/** Manifest permission restricts this internal-only entry point to DUMP-capable senders. */
@InjectWith(ReceiverScope::class)
class PrivacyConfigOverrideReceiver : BroadcastReceiver() {
    @Inject lateinit var workManager: WorkManager

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject @AppCoroutineScope lateinit var coroutineScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_SET_URL && action != ACTION_RESET) return
        val url = intent.getStringExtra(EXTRA_URL)
        if (action == ACTION_SET_URL && !PrivacyConfigInternalLoader.isValidUrl(url)) {
            logcat(WARN) { "Privacy config override rejected: expected an HTTP(S) URL with a host" }
            return
        }
        AndroidInjection.inject(this, context)
        val request = OneTimeWorkRequestBuilder<PrivacyConfigOverrideWorker>()
            .setInputData(workDataOf(PrivacyConfigOverrideWorker.KEY_ACTION to action, EXTRA_URL to url))
            .build()
        // Preserve command order, without retries or periodic refresh.
        val pendingResult = goAsync()
        coroutineScope.launch(dispatcherProvider.io()) {
            try {
                workManager.enqueueUniqueWork("privacy-config-override-command", ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
