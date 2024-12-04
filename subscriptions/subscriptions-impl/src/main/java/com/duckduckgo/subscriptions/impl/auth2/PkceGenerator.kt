/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.auth2

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface PkceGenerator {
    fun generateCodeVerifier(): String
    fun generateCodeChallenge(codeVerifier: String): String
}

@ContributesBinding(AppScope::class)
class PkceGeneratorImpl @Inject constructor() : PkceGenerator {

    override fun generateCodeVerifier(): String {
        val code = ByteArray(32)
            .apply { SecureRandom().nextBytes(this) }

        return code.encodeBase64()
    }

    override fun generateCodeChallenge(codeVerifier: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
            .encodeBase64()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun ByteArray.encodeBase64(): String {
        return Base64.UrlSafe.encode(this).trimEnd('=')
    }
}
