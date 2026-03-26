/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.feedback

import android.util.Base64
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollector
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.VPN
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

interface FeedbackCustomMetadataProvider {
    suspend fun getCustomMetadata(
        category: SubscriptionFeedbackCategory,
        appPackageId: String? = null,
    ): String

    suspend fun getCustomMetadataEncoded(
        category: SubscriptionFeedbackCategory,
        appPackageId: String? = null,
    ): String
}

@ContributesBinding(ActivityScope::class)
class RealFeedbackCustomMetadataProvider @Inject constructor(
    private val vpnStateCollector: VpnStateCollector,
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptions: Subscriptions,
) : FeedbackCustomMetadataProvider {
    override suspend fun getCustomMetadata(
        category: SubscriptionFeedbackCategory,
        appPackageId: String?,
    ): String {
        return withContext(dispatcherProvider.io()) {
            when (category) {
                VPN -> generateVPNCustomMetadata(appPackageId)
                SUBS_AND_PAYMENTS -> generateSubscriptionCustomMetadata()
                else -> ""
            }
        }
    }

    override suspend fun getCustomMetadataEncoded(
        category: SubscriptionFeedbackCategory,
        appPackageId: String?,
    ): String {
        return getCustomMetadata(category, appPackageId).run {
            if (this.isNotEmpty()) {
                Base64.encodeToString(
                    this.toByteArray(),
                    Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
                )
            } else {
                ""
            }
        }
    }

    private suspend fun generateVPNCustomMetadata(appPackageId: String?): String {
        val state = vpnStateCollector.collectVpnState(appPackageId)
        return state.toString()
    }

    private suspend fun generateSubscriptionCustomMetadata(): String {
        val subsState = JSONObject()
        subsState.put(KEY_CUSTOM_METADATA_SUBS_STATUS, subscriptions.getSubscriptionStatus().isActive().toString())
        return subsState.toString()
    }

    companion object {
        private const val KEY_CUSTOM_METADATA_SUBS_STATUS = "subscriptionActive"
    }
}
