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

package com.duckduckgo.sync.impl.setup

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.setup.SyncUrlIdentifier
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealSyncUrlIdentifier @Inject constructor(
    private val syncFeature: SyncFeature,
) : SyncUrlIdentifier {

    override fun shouldDelegateToSyncSetup(intentText: String?): Boolean {
        if (intentText == null) return false
        if (syncFeature.canInterceptSyncSetupUrls().isEnabled().not()) return false

        return intentText.isValidSyncUrl()
    }

    private fun String.isValidSyncUrl(): Boolean {
        return SyncBarcodeUrl.parseUrl(this) != null
    }
}
