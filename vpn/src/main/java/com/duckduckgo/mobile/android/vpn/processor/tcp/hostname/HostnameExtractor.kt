/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

import timber.log.Timber
import xyz.hexene.localvpn.TCB
import java.nio.charset.StandardCharsets

interface HostnameExtractor {

    fun extract(tcb: TCB, payloadBytes: ByteArray): String?

}

class AndroidHostnameExtractor(
    private val hostnameHeaderExtractor: HostnameHeaderExtractor,
    private val encryptedRequestHostExtractor: EncryptedRequestHostExtractor
) : HostnameExtractor {

    override fun extract(tcb: TCB, payloadBytes: ByteArray): String? {
        if (tcb.hostName != null) return tcb.hostName
        determineHost(tcb, payloadBytes)
        Timber.i("Host is %s for %s", tcb.hostName, tcb.ipAndPort)
        return tcb.hostName
    }

    private fun determineHost(tcb: TCB, payloadBytes: ByteArray) {
        var host = hostnameHeaderExtractor.extract(String(payloadBytes, StandardCharsets.US_ASCII))
        if (host != null) {
            Timber.v("Found domain from plaintext headers: %s", host)
            tcb.hostName = host
            return
        }

        host = encryptedRequestHostExtractor.extract(payloadBytes)
        if (host != null) {
            Timber.v("Found domain from encrypted headers: %s", host)
            tcb.hostName = host
            return
        }

        Timber.w("Failed to extract host")
    }

}
