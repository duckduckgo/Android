/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global

import java.math.BigInteger
import java.security.MessageDigest

val ByteArray.sha256: String
    get() = sha("SHA-256", this)

fun ByteArray.verifySha256(sha256: String): Boolean {
    return this.sha256 == sha256
}

val String.sha256: String
    get() = sha("SHA-256", this.toByteArray())

val String.sha1: String
    get() = sha("SHA-1", this.toByteArray())

private fun sha(algorithm: String, bytes: ByteArray): String {
    val md = MessageDigest.getInstance(algorithm)
    val digest = md.digest(bytes)
    return String.format("%0" + digest.size * 2 + "x", BigInteger(1, digest))
}

fun String.verifySha1(sha1: String): Boolean {
    return this.sha1 == sha1
}
