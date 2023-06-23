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

import com.duckduckgo.app.email.api.DuckAddressStatusManagementService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.duckaddress.DuckAddressStatusRepository
import com.duckduckgo.autofill.api.duckaddress.DuckAddressStatusRepository.ActivationStatusResult
import com.duckduckgo.autofill.api.duckaddress.DuckAddressStatusRepository.ActivationStatusResult.Activated
import com.duckduckgo.autofill.api.duckaddress.DuckAddressStatusRepository.ActivationStatusResult.Deactivated
import com.duckduckgo.autofill.api.duckaddress.DuckAddressStatusRepository.ActivationStatusResult.GeneralError
import com.duckduckgo.autofill.api.duckaddress.DuckAddressStatusRepository.ActivationStatusResult.NotSignedIn
import com.duckduckgo.autofill.api.duckaddress.DuckAddressStatusRepository.ActivationStatusResult.Unmanageable
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.net.HttpURLConnection
import javax.inject.Inject
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RemoteDuckAddressStatusRepository @Inject constructor(
    private val service: DuckAddressStatusManagementService,
    private val emailDatastore: EmailDataStore,
    private val dispatchers: DispatcherProvider,
) : DuckAddressStatusRepository {

    override suspend fun getActivationStatus(privateDuckAddress: String): ActivationStatusResult {
        return withContext(dispatchers.io()) {
            val authToken = emailDatastore.emailToken ?: return@withContext NotSignedIn
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
            Timber.d("Got status of duck address %s. Activated=%s", formattedDuckAddress, status.active)
            if (status.active) Activated else Deactivated
        } catch (e: Exception) {
            when (e) {
                is HttpException -> when (e.code()) {
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        Timber.w("Duck address not found: %s", formattedDuckAddress)
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
            val authToken = emailDatastore.emailToken ?: return@withContext false
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
            Timber.i("Network request to update status succeeded. Activated=${status.active}")
            return status.active == isActive
        } catch (e: Exception) {
            Timber.w("Failed to update activation status", e)
            false
        }
    }

    companion object {
        private const val DUCK_ADDRESS_SUFFIX = "@duck.com"
    }
}
