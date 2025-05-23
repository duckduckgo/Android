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
import logcat.LogPriority.VERBOSE
import logcat.logcat

/**
 * Constants for PrivateDnsMode
 *
 * "off" return when private DNS is off
 * "opportunistic" return when private DNS is set to "automatic" aka opportunistic
 * "hostname" return when private DNS is set to strict mode, aka user set a DNS
 */
private const val PRIVATE_DNS_MODE_OFF = "off"
private const val PRIVATE_DNS_MODE_OPPORTUNISTIC = "opportunistic"
private const val PRIVATE_DNS_MODE_STRICT = "hostname"

fun Context.isPrivateDnsActive(): Boolean {
    var dnsMode = Settings.Global.getString(contentResolver, "private_dns_mode")
    if (dnsMode == null) dnsMode = PRIVATE_DNS_MODE_OFF
    return PRIVATE_DNS_MODE_OFF != dnsMode
}

fun Context.isPrivateDnsAutomatic(): Boolean {
    var dnsMode = Settings.Global.getString(contentResolver, "private_dns_mode")
    return dnsMode == PRIVATE_DNS_MODE_OPPORTUNISTIC
}

fun Context.isPrivateDnsStrict(): Boolean {
    var dnsMode = Settings.Global.getString(contentResolver, "private_dns_mode")
    return dnsMode == PRIVATE_DNS_MODE_STRICT
}

fun Context.getPrivateDnsServerName(): String? {
    val dnsMode = Settings.Global.getString(contentResolver, "private_dns_mode")
    return if (PRIVATE_DNS_MODE_STRICT == dnsMode) Settings.Global.getString(contentResolver, "private_dns_specifier") else null
}

fun Context.isAirplaneModeOn(): Boolean {
    val airplaneMode = Settings.Global.getString(contentResolver, "airplane_mode_on")
    logcat(VERBOSE) { "airplane_mode_on $airplaneMode" }
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
    kotlin.runCatching {
        ContextCompat.registerReceiver(this, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}

fun Context.registerExportedReceiver(
    receiver: BroadcastReceiver,
    intentFilter: IntentFilter,
) {
    ContextCompat.registerReceiver(this, receiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
}

fun Context.isDdgApp(packageName: String): Boolean = packageName == this.applicationContext.packageName || packageName.startsWith("com.duckduckgo")
