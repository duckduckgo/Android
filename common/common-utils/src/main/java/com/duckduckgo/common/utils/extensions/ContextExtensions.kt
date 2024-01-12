/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.common.utils.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import timber.log.Timber

fun Context.isPrivateDnsActive(): Boolean {
    var dnsMode = Settings.Global.getString(contentResolver, "private_dns_mode")
    if (dnsMode == null) dnsMode = "off"
    return "off" != dnsMode
}

fun Context.getPrivateDnsServerName(): String? {
    val dnsMode = Settings.Global.getString(contentResolver, "private_dns_mode")
    return if ("hostname" == dnsMode) Settings.Global.getString(contentResolver, "private_dns_specifier") else null
}

fun Context.isAirplaneModeOn(): Boolean {
    val airplaneMode = Settings.Global.getString(contentResolver, "airplane_mode_on")
    Timber.v("airplane_mode_on $airplaneMode")
    return airplaneMode == "1"
}

fun Context.isIgnoringBatteryOptimizations(): Boolean {
    return runCatching {
        packageName?.let {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } ?: false
    }.getOrDefault(false)
}

fun Context.registerNotExportedReceiver(
    receiver: BroadcastReceiver,
    intentFilter: IntentFilter,
) {
    ContextCompat.registerReceiver(this, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
}

fun Context.registerExportedReceiver(
    receiver: BroadcastReceiver,
    intentFilter: IntentFilter,
) {
    ContextCompat.registerReceiver(this, receiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
}
