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

package com.duckduckgo.sync.internal.connect

import android.util.Base64
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.API_CODE.GONE
import com.duckduckgo.sync.impl.API_CODE.NOT_FOUND
import com.duckduckgo.sync.impl.ConnectCode
import com.duckduckgo.sync.impl.LinkCode
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncDeviceIds
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrlWrapper
import com.duckduckgo.sync.store.SyncStore
import com.squareup.moshi.Moshi
import javax.inject.Inject

/**
 * Shows the v1 connect code and waits for another device to scan it. Production devices only show the v2 code, so we keep this to test pairing
 * with older clients that only understand v1 codes.
 */
class V1ConnectCode @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncLib,
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
    private val syncFeature: SyncFeature,
    private val syncCodeUrlWrapper: SyncBarcodeUrlWrapper,
    private val syncAccountRepository: SyncAccountRepository,
) {

    private val linkCodeAdapter = Moshi.Builder().build().adapter(LinkCode::class.java)

    fun getConnectQR(): Result<AuthCode> {
        if (syncStore.isSignedIn()) return Error(reason = "Connect: already signed in")

        val prepareForConnect = kotlin.runCatching { nativeLib.prepareForConnect() }
            .getOrElse { return Error(reason = "Creating ConnectQR code failed: ${it.message}") }
            .takeIf { it.result == 0 } ?: return Error(reason = "Creating ConnectQR code failed")

        val deviceId = syncDeviceIds.deviceId()
        syncStore.deviceId = deviceId
        syncStore.primaryKey = prepareForConnect.publicKey
        syncStore.secretKey = prepareForConnect.secretKey

        val linkingQRCode = linkCodeAdapter.toJson(
            LinkCode(connect = ConnectCode(deviceId = deviceId, secretKey = prepareForConnect.publicKey)),
        ) ?: return Error(reason = "Error generating Linking Code")

        val b64Encoded = Base64.encodeToString(linkingQRCode.toByteArray(), Base64.NO_WRAP)
        val qrCode = if (syncFeature.syncSetupBarcodeIsUrlBased().isEnabled()) {
            syncCodeUrlWrapper.wrapCodeInUrl(b64Encoded)
        } else {
            b64Encoded
        }
        return Success(AuthCode(qrCode = qrCode, rawCode = b64Encoded))
    }

    fun pollConnectionKeys(): Result<Boolean> {
        val deviceId = syncDeviceIds.deviceId()

        return when (val result = syncApi.connectDevice(deviceId)) {
            is Error -> when (result.code) {
                NOT_FOUND.code -> Success(false)
                GONE.code -> Error(code = result.code, reason = "Connect: keys expired")
                else -> result
            }

            is Success -> {
                val sealOpen = kotlin.runCatching {
                    nativeLib.sealOpen(result.data, syncStore.primaryKey!!, syncStore.secretKey!!)
                }.getOrElse { return Error(reason = "Connect: Error opening seal") }
                val recoveryCode = kotlin.runCatching {
                    linkCodeAdapter.fromJson(sealOpen)?.recovery
                }.getOrNull() ?: return Error(reason = "Connect: Error reading received recovery code")
                syncAccountRepository.processCode(SyncAuthCode.Recovery(recoveryCode))
            }
        }
    }
}
