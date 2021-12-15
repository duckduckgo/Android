/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.device

import android.content.Context
import android.telephony.TelephonyManager
import android.util.TypedValue
import java.util.*

interface DeviceInfo {

    enum class FormFactor(val description: String) {
        PHONE("phone"),
        TABLET("tablet")
    }

    val appVersion: String

    val majorAppVersion: String

    val language: String

    val country: String

    fun formFactor(): FormFactor
}

class ContextDeviceInfo(private val context: Context) : DeviceInfo {

    override val appVersion by lazy {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty()
    }

    override val majorAppVersion by lazy { appVersion.split(".").first() }

    override val language: String by lazy { Locale.getDefault().language }

    override val country: String by lazy {
        val telephonyCountry = telephonyManager.networkCountryIso
        val deviceCountry =
            if (telephonyCountry.isNotBlank()) telephonyCountry else Locale.getDefault().country
        deviceCountry.toLowerCase()
    }

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun formFactor(): DeviceInfo.FormFactor {
        val metrics = context.resources.displayMetrics
        val smallestSize = Math.min(metrics.widthPixels, metrics.heightPixels)
        val tabletSize =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 600f, context.resources.displayMetrics)
                .toInt()
        return if (smallestSize >= tabletSize) DeviceInfo.FormFactor.TABLET
        else DeviceInfo.FormFactor.PHONE
    }
}
