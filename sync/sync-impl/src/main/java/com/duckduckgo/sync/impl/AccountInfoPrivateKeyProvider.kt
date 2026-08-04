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

package com.duckduckgo.sync.impl

import android.util.Base64
import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Supplies the account's `account_info` private key, unwrapped for the current credential and
 * encoded as base64url PKCS#8.
 */
@WorkerThread
interface AccountInfoPrivateKeyProvider {
    fun privateKey(): Result<String>
}

@ContributesBinding(AppScope::class)
class RealAccountInfoPrivateKeyProvider @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val protectedKeyUnwrapper: ProtectedKeyUnwrapper,
) : AccountInfoPrivateKeyProvider {

    override fun privateKey(): Result<String> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "AccountInfoPrivateKey: not signed in")
        val credentialId = syncStore.credentialId ?: CREDENTIAL_ID_DDG

        val entry = when (val result = syncApi.getProtectedKeys(token)) {
            is Success -> result.data.firstOrNull { it.purpose == SYNC_PURPOSE_ACCOUNT_INFO && it.encryptedWith == credentialId }
                ?: return Error(reason = "AccountInfoPrivateKey: no account_info key wrapped for credential=$credentialId")
            is Error -> return result
        }

        val rawKeyBytes = when (val result = protectedKeyUnwrapper.unwrap(entry)) {
            is Success -> result.data
            is Error -> return result
        }

        return Success(Base64.encodeToString(rawKeyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
    }
}
