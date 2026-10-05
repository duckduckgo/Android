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

package com.duckduckgo.credentialexchange.api

/**
 * App-wide entry point for Android's credential transfer flow. Starting the flow needs an Activity, so that lives in [CredentialExchangeLauncher].
 */
interface CredentialExchange {

    /**
     * Whether this build can run the flow at all, and whether any app on this device offers credentials to export.
     *
     * Best effort: A `true` is not a promise, and [CredentialExchangeLauncher.launchImportFlow] can still
     * end in [CredentialExchangeFailure.NO_EXPORTER_AVAILABLE].
     */
    suspend fun isImportSupported(): Boolean
}

sealed interface CredentialExchangeResult {

    /**
     * [credentials] is everything the exporting app sent, in the order it sent it.
     * [exporterPackageName] the package name of the password manager which provided the credentials
     * */
    data class Success(
        val credentials: List<ExchangedCredential>,
        val exporterPackageName: String?,
    ) : CredentialExchangeResult

    /** The user cancelled the exchange */
    data object Cancelled : CredentialExchangeResult

    /**
     * The exchange ended in a failure, for the given reason.
     *
     * [exporterPackageName] is set only when a [reason] of [CredentialExchangeFailure.MALFORMED_PAYLOAD] followed a response
     * from an exporting app; the other failure reasons happen before any app is known, so it is always null there.
     */
    data class Failure(
        val reason: CredentialExchangeFailure,
        val exporterPackageName: String? = null,
    ) : CredentialExchangeResult
}

/**
 * One `basic-auth` login from a CXF payload, as the exporting app sent it.
 *
 * A CXF item can hold several logins that share one set of [urls], [title] and [note]; each login becomes its own
 * [ExchangedCredential] carrying copies of them.
 *
 * [urls] are the item's web addresses in the order the exporter sent them; CXF treats the login as valid on
 * all of them. Any Android app scopes are dropped.
 */
data class ExchangedCredential(
    val urls: List<String>,
    val title: String?,
    val username: String?,
    val password: String?,
    val note: String?,
) {
    /** Masked: everything here except the title is either a secret or personal data. */
    override fun toString(): String = "ExchangedCredential(url=***, title=$title, username=***, password=***, note=***)"
}

enum class CredentialExchangeFailure {

    /** No credential transfer backend in this build. Always the answer on F-Droid builds. */
    NOT_SUPPORTED,

    /** Nothing on this device offered credentials to export. */
    NO_EXPORTER_AVAILABLE,

    /** The exporting app sent something we could not read as CXF. */
    MALFORMED_PAYLOAD,

    /** All other failures */
    UNKNOWN,
}
