/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.certificates.rootstore

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.certificates.LetsEncryptCertificate
import com.duckduckgo.app.browser.certificates.CertificateType
import com.duckduckgo.app.browser.certificates.CertificateTypes
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

/**
 * Let's Encrypt ISRG Root X2 (self-signed) ROOT certificate
 */
class IsrgRootX2(
    private val context: Context
) : LetsEncryptCertificate {

    private val certificate: Certificate by lazy {
        val certificateFactory = CertificateFactory.getInstance(CertificateTypes.X509)
        val certificate = certificateFactory.generateCertificate(context.resources.openRawResource(R.raw.isrg_root_x2))
        certificate
    }

    override fun certificate(): Certificate {
        return certificate
    }

    override fun type(): CertificateType {
        return CertificateType.Root
    }
}
