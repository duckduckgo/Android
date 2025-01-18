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

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import java.security.MessageDigest

@SuppressLint("NewApi")
internal fun PackageManager.getSHA256HexadecimalFingerprintCompat(
    packageName: String,
    appBuildConfig: AppBuildConfig,
): String? {
    return kotlin.runCatching {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.P) {
            getSHA256Fingerprint(packageName, this)
        } else {
            getSHA256FingerprintLegacy(packageName, this)
        }
    }.getOrNull()
}

@RequiresApi(VERSION_CODES.P)
private fun getSHA256Fingerprint(
    packageName: String,
    packageManager: PackageManager,
): String? {
    return try {
        val packageInfo: PackageInfo = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES, // Use GET_SIGNING_CERTIFICATES for API 28+
        )

        // Get the signing certificates
        val signatures = packageInfo.signingInfo.apkContentsSigners

        signatures.firstOrNull()?.sha256()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Suppress("DEPRECATION")
private fun getSHA256FingerprintLegacy(
    packageName: String,
    packageManager: PackageManager,
): String? {
    val packageInfo: PackageInfo = packageManager.getPackageInfo(
        packageName,
        PackageManager.GET_SIGNATURES,
    )

    val signatures = packageInfo.signatures ?: return null

    if (signatures.size != 1) {
        return null
    }

    return signatures.firstOrNull()?.sha256()
}

private fun Signature.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(this.toByteArray())

    // convert byte array to a hexadecimal string representation
    return bytes.joinToString(":") { byte -> "%02X".format(byte) }
}
