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

package com.duckduckgo.privacypass.impl

import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import logcat.LogPriority.ERROR
import logcat.logcat

private const val ACT_ORG = "duckduckgo"
private const val ACT_SERVICE = "privacy-pass"
private const val ACT_DEPLOYMENT = "prototype"
private const val ACT_VERSION = "2026-03"

class ActCoreException(message: String) : Exception(message)

data class IssuanceResult(
    val tokenCbor: ByteArray,
    val publicKeyCbor: ByteArray,
)

data class SpendResult(
    val spendProofCbor: ByteArray,
    val preRefundPtr: Pointer,
)

class ActCoreWrapper {

    private val lib: ActCoreLibrary by lazy { ActCoreLibrary.INSTANCE }

    fun performIssuance(
        publicKeyCbor: ByteArray,
        issuerPostRequest: (requestCborBase64: String) -> String?,
    ): IssuanceResult {
        val pk = lib.act_public_key_from_cbor(publicKeyCbor, NativeLong(publicKeyCbor.size.toLong()))
            ?: throw ActCoreException("act_public_key_from_cbor returned null")
        try {
            val pre = lib.act_pre_issuance_new()
                ?: throw ActCoreException("act_pre_issuance_new returned null")
            try {
                val params = createParams()
                try {
                    val requestBuf = lib.act_issuance_request(pre, params)
                    val requestCbor = requestBuf.toByteArray()
                        ?: run {
                            lib.act_buffer_free(requestBuf)
                            throw ActCoreException("act_issuance_request returned empty buffer")
                        }
                    lib.act_buffer_free(requestBuf)

                    val requestBase64 = android.util.Base64.encodeToString(requestCbor, android.util.Base64.NO_WRAP)
                    val responseCborBase64 = issuerPostRequest(requestBase64)
                        ?: throw ActCoreException("Issuance POST request failed")
                    val responseCbor = android.util.Base64.decode(responseCborBase64, android.util.Base64.DEFAULT)

                    val token = lib.act_complete_issuance(
                        pre, params, pk,
                        requestCbor, NativeLong(requestCbor.size.toLong()),
                        responseCbor, NativeLong(responseCbor.size.toLong()),
                    ) ?: throw ActCoreException("act_complete_issuance returned null")

                    try {
                        val tokenBuf = lib.act_credit_token_to_cbor(token)
                        val tokenCbor = tokenBuf.toByteArray()
                            ?: run {
                                lib.act_buffer_free(tokenBuf)
                                throw ActCoreException("act_credit_token_to_cbor returned empty buffer")
                            }
                        lib.act_buffer_free(tokenBuf)
                        return IssuanceResult(tokenCbor = tokenCbor, publicKeyCbor = publicKeyCbor)
                    } finally {
                        lib.act_credit_token_free(token)
                    }
                } finally {
                    lib.act_params_free(params)
                }
            } finally {
                lib.act_pre_issuance_free(pre)
            }
        } finally {
            lib.act_public_key_free(pk)
        }
    }

    fun spend(tokenCbor: ByteArray): SpendResult {
        val token = lib.act_credit_token_from_cbor(tokenCbor, NativeLong(tokenCbor.size.toLong()))
            ?: throw ActCoreException("act_credit_token_from_cbor returned null")
        try {
            val params = createParams()
            try {
                val spendResult = lib.act_spend(token, params, 1L)
                val spendProof = spendResult.getSpendProofBytes()
                    ?: run {
                        spendResult.freeSpendProofBuffer(lib)
                        spendResult.pre_refund?.let { lib.act_pre_refund_free(it) }
                        throw ActCoreException("act_spend returned empty spend proof")
                    }
                spendResult.freeSpendProofBuffer(lib)

                val preRefund = spendResult.pre_refund
                    ?: throw ActCoreException("act_spend returned null pre_refund")

                return SpendResult(spendProofCbor = spendProof, preRefundPtr = preRefund)
            } finally {
                lib.act_params_free(params)
            }
        } finally {
            lib.act_credit_token_free(token)
        }
    }

    fun completeRefund(
        preRefundPtr: Pointer,
        spendProofCbor: ByteArray,
        refundCbor: ByteArray,
        publicKeyCbor: ByteArray,
    ): ByteArray {
        val params = createParams()
        try {
            val pk = lib.act_public_key_from_cbor(publicKeyCbor, NativeLong(publicKeyCbor.size.toLong()))
                ?: throw ActCoreException("act_public_key_from_cbor returned null (refund)")
            try {
                val newToken = lib.act_complete_refund(
                    preRefundPtr, params,
                    spendProofCbor, NativeLong(spendProofCbor.size.toLong()),
                    refundCbor, NativeLong(refundCbor.size.toLong()),
                    pk,
                ) ?: throw ActCoreException("act_complete_refund returned null")

                try {
                    val buf = lib.act_credit_token_to_cbor(newToken)
                    val result = buf.toByteArray()
                        ?: run {
                            lib.act_buffer_free(buf)
                            throw ActCoreException("act_credit_token_to_cbor returned empty buffer (refund)")
                        }
                    lib.act_buffer_free(buf)
                    return result
                } finally {
                    lib.act_credit_token_free(newToken)
                }
            } finally {
                lib.act_public_key_free(pk)
            }
        } finally {
            lib.act_params_free(params)
        }
    }

    fun freePreRefund(ptr: Pointer) {
        try {
            lib.act_pre_refund_free(ptr)
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: failed to free pre_refund: ${e.message}" }
        }
    }

    private fun createParams(): Pointer {
        return lib.act_params_new(ACT_ORG, ACT_SERVICE, ACT_DEPLOYMENT, ACT_VERSION)
            ?: throw ActCoreException("act_params_new returned null")
    }
}
