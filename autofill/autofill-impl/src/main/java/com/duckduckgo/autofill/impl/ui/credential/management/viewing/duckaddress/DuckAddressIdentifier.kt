/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing.duckaddress

import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DuckAddressIdentifier {

    /**
     * A username will be considered a private duck address only if it ends with @duck.com and doesn't match main duck address
     * @param username the username to check. Should include the @duck.com suffix if was included.
     * @param mainDuckAddress the main duck address to check against. This can have its @duck.com suffix provided or omitted (both are valid). If not signed into email protection, this can be null.
     */
    fun isPrivateDuckAddress(
        username: String,
        mainDuckAddress: String?,
    ): Boolean
}

@ContributesBinding(ActivityScope::class)
class RealDuckAddressIdentifier @Inject constructor() : DuckAddressIdentifier {

    override fun isPrivateDuckAddress(
        username: String,
        mainDuckAddress: String?,
    ): Boolean {
        return username.endsWith(DUCK_ADDRESS_SUFFIX) &&
            username.length > DUCK_ADDRESS_SUFFIX.length &&
            username.normalizeDuckAddress() != mainDuckAddress.normalizeDuckAddress()
    }

    private fun String?.normalizeDuckAddress(): String? {
        if (this == null) return null

        return if (endsWith(DUCK_ADDRESS_SUFFIX)) {
            this
        } else {
            "${this}$DUCK_ADDRESS_SUFFIX"
        }
    }

    companion object {
        private const val DUCK_ADDRESS_SUFFIX = "@duck.com"
    }
}
