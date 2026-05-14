/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.domain

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import javax.inject.Inject

sealed interface ScriptletValidationResult {
    data object Valid : ScriptletValidationResult
    sealed interface Invalid : ScriptletValidationResult {
        data object Encoding : Invalid
        data object SignatureFormat : Invalid
        data object SignatureVerificationFailed : Invalid
    }
}

interface ScriptletSignatureValidator {
    suspend fun validate(content: ByteArray, signatureBase64: String): ScriptletValidationResult
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealScriptletSignatureValidator @Inject constructor(
    private val publicKey: PublicKey,
    private val signature: Signature,
    private val charsetDecoder: CharsetDecoder,
    private val base64Decoder: Base64.Decoder,
) : ScriptletSignatureValidator {

    private val mutex = Mutex()

    override suspend fun validate(content: ByteArray, signatureBase64: String): ScriptletValidationResult = mutex.withLock {
        try {
            charsetDecoder.decode(ByteBuffer.wrap(content))
        } catch (e: CharacterCodingException) {
            logcat(WARN) { "Validating scriptlet failed with invalid encoding: ${e.message}" }
            return@withLock ScriptletValidationResult.Invalid.Encoding
        }
        val signatureBytes = try {
            base64Decoder.decode(signatureBase64)
        } catch (e: IllegalArgumentException) {
            logcat(WARN) { "Validating scriptlet failed with invalid signature format: ${e.message}" }
            return@withLock ScriptletValidationResult.Invalid.SignatureFormat
        }
        try {
            val verified = signature.run {
                initVerify(publicKey)
                update(content)
                verify(signatureBytes)
            }
            if (verified) {
                logcat { "Validating scriptlet succeeded" }
                ScriptletValidationResult.Valid
            } else {
                logcat(WARN) { "Validating scriptlet failed with verification failed" }
                ScriptletValidationResult.Invalid.SignatureVerificationFailed
            }
        } catch (t: Throwable) {
            logcat(WARN) { "ScriptletSignatureValidator: verification threw: ${t.asLog()}" }
            ScriptletValidationResult.Invalid.SignatureVerificationFailed
        }
    }
}
