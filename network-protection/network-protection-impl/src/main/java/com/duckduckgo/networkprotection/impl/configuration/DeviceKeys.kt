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

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import javax.inject.Inject

interface DeviceKeys {
    val publicKey: String
    val privateKey: String
}

@ContributesBinding(VpnScope::class)
class RealDeviceKeys @Inject constructor(
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val keyPairGenerator: KeyPairGenerator,
) : DeviceKeys {
    override val publicKey: String
        get() = keyPairGenerator.generatePublicKey(privateKey)
    override val privateKey: String
        get() = if (networkProtectionRepository.privateKey.isNullOrEmpty()) {
            keyPairGenerator.generatePrivateKey().also {
                networkProtectionRepository.privateKey = it
            }
        } else {
            networkProtectionRepository.privateKey!!
        }
}

interface KeyPairGenerator {
    fun generatePrivateKey(): String
    fun generatePublicKey(privateKey: String): String
}

@ContributesBinding(VpnScope::class)
class WgKeyPairGenerator @Inject constructor() : KeyPairGenerator {
    override fun generatePrivateKey(): String = KeyPair().privateKey.toBase64()

    override fun generatePublicKey(privateKey: String): String = KeyPair(Key.fromBase64(privateKey)).publicKey.toBase64()
}
