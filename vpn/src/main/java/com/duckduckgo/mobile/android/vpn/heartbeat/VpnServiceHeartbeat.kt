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

package com.duckduckgo.mobile.android.vpn.heartbeat

import android.content.Context
import android.os.Process
import androidx.work.WorkManager
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.di.VpnScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

interface VpnServiceHeartbeat {
    suspend fun startHeartbeat()

    fun stopHeartbeat()
}

@ContributesBinding(VpnObjectGraph::class)
@VpnScope
class VpnServiceHeartbeatImpl @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager
) : VpnServiceHeartbeat {
    private val isActive = AtomicBoolean(false)

    override suspend fun startHeartbeat() {
        if (isActive.compareAndSet(false, true)) {
            isActive.set(TrackerBlockingVpnService.isServiceRunning(context))
            while (isActive.get()) {
                Timber.d("(${Process.myPid()}) - Sending heartbeat")
                VpnHeartbeatReceiverWorker.sendAliveHeartbeat(workManager, HEART_BEAT_PERIOD_SECONDS)
                delay(TimeUnit.SECONDS.toMillis(HEART_BEAT_PERIOD_SECONDS))
            }
        }
    }

    override fun stopHeartbeat() {
        isActive.set(false)
        VpnHeartbeatReceiverWorker.sendStopHeartbeat(workManager)
    }

    companion object {
        private const val HEART_BEAT_PERIOD_SECONDS: Long = 30
    }
}
