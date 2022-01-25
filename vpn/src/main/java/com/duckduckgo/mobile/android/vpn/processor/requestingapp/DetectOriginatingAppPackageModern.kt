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

package com.duckduckgo.mobile.android.vpn.processor.requestingapp

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.net.InetSocketAddress

@RequiresApi(Build.VERSION_CODES.Q)
class DetectOriginatingAppPackageModern(
    private val connectivityManager: ConnectivityManager,
    private val packageManager: PackageManager
) : OriginatingAppPackageIdentifier {

    override fun resolvePackageId(connectionInfo: ConnectionInfo): String {
        val destination = InetSocketAddress(connectionInfo.destinationAddress, connectionInfo.destinationPort)
        val source = InetSocketAddress(connectionInfo.sourceAddress, connectionInfo.sourcePort)
        val connectionOwnerUid: Int = getConnectionOwnerUid(connectionInfo, source, destination)
        return getPackageIdForUid(connectionOwnerUid)
    }

    private fun getConnectionOwnerUid(
        connectionInfo: ConnectionInfo,
        source: InetSocketAddress,
        destination: InetSocketAddress
    ): Int {
        return try {
            connectivityManager.getConnectionOwnerUid(connectionInfo.protocolNumber, source, destination)
        } catch (t: Throwable) {
            Timber.e(t, "Error getting connection owner UID")
            Process.INVALID_UID
        }
    }

    private fun getPackageIdForUid(uid: Int): String {
        val packages = packageManager.getPackagesForUid(uid)
        if (packages.isNullOrEmpty()) {
            Timber.w("Failed to get package ID for UID: $uid")
            return OriginatingAppPackageIdentifierStrategy.UNKNOWN
        }

        if (packages.size > 1) {
            val sb = StringBuilder(String.format("Found %d packages for uid:%d", packages.size, uid))
            packages.forEach {
                sb.append(String.format("\npackage: %s", it))
            }
            Timber.d(sb.toString())
        }

        return packages.first()
    }
}
