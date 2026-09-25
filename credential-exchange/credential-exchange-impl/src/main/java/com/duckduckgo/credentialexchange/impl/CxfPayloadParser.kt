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

package com.duckduckgo.credentialexchange.impl

import androidx.credentials.providerevents.transfer.CredentialTypes.CREDENTIAL_TYPE_BASIC_AUTH
import androidx.credentials.providerevents.transfer.CredentialTypes.CREDENTIAL_TYPE_NOTE
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.credentialexchange.api.ExchangedCredential
import com.duckduckgo.credentialexchange.impl.CxfParseResult.Malformed
import com.duckduckgo.credentialexchange.impl.CxfParseResult.Parsed
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

internal const val LOG_PREFIX = "CredentialExchange: "

/**
 * Reads the FIDO Credential Exchange Format (CXF) v1.0 payload returned by the exporting app.
 * Spec: https://fidoalliance.org/specs/cx/cxf-v1.0-ps-20250814.html
 */
interface CxfPayloadParser {
    suspend fun parse(cxfJson: String): CxfParseResult
}

sealed interface CxfParseResult {
    data class Parsed(val credentials: List<ExchangedCredential>) : CxfParseResult
    data object Malformed : CxfParseResult
}

@ContributesBinding(AppScope::class)
class RealCxfPayloadParser @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : CxfPayloadParser {

    private val moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }
    private val adapter by lazy { moshi.adapter(CxfPayload::class.java) }

    override suspend fun parse(cxfJson: String): CxfParseResult = withContext(dispatchers.io()) {
        val payload = runCatching { adapter.fromJson(cxfJson) }
            .onFailure { logcat(ERROR) { "${LOG_PREFIX}could not read CXF payload: ${it.message}" } }
            .getOrNull()

        val accounts = payload?.accounts ?: return@withContext Malformed

        Parsed(accounts.flatMap { account -> account.items.orEmpty().flatMap { it.toExchangedCredentials() } })
    }

    private fun CxfItem.toExchangedCredentials(): List<ExchangedCredential> {
        val logins = credentials.orEmpty()
            .filter { it.type == CREDENTIAL_TYPE_BASIC_AUTH }
            .filterNot { it.password?.value.isNullOrBlank() }

        // a note belongs to the item, not to one login, so copying it onto several would both
        // duplicate it and break the notes comparison the caller makes against saved passwords
        val note = if (logins.size == 1) {
            credentials.orEmpty()
                .filter { it.type == CREDENTIAL_TYPE_NOTE }
                .firstNotNullOfOrNull { it.content?.value?.takeIf(String::isNotBlank) }
        } else {
            null
        }

        return logins.map {
            ExchangedCredential(
                url = scope?.urls?.firstOrNull { url -> url.isNotBlank() },
                title = title,
                username = it.username?.value,
                password = it.password?.value,
                note = note,
            )
        }
    }
}

@JsonClass(generateAdapter = false)
internal data class CxfPayload(val accounts: List<CxfAccount>? = null)

@JsonClass(generateAdapter = false)
internal data class CxfAccount(val items: List<CxfItem>? = null)

@JsonClass(generateAdapter = false)
internal data class CxfItem(
    val title: String? = null,
    val scope: CxfScope? = null,
    val credentials: List<CxfCredential>? = null,
)

@JsonClass(generateAdapter = false)
internal data class CxfScope(val urls: List<String>? = null)

/**
 * One entry of the polymorphic `credentials` array. Every field is optional because a single shape
 * has to cover all 17 CXF credential types; a type we don't read simply leaves them null.
 */
@JsonClass(generateAdapter = false)
internal data class CxfCredential(
    val type: String? = null,
    val username: CxfEditableField? = null,
    val password: CxfEditableField? = null,
    val content: CxfEditableField? = null,
)

@JsonClass(generateAdapter = false)
internal data class CxfEditableField(val value: String? = null)
