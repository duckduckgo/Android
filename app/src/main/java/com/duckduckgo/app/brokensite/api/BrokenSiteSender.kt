/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.brokensite.api

import android.os.Build
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.trackerdetection.db.TdsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface BrokenSiteSender {
    fun submitBrokenSiteFeedback(brokenSite: BrokenSite)
}

class BrokenSiteSubmitter(
    private val statisticsStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val tdsDao: TdsDao
) : BrokenSiteSender {

    override fun submitBrokenSiteFeedback(brokenSite: BrokenSite) {

        GlobalScope.launch(Dispatchers.IO) {
            val params = mapOf(
                "category" to brokenSite.category,
                "siteUrl" to brokenSite.siteUrl,
                "upgradedHttps" to brokenSite.upgradeHttps,
                "tds" to tdsDao.eTag(),
                "blockedTrackers" to brokenSite.blockedTrackers,
                "surrogates" to "",
                "extensionVersion" to "",
                "appVersion" to BuildConfig.VERSION_NAME,
                "atb" to atbWithVariant(),
                "os" to Build.VERSION.SDK_INT,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "wvVersion" to brokenSite.webViewVersion,
                "siteType" to brokenSite.siteType
            )

//            runCatching {
//                service.submitBrokenSite(
//                    url = url,
//                    comment = comment,
//                    api = Build.VERSION.SDK_INT,
//                    manufacturer = Build.MANUFACTURER,
//                    model = Build.MODEL,
//                    version = BuildConfig.VERSION_NAME,
//                    atb = atbWithVariant()
//                ).execute()
//            }
//                .onSuccess { Timber.v("Feedback submission succeeded") }
//                .onFailure { Timber.w(it, "Feedback submission failed") }
        }
    }

    private fun atbWithVariant(): String {
        return statisticsStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""
    }
}