/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.crypto

/**
 * Represents a Curve25519 key pair as used by WireGuard.
 * <p>
 * Instances of this class are immutable.
 * Creates a key pair using:
 *  - a newly-generated private key OR
 *  - using an existing private key used to derive the public key
 */
class KeyPair @JvmOverloads constructor(
    /**
     * Returns the private key from the key pair.
     *
     * @return the private key
     */
    val privateKey: Key = Key.generatePrivateKey(),
) {
    /**
     * Returns the public key from the key pair.
     *
     * @return the public key
     */
    val publicKey: Key = Key.generatePublicKey(privateKey)

    override fun equals(obj: Any?): Boolean {
        if (obj === this) return true
        if (obj == null || obj.javaClass != javaClass) return false
        val other = obj as KeyPair
        return other.privateKey == this.privateKey
    }

    override fun hashCode(): Int {
        var result = privateKey.hashCode()
        result = 31 * result + publicKey.hashCode()
        return result
    }
}
