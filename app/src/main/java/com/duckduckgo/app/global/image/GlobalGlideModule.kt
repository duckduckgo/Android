/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.image

import android.content.Context
import android.os.Build
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.duckduckgo.app.browser.certificates.rootstore.IsrgRootX1
import com.duckduckgo.app.browser.certificates.rootstore.IsrgRootX2
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import timber.log.Timber
import java.io.InputStream
import java.security.cert.X509Certificate

@GlideModule
class GlobalGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            try {
                Timber.d("Registering OkHttp-based ModelLoader for GlideUrl")

                val isrgRootX1 = IsrgRootX1(context)
                val isrgRootX2 = IsrgRootX2(context)

                val handshakeCertificates = HandshakeCertificates.Builder()
                    .addTrustedCertificate(isrgRootX1.certificate() as X509Certificate)
                    .addTrustedCertificate(isrgRootX2.certificate() as X509Certificate)
                    .addPlatformTrustedCertificates()
                    .build()

                val okHttpClient = OkHttpClient.Builder()
                    .sslSocketFactory(handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager)
                    .build()

                // use our custom okHttp instead of default HTTPUrlConnection
                registry.replace(
                    GlideUrl::class.java,
                    InputStream::class.java,
                    OkHttpUrlLoader.Factory(okHttpClient)
                )
            } catch (t: Throwable) {
                Timber.d("Error registering GlideModule for GlideUrl: $t")
                super.registerComponents(context, glide, registry)
            }
        } else {
            super.registerComponents(context, glide, registry)
        }
    }
}
