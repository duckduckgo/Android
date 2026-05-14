/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.domain

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject

interface PublicKeyProvider {
    val publicKey: PublicKey
}

private const val KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuqrhn2ztPnPt8QybIGsLpROYR/xcJ/uw0jN85kdMQFKdCkwxOLaU70uVQ+zZ+F0B1fgx8qfAXus7mXWsJxBXrg=="

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPublicKeyProvider @Inject constructor(
    private val keyFactory: KeyFactory,
    private val decoder: Base64.Decoder,
) : PublicKeyProvider {

    override val publicKey: PublicKey by lazy {
        keyFactory.generatePublic(X509EncodedKeySpec(decoder.decode(KEY)))
    }
}
