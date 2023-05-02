/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.notification

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.res.Resources
import android.text.SpannableStringBuilder
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementActivity
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class NetPEnabledNotificationContentPlugin @Inject constructor(
    private val resources: Resources,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    netPIntentProvider: IntentProvider,
) : VpnEnabledNotificationContentPlugin {

    private val onPressIntent by lazy { netPIntentProvider.getOnPressNotificationIntent() }
    override fun getInitialContent(): VpnEnabledNotificationContent? {
        return if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
            return VpnEnabledNotificationContent(
                title = SpannableStringBuilder(resources.getString(R.string.netpEnabledNotificationTitle)),
                message = SpannableStringBuilder(),
                onNotificationPressIntent = onPressIntent,
                notificationAction = null,
            )
        } else {
            null
        }
    }

    override fun getUpdatedContent(): Flow<VpnEnabledNotificationContent?> {
        return flowOf(getInitialContent())
    }

    override fun getPriority(): VpnEnabledNotificationPriority {
        // higher than AppTP
        return VpnEnabledNotificationPriority.HIGH
    }

    override fun isActive(): Boolean {
        return vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)
    }

    // This fun interface is provided just for testing purposes
    fun interface IntentProvider {
        fun getOnPressNotificationIntent(): PendingIntent?
    }
}

@ContributesBinding(VpnScope::class)
class NetPEnabledNotificationIntentProvider @Inject constructor(
    private val context: Context,
) : NetPEnabledNotificationContentPlugin.IntentProvider {
    override fun getOnPressNotificationIntent(): PendingIntent? {
        val intent = NetworkProtectionManagementActivity.intent(context)
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
