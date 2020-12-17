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

package com.duckduckgo.app.browser.certificates

import android.content.Context
import com.duckduckgo.app.browser.certificates.rootstore.*
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class CertificateTrustedStoreModule {
    @Provides
    @Singleton
    fun trustedCertificateStore(
        letsEncryptCertificateProvider: LetsEncryptCertificateProvider
    ): TrustedCertificateStore = TrustedCertificateStoreImpl(letsEncryptCertificateProvider)

    @Provides
    @Singleton
    fun letsEncryptCertificateProvider(
        context: Context
    ): LetsEncryptCertificateProvider = LetsEncryptCertificateProviderImpl(
        setOf(
            IsrgRootX1(context),
            IsrgRootX2(context),
            LetsEncryptAuthorityX3(context),
            LetsEncryptR3(context),
            LetsEncryptE1(context)
        )
    )
}
