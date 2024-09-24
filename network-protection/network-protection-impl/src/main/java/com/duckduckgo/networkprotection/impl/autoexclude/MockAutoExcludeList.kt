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

package com.duckduckgo.networkprotection.impl.autoexclude

internal suspend fun getAutoExcludeList(): List<VpnIncompatibleApp> {
    return listOf(
        VpnIncompatibleApp(
            packageName = "sa.gov.nic.tawakkalna",
        ),
        VpnIncompatibleApp(
            packageName = "com.google.android.projection.gearhead",
        ),
        VpnIncompatibleApp(
            packageName = "app.aawireless",
        ),
        VpnIncompatibleApp(
            packageName = "com.ticketmaster.tickets.internation",
        ),
        VpnIncompatibleApp(
            packageName = "com.disney.disneyplus",
        ),
        VpnIncompatibleApp(
            packageName = "us.current.android",
        ),
        VpnIncompatibleApp(
            packageName = "com.cbs.ca",
        ),
        VpnIncompatibleApp(
            packageName = "com.capitalJ.onJuno",
        ),
        VpnIncompatibleApp(
            packageName = "com.bunq.android",
        ),
        VpnIncompatibleApp(
            packageName = "com.mobile.canaraepassbook",
        ),
        VpnIncompatibleApp(
            packageName = "com.truthsocial.android.app",
        ),
        VpnIncompatibleApp(
            packageName = "com.directoriotigo.hwm",
        ),
        VpnIncompatibleApp(
            packageName = "com.directoriotigo.hwm",
        ),
        VpnIncompatibleApp(
            packageName = "com.ringapp",
        ),
        VpnIncompatibleApp(
            packageName = "com.sonos.acr2",
        ),
        VpnIncompatibleApp(
            packageName = "com.nest.android",
        ),
        VpnIncompatibleApp(
            packageName = "com.immediasemi.android.blink",
        ),
        VpnIncompatibleApp(
            packageName = "com.ivuu",
        ),
        VpnIncompatibleApp(
            packageName = "com.openai.chatgpt",
        ),
    )
}

data class VpnIncompatibleApp(
    val packageName: String,
)
