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

package com.duckduckgo.networkprotection.impl.rekey

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.wireguard.crypto.KeyPair
import dagger.Module
import dagger.Provides
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Qualifier

interface NetPRekeyer {
    suspend fun doRekey()
}

@ContributesBinding(VpnScope::class)
class RealNetPRekeyer @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val networkProtectionPixels: NetworkProtectionPixels,
    @ProcessName private val processName: String,
    private val wgTunnel: WgTunnel,
    private val wgTunnelConfig: WgTunnelConfig,
    private val appBuildConfig: AppBuildConfig,
    @InternalApi private val deviceLockedChecker: DeviceLockedChecker,
) : NetPRekeyer {

    private val forceRekey = AtomicBoolean(false)

    override suspend fun doRekey() {
        fun AtomicBoolean.getAndResetValue(): Boolean {
            val value = getAndSet(false)
            return appBuildConfig.isInternalBuild() && value // only allowed in internal builds
        }

        logcat { "Rekeying client on $processName" }
        val forceOrFalseInProductionBuilds = forceRekey.getAndResetValue()

        val millisSinceLastKeyUpdate = System.currentTimeMillis() - wgTunnelConfig.getWgConfigCreatedAt()
        if (!forceOrFalseInProductionBuilds && millisSinceLastKeyUpdate < TimeUnit.DAYS.toMillis(1)) {
            logcat { "Less than 24h passed, skip re-keying" }
            return
        }

        if (deviceLockedChecker.invoke() || forceOrFalseInProductionBuilds) {
            if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
                val config = wgTunnel.createAndSetWgConfig(KeyPair())
                    .onFailure {
                        logcat(ERROR) { "Failed registering the new key during re-keying: ${it.asLog()}" }
                    }.getOrNull() ?: return

                logcat { "Re-keying with public key: ${config.`interface`.keyPair.publicKey.toBase64()}" }

                logcat { "Restarting VPN after clearing client keys" }
                networkProtectionPixels.reportRekeyCompleted()
                vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
            } else {
                logcat(ERROR) { "Re-key work should not happen" }
            }
        } else {
            logcat { "Device not locked, skip re-keying" }
        }
    }

    suspend fun forceRekey() {
        if (appBuildConfig.isInternalBuild()) {
            forceRekey.set(true)
            doRekey()
        } else {
            logcat(ERROR) { "Force re-key not allowed in production builds" }
        }
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

// visible for testing
internal typealias DeviceLockedChecker = () -> Boolean

@Module
@ContributesTo(VpnScope::class)
object DeviceLockedCheckerModule {

    @Provides
    @InternalApi
    fun provideDeviceLockChecker(context: Context): DeviceLockedChecker {
        val keyguardManager = runCatching { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }.getOrNull()
        val powerManager = runCatching { context.getSystemService(Context.POWER_SERVICE) as PowerManager }.getOrNull()

        return {
            (keyguardManager?.isDeviceLocked == true || powerManager?.isInteractive == false)
        }
    }
}
