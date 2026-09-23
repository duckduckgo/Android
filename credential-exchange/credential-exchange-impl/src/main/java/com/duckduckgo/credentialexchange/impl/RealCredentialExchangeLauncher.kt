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

import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.providerevents.exception.ImportCredentialsCancellationException
import androidx.credentials.providerevents.exception.ImportCredentialsInvalidJsonException
import androidx.credentials.providerevents.exception.ImportCredentialsNoExportOptionException
import androidx.credentials.providerevents.exception.ImportCredentialsUnknownErrorException
import androidx.credentials.providerevents.transfer.CredentialTypes
import androidx.credentials.providerevents.transfer.ImportCredentialsRequest
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure.MALFORMED_PAYLOAD
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure.NO_EXPORTER_AVAILABLE
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure.UNKNOWN
import com.duckduckgo.credentialexchange.api.CredentialExchangeLauncher
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult.Cancelled
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult.Failure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult.Success
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
class RealCredentialExchangeLauncher @Inject constructor(
    private val activity: AppCompatActivity,
    private val providerEventsManagerFactory: ProviderEventsManagerFactory,
    private val cxfPayloadParser: CxfPayloadParser,
    private val dispatchers: DispatcherProvider,
) : CredentialExchangeLauncher {

    override suspend fun launchImportFlow(): CredentialExchangeResult {
        return when (val outcome = requestFromExporter()) {
            is ImportOutcome.Received -> parseCredentials(outcome.cxfJson, outcome.exporterPackageName)
            is ImportOutcome.Cancelled -> Cancelled
            is ImportOutcome.Failed -> Failure(outcome.reason)
        }
    }

    private suspend fun requestFromExporter(): ImportOutcome {
        val request = ImportCredentialsRequest(
            credentialTypes = setOf(CredentialTypes.CREDENTIAL_TYPE_BASIC_AUTH),
            knownExtensions = emptySet(),
        )

        return try {
            // Must be the Activity, not the app context: the Play Services backend calls startActivity
            // on it without FLAG_ACTIVITY_NEW_TASK.
            val response = withContext(dispatchers.main()) {
                providerEventsManagerFactory.create().importCredentials(activity, request)
            }
            val exporterPackageName = response.callingAppInfo.packageName

            logcat(VERBOSE) { "${LOG_PREFIX}exporter $exporterPackageName returned a payload" }
            ImportOutcome.Received(response.response.responseJson, exporterPackageName)
        } catch (_: ImportCredentialsCancellationException) {
            ImportOutcome.Cancelled
        } catch (_: ImportCredentialsNoExportOptionException) {
            ImportOutcome.Failed(NO_EXPORTER_AVAILABLE)
        } catch (_: ImportCredentialsInvalidJsonException) {
            ImportOutcome.Failed(MALFORMED_PAYLOAD)
        } catch (e: ImportCredentialsUnknownErrorException) {
            outcomeForUnknownError(e)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logcat(ERROR) { "${LOG_PREFIX}import failed: ${e.javaClass.name}, message=${e.message}" }
            ImportOutcome.Failed(UNKNOWN)
        }
    }

    // The Play Services backend never throws ImportCredentialsCancellationException. A user who backs
    // out of the system picker arrives here instead, with no message. Verified on device.
    private fun outcomeForUnknownError(e: ImportCredentialsUnknownErrorException): ImportOutcome {
        if (e.message == null) {
            logcat(VERBOSE) { "${LOG_PREFIX}cancelled by user" }
            return ImportOutcome.Cancelled
        }
        logcat(ERROR) { "${LOG_PREFIX}import failed: ${e.message}" }
        return ImportOutcome.Failed(UNKNOWN)
    }

    private suspend fun parseCredentials(cxfJson: String, exporterPackageName: String): CredentialExchangeResult {
        return when (val parsed = cxfPayloadParser.parse(cxfJson)) {
            is CxfParseResult.Parsed -> {
                logcat(VERBOSE) { "${LOG_PREFIX}exporter sent ${parsed.credentials.size} logins" }
                Success(parsed.credentials, exporterPackageName)
            }
            is CxfParseResult.Malformed -> Failure(MALFORMED_PAYLOAD, exporterPackageName)
        }
    }

    private sealed interface ImportOutcome {
        data class Received(val cxfJson: String, val exporterPackageName: String) : ImportOutcome
        data object Cancelled : ImportOutcome
        data class Failed(val reason: CredentialExchangeFailure) : ImportOutcome
    }
}
