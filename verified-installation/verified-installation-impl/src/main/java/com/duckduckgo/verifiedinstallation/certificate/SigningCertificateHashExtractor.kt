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

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
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

    override fun sha256Hash(): String? {
        return kotlin.runCatching {
            val info: PackageInfo? = context.packageManager.getPackageInfo(appBuildConfig.applicationId, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info?.signingInfo ?: return@runCatching null

            if (signingInfo.signingCertificateHistory.size != 1) {
                return@runCatching null
            }

            signingInfo.signingCertificateHistory?.lastOrNull()?.sha256()
        }.getOrNull()
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
