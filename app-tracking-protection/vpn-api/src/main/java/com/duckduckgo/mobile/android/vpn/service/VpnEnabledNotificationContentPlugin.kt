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

package com.duckduckgo.mobile.android.vpn.service

import android.app.PendingIntent
import android.text.SpannableStringBuilder
import kotlinx.coroutines.flow.Flow

interface VpnEnabledNotificationContentPlugin {

    /**
     * This method will be called to show the first notification when the VPN is enabled.
     * The method will be called from the main thread.
     *
     * @return shall return the content of the notification or null if the plugin does not want to show content in the notification.
     */
    fun getInitialContent(): VpnEnabledNotificationContent?

    /**
     * The VPN will subscribe to this flow, after it's being enabled, to get notification content updates.
     * The method will NOT be called from the main thread.
     *
     * @return shall return a flow of notification content updates, or null if the plugin does not want to show content in the notification.
     */
    fun getUpdatedContent(): Flow<VpnEnabledNotificationContent?>

    /**
     * This method will be called when the user clicks on the notification.
     *
     * @return shall return the intent to be launched when the user clicks on the notification or null if the plugin does not want to handle the click.
     */
    fun getOnPressNotificationIntent(): PendingIntent?

    /**
     * The VPN will call this method to select what plugin will be displayed in the notification.
     * To select a proper priority:
     * - check the priority of any other plugins
     * - check with product/design what should be the priority of your plugin w.r.t. other plugins
     *
     * @return shall return the priority of the plugin.
     */
    fun getPriority(): VpnEnabledNotificationPriority

    data class VpnEnabledNotificationContent(
        val title: SpannableStringBuilder,
        val message: SpannableStringBuilder,
    )
    enum class VpnEnabledNotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        VERY_HIGH,
    }
}
