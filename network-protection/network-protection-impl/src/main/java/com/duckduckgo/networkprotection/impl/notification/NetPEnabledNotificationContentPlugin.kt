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
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.NotificationActions
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.R
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class NetPEnabledNotificationContentPlugin @Inject constructor(
    private val resources: Resources,
    private val networkProtectionState: NetworkProtectionState,
    private val appTrackingProtection: AppTrackingProtection,
    netPIntentProvider: IntentProvider,
) : VpnEnabledNotificationContentPlugin {

    private val onPressIntent by lazy { netPIntentProvider.getOnPressNotificationIntent() }

    override val uuid: String = "d0c6aa7b-16dc-4d35-a4c6-35c6dfcb8309"

    override fun getInitialContent(): VpnEnabledNotificationContent? {
        return if (isActive()) {
            val text = networkProtectionState.serverLocation()?.run {
                HtmlCompat.fromHtml(resources.getString(R.string.netpEnabledNotificationTitle, this), FROM_HTML_MODE_LEGACY)
            } ?: resources.getString(R.string.netpEnabledNotificationInitialTitle)

            /**
             * deleteIntent is set to null here since we decided that we don't need to reshow the notification for NetP on the event that
             * the user dismissed it. This is applicable to Android 14 and up. More info: https://app.asana.com/0/0/1206344475728481/f
             */
            return VpnEnabledNotificationContent(
                title = resources.getString(R.string.netp_name),
                text = SpannableStringBuilder(text),
                onNotificationPressIntent = onPressIntent,
                notificationActions = NotificationActions.VPNActions,
                deleteIntent = null,
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
        return runBlocking { networkProtectionState.isEnabled() && !appTrackingProtection.isEnabled() }
    }

    // This fun interface is provided just for testing purposes
    fun interface IntentProvider {
        fun getOnPressNotificationIntent(): PendingIntent?
    }
}

@ContributesBinding(VpnScope::class)
class NetPEnabledNotificationIntentProvider @Inject constructor(
    private val context: Context,
    private val globalActivityStarter: GlobalActivityStarter,
) : NetPEnabledNotificationContentPlugin.IntentProvider {
    override fun getOnPressNotificationIntent(): PendingIntent? {
        val intent = globalActivityStarter.startIntent(context, NetworkProtectionManagementScreenNoParams)
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
