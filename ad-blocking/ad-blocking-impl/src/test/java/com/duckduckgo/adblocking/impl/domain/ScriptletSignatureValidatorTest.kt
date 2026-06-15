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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class ScriptletSignatureValidatorTest {

    private lateinit var keyPair: KeyPair
    private lateinit var validator: ScriptletSignatureValidator

    @Before
    fun setup() {
        keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        validator = RealScriptletSignatureValidator(
            publicKeyProvider = object : PublicKeyProvider {
                override val publicKey: PublicKey = keyPair.public
            },
        )
    }

    @Test
    fun whenContentAndSignatureMatchThenValidateReturnsValid() {
        val content = "console.log('hello')".toByteArray()
        val signature = sign(content, keyPair.private)

        Assert.assertEquals(ScriptletValidationResult.Valid, validator.validate(content, signature))
    }

    @Test
    fun whenContentIsTamperedThenValidateReturnsSignatureVerificationFailed() {
        val original = "original content".toByteArray()
        val signature = sign(original, keyPair.private)
        val tampered = "tampered content".toByteArray()

        Assert.assertEquals(
            ScriptletValidationResult.Invalid.SignatureVerificationFailed,
            validator.validate(tampered, signature),
        )
    }

    @Test
    fun whenSignatureIsFromDifferentKeyThenValidateReturnsSignatureVerificationFailed() {
        val content = "content".toByteArray()
        val otherKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val signature = sign(content, otherKeyPair.private)

        Assert.assertEquals(
            ScriptletValidationResult.Invalid.SignatureVerificationFailed,
            validator.validate(content, signature),
        )
    }

    @Test
    fun whenSignatureIsMalformedBase64ThenValidateReturnsInvalidSignatureFormat() {
        val content = "content".toByteArray()

        Assert.assertEquals(
            ScriptletValidationResult.Invalid.SignatureFormat,
            validator.validate(content, "not!valid!base64"),
        )
    }

    @Test
    fun whenSignatureBytesAreNotValidEcdsaSignatureThenValidateReturnsSignatureVerificationFailed() {
        val content = "content".toByteArray()
        val garbage = Base64.getEncoder().encodeToString(ByteArray(64) { 0x42 })

        Assert.assertEquals(
            ScriptletValidationResult.Invalid.SignatureVerificationFailed,
            validator.validate(content, garbage),
        )
    }

    @Test
    fun whenContentIsNotValidUtf8ThenValidateReturnsInvalidEncoding() {
        val invalidUtf8 = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x80.toByte(), 0x81.toByte())
        val signature = sign(invalidUtf8, keyPair.private)

        Assert.assertEquals(
            ScriptletValidationResult.Invalid.Encoding,
            validator.validate(invalidUtf8, signature),
        )
    }

    private fun sign(content: ByteArray, privateKey: PrivateKey): String {
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(content)
        }
        return Base64.getEncoder().encodeToString(sig.sign())
    }
}
