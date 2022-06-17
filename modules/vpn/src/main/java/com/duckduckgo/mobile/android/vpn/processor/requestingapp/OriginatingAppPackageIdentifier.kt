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

import android.annotation.SuppressLint
import android.os.Build
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Named

data class ConnectionInfo(
    val destinationAddress: InetAddress,
    val destinationPort: Int,
    val sourceAddress: InetAddress,
    val sourcePort: Int,
    val protocolNumber: Int
)

interface OriginatingAppPackageIdentifier {
    fun resolvePackageId(connectionInfo: ConnectionInfo): String
}

class OriginatingAppPackageIdentifierStrategy @Inject constructor(
    @Named("DetectOriginatingAppPackageModern") private val modern: OriginatingAppPackageIdentifier,
    @Named("DetectOriginatingAppPackageLegacy") private val legacy: OriginatingAppPackageIdentifier
) {

    @SuppressLint("NewApi")
    fun resolvePackageId(
        connectionInfo: ConnectionInfo,
        sdkVersion: Int = Build.VERSION.SDK_INT
    ): String {

        return if (sdkVersion >= Build.VERSION_CODES.Q) {
            modern.resolvePackageId(connectionInfo)
        } else {
            legacy.resolvePackageId(connectionInfo)
        }
    }

    companion object {
        const val UNKNOWN = "unknown"
    }
}
