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

package com.duckduckgo.networkprotection.impl.configuration

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import dagger.Module
import dagger.Provides
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Qualifier
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

/**
 * This class exposes a read-write version of the WG config
 */
interface WgTunnel {
    /**
     * Creates a new wireguard [Config] if it doesn't exists
     * If it exists, updates it and returns it.
     *
     * @param keyPair is the private/public key [KeyPair] to be used in the wireguard [Config]
     * @param updateConfig internally calls [setWgConfig] when set to `true`. Default value is `true`
     */
    suspend fun newOrUpdateConfig(keyPair: KeyPair? = null, updateConfig: Boolean = true): Result<Config>

    /**
     * Set the wireguard [Config] to the one provided in the parameter
     * @param config wireguard Config created using [newOrUpdateConfig]
     */
    suspend fun setWgConfig(config: Config)

    /**
     * Clear the current wiregiard [Config]
     */
    fun clearWgConfig()

    /**
     * @return returns the  [Config] creation timestamp in milliseconds
     */
    suspend fun getWgConfigCreatedAt(): Long
}

/**
 * This class exposes a read-only version of the WG config
 */
interface WgTunnelConfig {
    /**
     * @return the currently stored [Config] or null
     */
    suspend fun getWgConfig(): Config?

    /**
     * @return returns the  [Config] creation timestamp in milliseconds
     */
    suspend fun getWgConfigCreatedAt(): Long

    /**
     * Clear the current wiregiard [Config]
     */
    fun clearWgConfig()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = WgTunnelConfig::class,
)
class RealWgTunnelConfig @Inject constructor(
    @InternalApi private val wgTunnelStore: WgTunnelStore,
) : WgTunnelConfig {
    override suspend fun getWgConfig(): Config? {
        return wgTunnelStore.wireguardConfig
    }

    override suspend fun getWgConfigCreatedAt(): Long {
        return wgTunnelStore.lastPrivateKeyUpdateTimeInMillis
    }

    override fun clearWgConfig() {
        wgTunnelStore.wireguardConfig = null
    }
}

@ContributesBinding(
    scope = VpnScope::class,
    boundType = WgTunnel::class,
)
class RealWgTunnel @Inject constructor(
    private val wgServerApi: WgServerApi,
    private val netPDefaultConfigProvider: NetPDefaultConfigProvider,
    @InternalApi private val wgTunnelStore: WgTunnelStore,
) : WgTunnel {

    override suspend fun newOrUpdateConfig(keyPair: KeyPair?, updateConfig: Boolean): Result<Config> {
        try {
            // return updated existing config or new one
            val config = wgTunnelStore.wireguardConfig?.let outerLet@{ wgConfig ->
                keyPair?.let { newKeys ->
                    if (wgConfig.`interface`.keyPair != newKeys) {
                        logcat { "Different keys, fetching new config" }
                        return@outerLet fetchNewConfig(keyPair)
                    }
                }

                logcat { "Updating existing WG config" }

                val newConfigBuilder = wgConfig.builder
                val oldInterfaceBuilder = wgConfig.`interface`.builder

                // update exclusion list and routes
                wgConfig.builder.apply {
                    setInterface(
                        oldInterfaceBuilder.apply {
                            // update excluded applications
                            includedApplications.clear()
                            excludedApplications.clear()
                            excludeApplications(netPDefaultConfigProvider.exclusionList())

                            routes.clear()
                            addRoutes(
                                netPDefaultConfigProvider.routes().map {
                                    InetNetwork.parse("${it.key}/${it.value}")
                                },
                            )
                        }.build(),
                    )
                }.build()

                newConfigBuilder.build()
            } ?: fetchNewConfig(keyPair)

            // update config
            if (updateConfig) {
                setWgConfig(config)
            }

            return Result.success(config)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Error getting WgTunnelData: ${e.asLog()}" }
            return Result.failure(e)
        }
    }

    override suspend fun setWgConfig(config: Config) {
        wgTunnelStore.wireguardConfig = config
    }

    private suspend fun fetchNewConfig(keyPair: KeyPair?): Config {
        @Suppress("NAME_SHADOWING")
        val keyPair = keyPair ?: KeyPair()
        val publicKey = keyPair.publicKey.toBase64()
        val privateKey = keyPair.privateKey.toBase64()

        // throw on error
        val serverData = wgServerApi.registerPublicKey(publicKey) ?: throw NullPointerException("serverData = null")

        return Config.Builder()
            .setInterface(
                Interface.Builder()
                    .parsePrivateKey(privateKey)
                    .addAddress(InetNetwork.parse(serverData.address))
                    .apply {
                        addDnsServer(InetAddress.getByName(serverData.gateway))
                        addDnsServers(netPDefaultConfigProvider.fallbackDns())
                    }
                    .excludeApplications(netPDefaultConfigProvider.exclusionList())
                    .setMtu(netPDefaultConfigProvider.mtu())
                    .build(),
            )
            .addPeer(
                Peer.Builder()
                    .parsePublicKey(serverData.publicKey)
                    // peer is a relay server that bounces all internet & VPN traffic (like a proxy), including IPv6
                    .parseAllowedIPs("0.0.0.0/0,::/0")
                    .parseEndpoint(serverData.publicEndpoint)
                    .setName(serverData.serverName)
                    .setLocation(serverData.location.orEmpty())
                    .build(),
            )
            .build()
    }

    override fun clearWgConfig() {
        wgTunnelStore.wireguardConfig = null
    }

    override suspend fun getWgConfigCreatedAt(): Long {
        return wgTunnelStore.lastPrivateKeyUpdateTimeInMillis
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

class WgTunnelStore constructor(
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
) {
    private val prefs: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = false)
    }

    var wireguardConfig: Config?
        get() {
            val wgQuickString = prefs.getString(KEY_WG_CONFIG, null)
            return wgQuickString?.let {
                Config.parse(BufferedReader(StringReader(it)))
            }
        }
        set(value) {
            val oldConfig = prefs.getString(KEY_WG_CONFIG, null)?.let {
                Config.parse(BufferedReader(StringReader(it)))
            }
            // nothing to update
            if (oldConfig == value) return

            prefs.edit(commit = true) { putString(KEY_WG_CONFIG, value?.toWgQuickString()) }
            if (value == null) {
                lastPrivateKeyUpdateTimeInMillis = -1
            } else if (oldConfig?.`interface`?.keyPair != value.`interface`.keyPair) {
                lastPrivateKeyUpdateTimeInMillis = System.currentTimeMillis()
            }
        }

    var lastPrivateKeyUpdateTimeInMillis: Long
        get() = prefs.getLong(KEY_WG_PRIVATE_KEY_LAST_UPDATE, -1L)
        set(value) {
            prefs.edit(commit = true) { putLong(KEY_WG_PRIVATE_KEY_LAST_UPDATE, value) }
        }
    companion object {
        private const val FILENAME = "com.duckduckgo.vpn.tunnel.config.v1"
        private const val KEY_WG_CONFIG = "wg_config_key"
        private const val KEY_WG_PRIVATE_KEY_LAST_UPDATE = "wg_private_key_last_update"
    }
}

@Module
@ContributesTo(AppScope::class)
object WgTunnelStoreModule {
    @Provides
    @InternalApi
    fun provideWgTunnelStore(preferencesProvider: VpnSharedPreferencesProvider): WgTunnelStore {
        return WgTunnelStore(preferencesProvider)
    }
}
