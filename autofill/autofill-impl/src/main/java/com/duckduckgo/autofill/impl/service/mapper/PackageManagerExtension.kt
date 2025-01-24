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
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import java.security.MessageDigest

@SuppressLint("NewApi")
internal fun PackageManager.getSHA256HexadecimalFingerprintCompat(
    packageName: String,
    appBuildConfig: AppBuildConfig,
): List<String> {
    return kotlin.runCatching {
        if (appBuildConfig.sdkInt >= 28) {
            getSHA256Fingerprint(packageName, this)
        } else {
            getSHA256FingerprintLegacy(packageName, this)
        }
    }.getOrElse { emptyList() }
}

@RequiresApi(28)
private fun getSHA256Fingerprint(
    packageName: String,
    packageManager: PackageManager,
): List<String> {
    return try {
        val packageInfo: PackageInfo = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES, // Use GET_SIGNING_CERTIFICATES for API 28+
        )

        // Get the signing certificates
        val signatures = packageInfo.signingInfo?.let {
            if (it.hasMultipleSigners()) {
                it.apkContentsSigners
            } else {
                it.signingCertificateHistory
            }
        }

        signatures?.map {
            it.sha256()
        } ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@Suppress("DEPRECATION")
private fun getSHA256FingerprintLegacy(
    packageName: String,
    packageManager: PackageManager,
): List<String> {
    val packageInfo: PackageInfo = packageManager.getPackageInfo(
        packageName,
        PackageManager.GET_SIGNATURES,
    )

    val signatures = packageInfo.signatures ?: return emptyList()

    if (signatures.size != 1) {
        return emptyList()
    }

    return signatures.map {
        it.sha256()
    }
}

private fun Signature.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(this.toByteArray())

    // convert byte array to a hexadecimal string representation
    return bytes.joinToString(":") { byte -> "%02X".format(byte) }
}
