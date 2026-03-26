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

package com.duckduckgo.sync.api

/** Public interface to encrypt and decrypt Sync related data*/
interface SyncCrypto {

    /**
     * Encrypts a blob of text
     * @param text to encrypt
     * @return text encrypted (Base64 encoded)
     */
    fun encrypt(text: String): String

    /**
     * Decrypts a blob of text
     * @param data text to decrypt
     * @return text decrypted
     */
    fun decrypt(data: String): String

    /**
     * Encrypts a byte array
     * @param data raw bytes to encrypt
     * @return encrypted byte array
     */
    fun encrypt(data: ByteArray): ByteArray

    /**
     * Decrypts a byte array
     * @param data encrypted byte array
     * @return decrypted byte array
     */
    fun decrypt(data: ByteArray): ByteArray
}
