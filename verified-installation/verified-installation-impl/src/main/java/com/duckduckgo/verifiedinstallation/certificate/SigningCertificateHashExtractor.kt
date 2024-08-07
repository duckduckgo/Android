/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.verifiedinstallation.certificate

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.security.MessageDigest
import javax.inject.Inject

interface SigningCertificateHashExtractor {
    fun sha256Hash(): String?
}

@ContributesBinding(AppScope::class)
class SigningCertificateHashExtractorImpl @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val context: Context,
) : SigningCertificateHashExtractor {

    @SuppressLint("NewApi")
    override fun sha256Hash(): String? {
        return kotlin.runCatching {
            if (appBuildConfig.sdkInt >= Build.VERSION_CODES.P) {
                getSigningCertHashesModern()
            } else {
                getSigningCertHashesLegacy()
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun getSigningCertHashesLegacy(): String? {
        val info: PackageInfo? = context.packageManager.getPackageInfo(appBuildConfig.applicationId, PackageManager.GET_SIGNATURES)
        val signatures = info?.signatures ?: return null

        if (signatures.size != 1) {
            return null
        }

        return signatures.firstOrNull()?.sha256()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun getSigningCertHashesModern(): String? {
        val info: PackageInfo? = context.packageManager.getPackageInfo(appBuildConfig.applicationId, PackageManager.GET_SIGNING_CERTIFICATES)
        if (info?.signingInfo == null) {
            return null
        }

        if (info.signingInfo!!.signingCertificateHistory.size != 1) {
            return null
        }

        return info.signingInfo!!.signingCertificateHistory?.lastOrNull()?.sha256()
    }

    private fun Signature.sha256(): String {
        val md = MessageDigest.getInstance(KEY_SHA_256)
        val bytes = md.digest(this.toByteArray())

        // convert byte array to a hexadecimal string representation
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_SHA_256 = "SHA-256"
    }
}
