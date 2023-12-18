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

package com.duckduckgo.fingerprintprotection.store.seed

import java.util.*
import java.util.concurrent.atomic.AtomicReference

interface FingerprintProtectionSeedRepository {
    val seed: String
    fun storeNewSeed()
}

class RealFingerprintProtectionSeedRepository constructor() : FingerprintProtectionSeedRepository {

    private val atomicSeed = AtomicReference(getRandomSeed())
    override val seed: String
        get() = atomicSeed.get()

    override fun storeNewSeed() {
        atomicSeed.set(getRandomSeed())
    }

    private fun getRandomSeed(): String {
        return UUID.randomUUID().toString()
    }
}
