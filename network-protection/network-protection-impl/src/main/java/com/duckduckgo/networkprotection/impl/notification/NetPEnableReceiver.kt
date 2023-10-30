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

package com.duckduckgo.networkprotection.impl.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.vpn.Vpn
import dagger.android.AndroidInjection
import javax.inject.Inject
import kotlinx.coroutines.*
import logcat.LogPriority
import logcat.logcat

@InjectWith(ReceiverScope::class)
class NetPEnableReceiver : BroadcastReceiver() {

//    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var vpn: Vpn

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var context: Context

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)

        logcat { "NetPEnableReceiver onReceive ${intent.action}" }
        val pendingResult = goAsync()

        if (intent.action == ACTION_NETP_ENABLE) {
            logcat { "NetP will restart because the user asked it" }
            goAsync(pendingResult) {
                vpn.start()
            }
        } else if (intent.action == ACTION_NETP_SNOOZE) {
            logcat { "NetP will snooze because the user asked it" }
            goAsync(pendingResult) {
                snoozeAndScheduleWakeUp()
            }
        } else if (intent.action == ACTION_NETP_DISABLE) {
            logcat { "NetP will disable because the user asked it" }
            goAsync(pendingResult) {
                vpn.stop()
            }
        } else {
            logcat(LogPriority.WARN) { "NetPEnableReceiver: unknown action" }
            pendingResult?.finish()
        }
    }

    private suspend fun snoozeAndScheduleWakeUp() = withContext(dispatcherProvider.io()) {
        vpn.snooze(10_000)

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NetPEnableReceiver::class.java).apply {
                action = ACTION_NETP_ENABLE
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        if (alarmIntent != null && alarmManager != null) {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 10_000,
                alarmIntent,
            )
        }
    }

    companion object {
        internal const val ACTION_NETP_ENABLE = "com.duckduckgo.networkprotection.notification.ACTION_NETP_ENABLE"
        internal const val ACTION_NETP_DISABLE = "com.duckduckgo.networkprotection.notification.ACTION_NETP_DISABLE"
        internal const val ACTION_NETP_SNOOZE = "com.duckduckgo.networkprotection.notification.ACTION_NETP_SNOOZE"
    }
}

@Suppress("NoHardcodedCoroutineDispatcher")
fun goAsync(
    pendingResult: BroadcastReceiver.PendingResult?,
    coroutineScope: CoroutineScope = GlobalScope,
    block: suspend () -> Unit,
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            block()
        } finally {
            // Always call finish(), even if the coroutineScope was cancelled
            pendingResult?.finish()
        }
    }
}
