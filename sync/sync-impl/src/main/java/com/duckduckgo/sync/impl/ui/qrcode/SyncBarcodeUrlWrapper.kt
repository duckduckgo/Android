/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.qrcode

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncDeviceIds
import com.duckduckgo.sync.impl.applyUrlSafetyFromB64
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat

interface SyncBarcodeUrlWrapper {

    /**
     * Will accept a sync code and format it so it's wrapped in a URL.
     *
     * @param originalCodeB64Encoded the original base64-encoded code to be modified.
     */
    fun wrapCodeInUrl(originalCodeB64Encoded: String): String
}

@ContributesBinding(AppScope::class)
class SyncBarcodeUrlUrlWrapper @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
) : SyncBarcodeUrlWrapper {

    override fun wrapCodeInUrl(originalCodeB64Encoded: String): String {
        return originalCodeB64Encoded.wrapInUrl().also {
            logcat(VERBOSE) { "Sync: code to include in the barcode is $it" }
        }
    }

    private fun String.wrapInUrl(): String {
        return kotlin.runCatching {
            val urlSafeCode = this.applyUrlSafetyFromB64()
            SyncBarcodeUrl(webSafeB64EncodedCode = urlSafeCode, deviceName = getDeviceName()).asUrl()
        }.getOrElse {
            logcat(WARN) { "Sync-url: Failed to encode string for use inside a URL; returning original code" }
            this
        }
    }

    private fun getDeviceName(): String {
        return syncDeviceIds.deviceName()
    }
}
