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

package com.duckduckgo.app.browser.autofill

import android.os.Bundle
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult
import timber.log.Timber
import javax.inject.Inject

class AutofillCredentialsSelectionResultHandler @Inject constructor(val deviceAuthenticator: DeviceAuthenticator) {

    fun processAutofillCredentialSelectionResult(
        result: Bundle,
        browserTabFragment: BrowserTabFragment,
        viewModel: BrowserTabViewModel,
    ) {
        val originalUrl = result.getString("url") ?: return

        if (result.getBoolean("cancelled")) {
            Timber.v("Autofill: User cancelled credential selection")
            viewModel.returnNoCredentialsWithPage(originalUrl)
            return
        }

        deviceAuthenticator.authenticate(DeviceAuthenticator.Features.AUTOFILL, browserTabFragment) {
            val successfullyAuthenticated = it is AuthResult.Success
            Timber.v("Autofill: user selected credential to use. Successfully authenticated: %s", successfullyAuthenticated)

            if (successfullyAuthenticated) {
                val selectedCredentials = result.getParcelable<LoginCredentials>("creds") ?: return@authenticate
                viewModel.shareCredentialsWithPage(originalUrl, selectedCredentials)
            } else {
                viewModel.returnNoCredentialsWithPage(originalUrl)
            }
        }
    }

    fun processSaveCredentialsResult(result: Bundle, viewModel: BrowserTabViewModel) {
        val selectedCredentials = result.getParcelable<LoginCredentials>("creds") ?: return
        val originalUrl = result.getString("url") ?: return
        viewModel.saveCredentials(originalUrl, selectedCredentials)
    }
}
