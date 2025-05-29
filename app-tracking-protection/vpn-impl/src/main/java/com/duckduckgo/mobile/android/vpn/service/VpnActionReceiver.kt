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

package com.duckduckgo.mobile.android.vpn.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.Vpn
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import dagger.android.AndroidInjection
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat

@InjectWith(ReceiverScope::class)
class VpnActionReceiver : BroadcastReceiver() {
    @Inject lateinit var vpn: Vpn

    @Inject lateinit var networkProtectionState: NetworkProtectionState

    @Inject lateinit var appTrackingProtection: AppTrackingProtection

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var context: Context

    @Inject lateinit var deviceShieldPixels: DeviceShieldPixels

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)

        logcat { "VpnActionReceiver onReceive ${intent.action}" }
        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_VPN_SNOOZE_END -> {
                logcat { "Entire VPN will be enabled because the user asked it" }
                goAsync(pendingResult) {
                    deviceShieldPixels.reportVpnSnoozedEnded()
                    vpn.start()
                }
            }

            ACTION_VPN_DISABLE -> {
                logcat { "Entire VPN will disabled because the user asked it" }
                goAsync(pendingResult) {
                    vpn.stop()
                }
            }

            ACTION_VPN_SNOOZE -> {
                logcat { "Entire VPN will snooze because the user asked it" }
                goAsync(pendingResult) {
                    deviceShieldPixels.reportVpnSnoozedStarted()
                    snoozeAndScheduleWakeUp()
                }
            }

            else -> {
                logcat(WARN) { "VpnActionReceiver: unknown action: ${intent.action}" }
                pendingResult?.finish()
            }
        }
    }

    private suspend fun snoozeAndScheduleWakeUp() = withContext(dispatcherProvider.io()) {
        vpn.snooze(DEFAULT_SNOOZE_LENGTH_IN_MILLIS)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, VpnActionReceiver::class.java).apply {
                action = ACTION_VPN_SNOOZE_END
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        if (alarmIntent != null && alarmManager != null) {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + DEFAULT_SNOOZE_LENGTH_IN_MILLIS,
                alarmIntent,
            )
        }
    }

    companion object {
        internal const val ACTION_VPN_SNOOZE_END = "com.duckduckgo.vpn.ACTION_VPN_SNOOZE_END"
        internal const val ACTION_VPN_DISABLE = "com.duckduckgo.vpn.ACTION_VPN_DISABLE"
        internal const val ACTION_VPN_SNOOZE = "com.duckduckgo.vpn.ACTION_VPN_SNOOZE"
        private val DEFAULT_SNOOZE_LENGTH_IN_MILLIS = TimeUnit.MINUTES.toMillis(20)
    }
}
