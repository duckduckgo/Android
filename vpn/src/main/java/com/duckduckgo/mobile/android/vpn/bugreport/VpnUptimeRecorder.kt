/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.bugreport

import android.os.SystemClock
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import dagger.SingleInstanceIn
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class)
class VpnUptimeRecorder @Inject constructor(val pixels: DeviceShieldPixels) : VpnServiceCallbacks {

    private val vpnStartTime = AtomicLong(0)

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        vpnStartTime.set(SystemClock.elapsedRealtime())
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        Timber.d("VpnUptimeRecorder: vpn was up for ${getVpnUpTime()} ms")
        pixels.reportVpnUptime(getVpnUpTime())
        vpnStartTime.set(0)
    }

    fun getVpnUpTime(): Long {
        val startTime = vpnStartTime.get()
        return if (startTime == 0L) 0L else SystemClock.elapsedRealtime() - startTime
    }
}
