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

package com.duckduckgo.sync.internal.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.android.AndroidInjection
import com.duckduckgo.di.scopes.ReceiverScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Lets adb drive Sync setup in internal debug builds (registered in the debug manifest only), for example:
 * `adb shell am broadcast -a com.duckduckgo.sync.internal.DEBUG -p <package> --es command status`
 * Each result is logged as `SyncDebugBroadcast: <command>: <result>`. Commands that change the account only run on staging.
 */
@InjectWith(ReceiverScope::class)
class SyncDebugBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var commands: SyncDebugCommands

    @Inject @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        AndroidInjection.inject(this, context)
        val command = intent.getStringExtra(EXTRA_COMMAND)
        val on = intent.getBooleanExtra(EXTRA_ON, true)
        val pendingResult = goAsync()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            try {
                logcat { "SyncDebugBroadcast: $command: ${commands.run(command, on)}" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(WARN) { "SyncDebugBroadcast: $command: failed: $e" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.duckduckgo.sync.internal.DEBUG"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ON = "on"
    }
}
