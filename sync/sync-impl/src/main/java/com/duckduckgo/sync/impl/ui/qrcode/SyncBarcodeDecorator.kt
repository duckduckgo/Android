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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncDeviceIds
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.applyUrlSafetyFromB64
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeDecorator.CodeType
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeDecorator.CodeType.Connect
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeDecorator.CodeType.Exchange
import com.squareup.anvil.annotations.ContributesBinding
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

interface SyncBarcodeDecorator {

    /**
     * Will accept a sync code and potentially modify it depending on feature flagged capabilities.
     * Not all code types can be modified so the type of code must be provided.
     *
     * @param originalCodeB64Encoded the original base64-encoded code to be potentially modified.
     * @param codeType the type of code to be decorated
     */
    suspend fun decorateCode(
        originalCodeB64Encoded: String,
        codeType: CodeType,
    ): String

    sealed interface CodeType {
        data object Connect : CodeType
        data object Exchange : CodeType
        data object Recovery : CodeType
    }
}

@ContributesBinding(AppScope::class)
class SyncBarcodeUrlDecorator @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val syncFeature: SyncFeature,
    private val dispatchers: DispatcherProvider,
) : SyncBarcodeDecorator {

    override suspend fun decorateCode(originalCodeB64Encoded: String, codeType: CodeType): String {
        return withContext(dispatchers.io()) {
            // can only wrap codes in a URL if the feature is enabled
            if (!urlFeatureSupported()) {
                return@withContext originalCodeB64Encoded
            }

            // only `Connect` and `Exchange` codes can be wrapped in a URL
            when (codeType) {
                is Connect -> originalCodeB64Encoded.wrapInUrl()
                is Exchange -> originalCodeB64Encoded.wrapInUrl()
                else -> originalCodeB64Encoded
            }
        }.also {
            Timber.v("Sync: code to include in the barcode is $it")
        }
    }

    private fun urlFeatureSupported(): Boolean {
        return syncFeature.syncSetupBarcodeIsUrlBased().isEnabled()
    }

    private fun String.wrapInUrl(): String {
        return kotlin.runCatching {
            val urlSafeCode = this.applyUrlSafetyFromB64()
            SyncBarcodeUrl(webSafeB64EncodedCode = urlSafeCode, urlEncodedDeviceName = getDeviceName()).asUrl()
        }.getOrElse {
            Timber.w("Sync-url: Failed to encode string for use inside a URL; returning original code")
            this
        }
    }

    private fun getDeviceName(): String {
        val deviceName = syncDeviceIds.deviceName()
        return URLEncoder.encode(deviceName, "UTF-8")
    }
}
