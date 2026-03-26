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

import android.content.Context
import com.duckduckgo.library.loader.LibraryLoader

class ActCoreNative {
    companion object {
        @Volatile private var initialized = false

        fun init(context: Context) {
            if (!initialized) {
                synchronized(this) {
                    if (!initialized) {
                        LibraryLoader.loadLibrary(context, "act_core")
                        initialized = true
                    }
                }
            }
        }

        fun isReady(): Boolean = initialized
    }

    external fun createParams(org: String, service: String, deployment: String, version: String): String
    external fun parsePublicKey(cborBase64: String): String
    external fun createIssuanceRequest(paramsId: Long): String
    external fun completeIssuance(
        preIssuanceId: Long,
        paramsId: Long,
        publicKeyId: Long,
        requestCborBase64: String,
        responseCborBase64: String,
    ): String
    external fun loadCreditToken(tokenCborBase64: String): String
    external fun spend(creditTokenId: Long, paramsId: Long, charge: Long): String
    external fun completeRefund(
        preRefundId: Long,
        paramsId: Long,
        publicKeyId: Long,
        spendProofCborBase64: String,
        refundCborBase64: String,
    ): String
}
