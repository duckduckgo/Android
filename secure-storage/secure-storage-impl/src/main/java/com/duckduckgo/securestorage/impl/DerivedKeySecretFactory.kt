/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.securestorage.impl

import java.security.Key
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

interface DerivedKeySecretFactory {
    fun getKey(spec: PBEKeySpec): Key
}

class RealDerivedKeySecretFactory : DerivedKeySecretFactory {
    private val secretKeyFactory by lazy {
        SecretKeyFactory.getInstance("PBKDF2withHmacSHA256")
    }

    override fun getKey(spec: PBEKeySpec): Key = secretKeyFactory.generateSecret(spec)
}

class LegacyDerivedKeySecretFactory : DerivedKeySecretFactory {
    private val secretKeyFactory by lazy {
        SecretKeyFactory.getInstance("PBKDF2withHmacSHA1")
    }

    override fun getKey(spec: PBEKeySpec): Key = secretKeyFactory.generateSecret(spec)
}
