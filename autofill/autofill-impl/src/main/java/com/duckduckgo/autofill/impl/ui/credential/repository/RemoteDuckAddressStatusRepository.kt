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

package com.duckduckgo.autofill.impl.ui.credential.repository

import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.email.service.DuckAddressStatusManagementService
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository.ActivationStatusResult
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository.ActivationStatusResult.*
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import retrofit2.HttpException
import java.net.HttpURLConnection
import javax.inject.Inject

/**
 * Repository for Duck Address activation status
 *
 * Provides ability to get and set activation status for a private duck address
 */
interface DuckAddressStatusRepository {

    /**
     * Get activation status for a private duck address
     *
     * @return [ActivationStatusResult] representing the activation status for the private duck address
     */
    suspend fun getActivationStatus(privateDuckAddress: String): ActivationStatusResult

    /**
     * Set activation status for a private duck address
     *
     * @return true if the activation status was successfully set, false otherwise
     */
    suspend fun setActivationStatus(privateDuckAddress: String, isActivated: Boolean): Boolean

    sealed interface ActivationStatusResult {
        data object NotSignedIn : ActivationStatusResult
        data object Activated : ActivationStatusResult
        data object Deactivated : ActivationStatusResult
        data object Unmanageable : ActivationStatusResult
        data object GeneralError : ActivationStatusResult
    }
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RemoteDuckAddressStatusRepository @Inject constructor(
    private val service: DuckAddressStatusManagementService,
    private val emailManager: EmailManager,
    private val dispatchers: DispatcherProvider,
) : DuckAddressStatusRepository {

    override suspend fun getActivationStatus(privateDuckAddress: String): ActivationStatusResult {
        return withContext(dispatchers.io()) {
            val authToken = emailManager.getToken() ?: return@withContext NotSignedIn
            val formattedAuthToken = "Bearer $authToken"
            val formattedDuckAddress = privateDuckAddress.removeSuffix(DUCK_ADDRESS_SUFFIX)

            return@withContext executeNetworkRequestToGetStatus(formattedAuthToken, formattedDuckAddress)
        }
    }

    private suspend fun executeNetworkRequestToGetStatus(
        formattedAuthToken: String,
        formattedDuckAddress: String,
    ): ActivationStatusResult {
        return try {
            val status = service.getActivationStatus(authorization = formattedAuthToken, duckAddress = formattedDuckAddress)
            logcat(VERBOSE) { "Got status of duck address $formattedDuckAddress. Activated=${status.active}" }
            if (status.active) Activated else Deactivated
        } catch (e: Exception) {
            when (e) {
                is HttpException -> when (e.code()) {
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        logcat(WARN) { "Duck address not found: $formattedDuckAddress" }
                        Unmanageable
                    }

                    else -> GeneralError
                }

                else -> GeneralError
            }
        }
    }

    override suspend fun setActivationStatus(
        privateDuckAddress: String,
        isActivated: Boolean,
    ): Boolean {
        return withContext(dispatchers.io()) {
            val authToken = emailManager.getToken() ?: return@withContext false
            val formattedAuthToken = "Bearer $authToken"
            val formattedDuckAddress = privateDuckAddress.removeSuffix(DUCK_ADDRESS_SUFFIX)

            return@withContext executeNetworkRequestToSetStatus(formattedAuthToken, formattedDuckAddress, isActivated)
        }
    }

    private suspend fun executeNetworkRequestToSetStatus(
        formattedAuthToken: String,
        formattedDuckAddress: String,
        isActive: Boolean,
    ): Boolean {
        return try {
            val status =
                service.setActivationStatus(authorization = formattedAuthToken, duckAddress = formattedDuckAddress, isActive = isActive)
            logcat(INFO) { "Network request to update status succeeded. Activated=${status.active}" }
            return status.active == isActive
        } catch (e: Exception) {
            logcat(WARN) { "Failed to update activation status ${e.asLog()}" }
            false
        }
    }

    companion object {
        private const val DUCK_ADDRESS_SUFFIX = "@duck.com"
    }
}
