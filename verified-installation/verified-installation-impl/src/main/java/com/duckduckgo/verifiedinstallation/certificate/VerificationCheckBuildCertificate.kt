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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VerificationCheckBuildCertificate {
    fun builtWithVerifiedCertificate(): Boolean
}

@ContributesBinding(AppScope::class)
class VerificationCheckBuildCertificateImpl @Inject constructor(
    private val certificateHashExtractor: SigningCertificateHashExtractor,
) : VerificationCheckBuildCertificate {

    override fun builtWithVerifiedCertificate(): Boolean {
        val hash = certificateHashExtractor.sha256Hash()
        return PRODUCTION_SHA_256_HASH.equals(hash, ignoreCase = true)
    }

    companion object {
        const val PRODUCTION_SHA_256_HASH = "bb7bb31c573c46a1da7fc5c528a6acf432108456feec50810c7f33694eb3d2d4"
    }
}
