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
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.Mode.FailureRecovery
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
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

/**
 * This class exposes a read-write version of the WG config
 */
interface WgTunnel {
    /**
     * Creates a new wireguard [Config] and returns it
     * If one exists, updates it and returns it but doesn't store it internally.
     *
     * @param keyPair is the private/public key [KeyPair] to be used in the wireguard [Config]
     */
    suspend fun createWgConfig(keyPair: KeyPair? = null): Result<Config>

    /**
     * Creates a new wireguard [Config], returns it and stores it internally.
     * If one exists, updates it and returns it and also stores it internally.
     *
     * @param keyPair is the private/public key [KeyPair] to be used in the wireguard [Config]
     */
    suspend fun createAndSetWgConfig(keyPair: KeyPair? = null): Result<Config>

    /**
     * Marks the currently stored tunnel config as potentially unhealthy
     */
    suspend fun markTunnelUnhealthy()

    /**
     * Marks the currently stored tunnel config as healthy
     */
    suspend fun markTunnelHealthy()
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
     * Clear the current wireguard [Config]
     */
    fun clearWgConfig()

    fun setWgConfig(config: Config)
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

    override fun setWgConfig(config: Config) {
        wgTunnelStore.wireguardConfig = config
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

    private var isTunnelHealthy = true

    override suspend fun createWgConfig(keyPair: KeyPair?): Result<Config> {
        try {
            // return updated existing config or new one
            val config = wgTunnelStore.wireguardConfig?.let outerLet@{ wgConfig ->
                // if new keys are provided and are different from current key, fetch new config
                keyPair?.let { newKeys ->
                    if (wgConfig.`interface`.keyPair != newKeys) {
                        logcat { "Different keys, fetching new config" }
                        return@outerLet fetchNewConfig(keyPair)
                    }
                }

                // if tunnel is marked unhealthy fetch new config
                if (!isTunnelHealthy) {
                    return@outerLet fetchNewConfig(keyPair ?: wgConfig.`interface`.keyPair)
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

            return Result.success(config)
        } catch (e: Throwable) {
            logcat(ERROR) { "Error getting WgTunnelData: ${e.asLog()}" }
            return Result.failure(e)
        }
    }

    override suspend fun createAndSetWgConfig(keyPair: KeyPair?): Result<Config> {
        markTunnelHealthy() // reset tunnel health
        val result = createWgConfig(keyPair)
        if (result.isFailure) {
            return result
        }
        wgTunnelStore.wireguardConfig = result.getOrThrow()
        return result
    }

    override suspend fun markTunnelUnhealthy() {
        isTunnelHealthy = false
    }

    override suspend fun markTunnelHealthy() {
        isTunnelHealthy = true
    }

    private suspend fun fetchNewConfig(keyPair: KeyPair?): Config {
        @Suppress("NAME_SHADOWING")
        val keyPair = keyPair ?: KeyPair()
        val publicKey = keyPair.publicKey.toBase64()
        val privateKey = keyPair.privateKey.toBase64()

        // throw on error
        val mode = if (!isTunnelHealthy) {
            FailureRecovery(currentServer = wgTunnelStore.wireguardConfig?.asServerDetails()?.serverName ?: "*")
        } else {
            null
        }

        val serverData = kotlin.runCatching {
            wgServerApi.registerPublicKey(publicKey, mode = mode) ?: throw NullPointerException("serverData = null")
        }.onFailure {
            logcat(ERROR) { "Error registering public key" }
        }.getOrThrow()

        return Config.Builder()
            .setInterface(
                Interface.Builder()
                    .parsePrivateKey(privateKey)
                    .addAddress(InetNetwork.parse(serverData.address))
                    .apply {
                        addDnsServer(InetAddress.getByName(serverData.gateway))
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
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

class WgTunnelStore constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val prefs: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = false)
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
    fun provideWgTunnelStore(preferencesProvider: SharedPreferencesProvider): WgTunnelStore {
        return WgTunnelStore(preferencesProvider)
    }
}
