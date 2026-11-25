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

package com.duckduckgo.request.interception.impl

import android.net.Uri
import android.webkit.WebResourceRequest
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class HttpsUpgradeStep @Inject constructor(
    private val httpsUpgrader: HttpsUpgrader,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) {
    fun upgradeOrNull(request: WebResourceRequest): Uri? {
        if (!request.isForMainFrame) return null
        val url = request.url
        return if (url != null && httpsUpgrader.shouldUpgrade(url)) {
            httpsUpgrader.upgrade(url)
        } else {
            null
        }
    }

    fun recordUpgrade() {
        appCoroutineScope.launch(dispatchers.io()) { privacyProtectionCountDao.incrementUpgradeCount() }
    }
}
