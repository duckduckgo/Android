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

package com.duckduckgo.networkprotection.internal.feature.snooze

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.Vpn
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.internal.feature.NetPInternalFeatureToggles
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class VpnCallStateListener @Inject constructor(
    private val netPInternalFeatureToggles: NetPInternalFeatureToggles,
    private val dispatcherProvider: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val vpn: Vpn,
) : VpnServiceCallbacks, PhoneStateListener() {
    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
    }
    private val job = ConflatedJob()
    private var listener: PhoneStateListener? = null

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        fun registerListener() {
            runCatching {
                telephonyManager?.let { tm ->
                    logcat { "CALL_STATE listener registered" }
                    telephonyManager?.listen(this@VpnCallStateListener, LISTEN_CALL_STATE)
                    listener = this@VpnCallStateListener
                }
            }.onFailure { t ->
                logcat(LogPriority.ERROR) { "CALL_STATE error registering: ${t.asLog()}" }
            }
        }

        job += coroutineScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                delay(2000)
                if (listener == null && netPInternalFeatureToggles.snoozeWhileCalling().isEnabled()) {
                    registerListener()
                } else if (listener != null && !netPInternalFeatureToggles.snoozeWhileCalling().isEnabled()) {
                    unregisterListener()
                }
            }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        unregisterListener()
        job.cancel()
    }

    @Deprecated("Deprecated in Java")
    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat { "Call state: $state" }
            if (netPInternalFeatureToggles.snoozeWhileCalling().isEnabled()) {
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    vpn.start()
                } else {
                    vpn.stop()
                }
            }
        }
    }

    private fun unregisterListener() {
        runCatching {
            listener?.let {
                logcat { "CALL_STATE listener un-registered" }
                telephonyManager?.listen(this@VpnCallStateListener, LISTEN_NONE)
            }
        }.onFailure { t ->
            logcat(LogPriority.ERROR) { "CALL_STATE error un-registering: ${t.asLog()}" }
        }
        listener = null
    }

    private fun hasPhoneStatePermission(): Boolean {
        return if (appBuildConfig.sdkInt >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
