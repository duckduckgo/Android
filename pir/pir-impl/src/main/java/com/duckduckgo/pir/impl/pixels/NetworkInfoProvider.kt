/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.pixels

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NetworkInfoProvider {
    fun getCurrentNetworkInfo(): String
}

@ContributesBinding(AppScope::class)
class RealNetworkInfoProvider @Inject constructor(
    private val context: Context,
) : NetworkInfoProvider {
    private val connectivityManager: ConnectivityManager by lazy { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    override fun getCurrentNetworkInfo(): String {
        val activeNetwork = connectivityManager.activeNetwork
        val activeNetworkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return if (activeNetworkCapabilities == null || !activeNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "NO_CONNECTION"
        } else {
            when {
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "NO_CONNECTION"
            }
        }
    }
}
