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

package com.duckduckgo.networkprotection.impl.reconnect

import android.content.Context
import android.os.SystemClock
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.connectivity.VpnConnectivityLossListenerPlugin
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.alerts.reconnect.NetPReconnectNotifications
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ReconnectStatus.NotReconnecting
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ReconnectStatus.Reconnecting
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ReconnectStatus.ReconnectingFailed
import com.duckduckgo.networkprotection.impl.waitlist.NetPRemoteFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnConnectivityLossListenerPlugin::class,
)
@SingleInstanceIn(VpnScope::class)
class NetpVpnConnectivityLossListenerPlugin @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val repository: NetworkProtectionRepository,
    private val reconnectNotifications: NetPReconnectNotifications,
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val netpPixels: Lazy<NetworkProtectionPixels>,
    private val netPRemoteFeature: NetPRemoteFeature,
    @InternalApi private val stopWatchProvider: Provider<StopWatch>,
) : VpnConnectivityLossListenerPlugin {

    private var stopWatch: StopWatch? = null

    override fun onVpnConnectivityLoss(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isActive()) {
                handleConnectivityLoss()
            }
        }
    }

    override fun onVpnConnected(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isActive()) {
                logcat { "onVpnConnected called." }
                if (repository.reconnectStatus == Reconnecting) {
                    repository.reconnectStatus = NotReconnecting
                    successfullyRecovered()
                } else if (repository.reconnectStatus == ReconnectingFailed) {
                    repository.reconnectStatus = NotReconnecting
                    logcat { "Resetting reconnectStatus from ReconnectingFailed to NotReconnecting" }
                }
            }
        }
    }

    private suspend fun handleConnectivityLoss() {
        logcat { "handleConnectivityLoss called." }
        repository.reconnectStatus = Reconnecting

        if (reachedMaxRecoveryAttempts()) {
            giveUpRecovering()
        } else {
            initiateRecovery()
        }
    }

    private fun successfullyRecovered() {
        resetReconnectValues()

        reconnectNotifications.clearNotifications()
        logcat { "Successfully recovered from VPN connectivity loss." }
    }

    private suspend fun initiateRecovery() {
        logcat { "Attempting to recover from vpn connectivity loss." }
        netpPixels.get().reportVpnConnectivityLoss()
        reconnectNotifications.clearNotifications()
        vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
    }

    private suspend fun giveUpRecovering() {
        repository.reconnectStatus = ReconnectingFailed
        resetReconnectValues()

        netpPixels.get().reportVpnReconnectFailed()
        reconnectNotifications.clearNotifications()
        reconnectNotifications.launchReconnectionFailedNotification(context)
        vpnFeaturesRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
    }

    private fun resetReconnectValues() {
        this.stopWatch = null
    }

    private suspend fun isActive(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext netPRemoteFeature.retryOnConnectivityLoss().isEnabled() &&
            vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)
    }

    private fun reachedMaxRecoveryAttempts(): Boolean {
        this.stopWatch?.let { watch ->
            val timeSinceFirstConnectivityLoss = watch.elapsedRealtime()
            logcat { "Time since first connectivity loss is $timeSinceFirstConnectivityLoss" }
            return timeSinceFirstConnectivityLoss > TimeUnit.MINUTES.toMillis(1)
        }

        // init and start the stop watch
        this.stopWatch = stopWatchProvider.get()
        return false
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

/** This class is here just to enable testing, that's why we make it open */
open class StopWatch {
    private val start = SystemClock.elapsedRealtime()

    open fun elapsedRealtime(): Long {
        return SystemClock.elapsedRealtime() - start
    }
}

@ContributesTo(VpnScope::class)
@Module
class StopWatchModule {
    @Provides
    @InternalApi
    fun providesStopWatch(): StopWatch {
        return StopWatch()
    }
}
