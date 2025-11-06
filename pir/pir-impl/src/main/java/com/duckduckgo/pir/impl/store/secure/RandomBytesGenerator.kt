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

package com.duckduckgo.pir.impl.store.secure

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.security.SecureRandom
import javax.inject.Inject

/**
 * This class is responsible for generating new ByteArray passwords/passphrase that can be used for any purpose
 */
interface PirRandomBytesGenerator {
    fun generateBytes(size: Int): ByteArray
}

@ContributesBinding(AppScope::class)
class RealPirRandomBytesGenerator @Inject constructor() : PirRandomBytesGenerator {

    override fun generateBytes(size: Int): ByteArray {
        return ByteArray(size).apply {
            SecureRandom.getInstanceStrong().nextBytes(this)
        }
    }
}
