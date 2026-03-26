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

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure

@Structure.FieldOrder("data", "len")
open class ActBuffer : Structure() {
    @JvmField var data: Pointer? = null

    @JvmField var len: NativeLong = NativeLong(0)

    class ByValue : ActBuffer(), Structure.ByValue

    fun toByteArray(): ByteArray? {
        val p = data ?: return null
        val size = len.toLong().toInt()
        if (size <= 0) return null
        return p.getByteArray(0, size)
    }

    fun toByValue(): ByValue {
        val bv = ByValue()
        bv.data = data
        bv.len = len
        return bv
    }
}

@Structure.FieldOrder("spend_proof_cbor_data", "spend_proof_cbor_len", "pre_refund")
open class ActSpendResult : Structure() {
    @JvmField var spend_proof_cbor_data: Pointer? = null

    @JvmField var spend_proof_cbor_len: NativeLong = NativeLong(0)

    @JvmField var pre_refund: Pointer? = null

    class ByValue : ActSpendResult(), Structure.ByValue

    fun getSpendProofBytes(): ByteArray? {
        val p = spend_proof_cbor_data ?: return null
        val size = spend_proof_cbor_len.toLong().toInt()
        if (size <= 0) return null
        return p.getByteArray(0, size)
    }

    fun freeSpendProofBuffer(lib: ActCoreLibrary) {
        val buf = ActBuffer.ByValue()
        buf.data = spend_proof_cbor_data
        buf.len = spend_proof_cbor_len
        lib.act_buffer_free(buf)
    }
}

@Suppress("FunctionName")
interface ActCoreLibrary : Library {
    companion object {
        val INSTANCE: ActCoreLibrary by lazy {
            Native.load("act_core", ActCoreLibrary::class.java)
        }
    }

    fun act_buffer_free(buf: ActBuffer.ByValue)
    fun act_params_free(ptr: Pointer)
    fun act_public_key_free(ptr: Pointer)
    fun act_pre_issuance_free(ptr: Pointer)
    fun act_credit_token_free(ptr: Pointer)
    fun act_pre_refund_free(ptr: Pointer)

    fun act_params_new(
        organization: String,
        service: String,
        deployment_id: String,
        version: String,
    ): Pointer?

    fun act_public_key_from_cbor(data: ByteArray, len: NativeLong): Pointer?

    fun act_pre_issuance_new(): Pointer?

    fun act_issuance_request(pre: Pointer, params: Pointer): ActBuffer.ByValue

    fun act_complete_issuance(
        pre: Pointer,
        params: Pointer,
        pk: Pointer,
        request_cbor: ByteArray,
        request_len: NativeLong,
        response_cbor: ByteArray,
        response_len: NativeLong,
    ): Pointer?

    fun act_spend(token: Pointer, params: Pointer, charge: Long): ActSpendResult.ByValue

    fun act_complete_refund(
        pre_refund: Pointer,
        params: Pointer,
        spend_proof_cbor: ByteArray,
        spend_proof_len: NativeLong,
        refund_cbor: ByteArray,
        refund_len: NativeLong,
        pk: Pointer,
    ): Pointer?

    fun act_credit_token_to_cbor(token: Pointer): ActBuffer.ByValue
    fun act_credit_token_from_cbor(data: ByteArray, len: NativeLong): Pointer?
}
