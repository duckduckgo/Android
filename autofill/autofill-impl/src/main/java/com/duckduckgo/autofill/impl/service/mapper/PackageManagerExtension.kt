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

package com.duckduckgo.autofill.impl.service.mapper

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

internal fun PackageManager.getSHA256HexadecimalFingerprintCompat(packageName: String): List<String> {
    return kotlin.runCatching {
        val packageInfo: PackageInfo = getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = packageInfo.signingInfo?.let {
            if (it.hasMultipleSigners()) {
                it.apkContentsSigners
            } else {
                it.signingCertificateHistory
            }
        }
        signatures?.map { it.sha256() } ?: emptyList()
    }.getOrElse { emptyList() }
}

private fun Signature.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(this.toByteArray())

    // convert byte array to a hexadecimal string representation
    return bytes.joinToString(":") { byte -> "%02X".format(byte) }
}
