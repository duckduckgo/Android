/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service.state

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.WorkerThread
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.dao.HeartBeatEntity
import com.duckduckgo.mobile.android.vpn.heartbeat.VpnServiceHeartbeatMonitor
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class VpnStateMonitorService : Service() {

    @Inject lateinit var vpnDatabase: VpnDatabase

    @Inject
    @AppCoroutineScope lateinit var coroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    private val binder = Binder()

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("Bound to VPN")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.d("Unbound from VPN")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        coroutineScope.launch(dispatcherProvider.io()) {
            // check last state, if it was enabled then we store disabled state reason unknown
            vpnBringUpIfSuddenKill()
        }
        super.onDestroy()
    }

    private fun maybeUpdateVPNState(){
        val lastState = vpnDatabase.vpnServiceStateDao().getServiceStateStatsSince()
        // create method in dao that returns last service state
        // create interface in vpn-api Flow<VpnRunningState> (isRunning yes no and vpnstopreason)
        // impl interface in vpn as a Repository
        // use that repository from PrivacyReportViewModel
    }

    @WorkerThread
    private fun vpnBringUpIfSuddenKill() {
        val lastHearBeat = vpnDatabase.vpnHeartBeatDao().hearBeats().maxByOrNull { it.timestamp }
        if (true == lastHearBeat?.isAlive()) {
            Timber.d("Sudden stop, restarting VPN")
            TrackerBlockingVpnService.startService(applicationContext)
        } else {
            Timber.d("No need to restart the VPN")
        }
    }

    private fun HeartBeatEntity.isAlive(): Boolean {
        return VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE == type
    }
}
