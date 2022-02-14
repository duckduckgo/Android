/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.network

import android.annotation.TargetApi
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.network.VpnDetector.AlwaysOn
import com.duckduckgo.mobile.android.vpn.network.VpnDetector.AlwaysOnMode
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealVpnDetector @Inject constructor(
    private val context: Context
) : VpnDetector {

    override fun isVpnDetected(): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            detectVpnLollipop()
        } else {
            detectVpn()
        }
    }

    override fun inAlwaysOnMode(): AlwaysOnMode {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            detectAlwaysOnModeLollipop()
        } else {
            detectAlwaysOnMode()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun detectVpn(): Boolean {
        val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        return connectivityManager.getNetworkCapabilities(activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun detectVpnLollipop(): Boolean {
        val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_VPN ?: false
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun detectAlwaysOnMode(): AlwaysOnMode {

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun detectAlwaysOnModeLollipop(): AlwaysOnMode {

    }

}
