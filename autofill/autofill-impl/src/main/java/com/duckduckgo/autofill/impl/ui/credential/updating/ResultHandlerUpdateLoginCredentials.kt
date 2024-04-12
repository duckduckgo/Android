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

package com.duckduckgo.autofill.impl.ui.credential.updating

import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.Companion.KEY_CREDENTIALS
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.Companion.KEY_CREDENTIAL_UPDATE_TYPE
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.Companion.KEY_URL
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class ResultHandlerUpdateLoginCredentials @Inject constructor(
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor,
    private val dispatchers: DispatcherProvider,
    private val autofillStore: InternalAutofillStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)

        val selectedCredentials = BundleCompat.getParcelable(result, KEY_CREDENTIALS, LoginCredentials::class.java) ?: return
        val autofillWebMessageRequest = BundleCompat.getParcelable(result, KEY_URL, AutofillWebMessageRequest::class.java) ?: return
        val updateType = BundleCompat.getParcelable(result, KEY_CREDENTIAL_UPDATE_TYPE, CredentialUpdateType::class.java) ?: return

        appCoroutineScope.launch(dispatchers.io()) {
            autofillStore.updateCredentials(autofillWebMessageRequest.requestOrigin, selectedCredentials, updateType)?.let {
                withContext(dispatchers.main()) {
                    autofillCallback.onUpdatedCredentials(it)
                }
            }
        }
    }

    override fun resultKey(tabId: String): String {
        return CredentialUpdateExistingCredentialsDialog.resultKeyCredentialUpdated(tabId)
    }
}
