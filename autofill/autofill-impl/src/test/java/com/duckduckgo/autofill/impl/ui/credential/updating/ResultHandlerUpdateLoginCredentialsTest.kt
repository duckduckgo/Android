/*
 * Copyright (c) 2024 DuckDuckGo
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

import android.os.Bundle
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType.Password
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class ResultHandlerUpdateLoginCredentialsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val autofillStore: InternalAutofillStore = mock()
    private val autofillDialogSuppressor: AutofillFireproofDialogSuppressor = mock()
    private val callback: AutofillEventListener = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val webView: WebView = mock()

    private val testee = ResultHandlerUpdateLoginCredentials(
        autofillFireproofDialogSuppressor = autofillDialogSuppressor,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillStore = autofillStore,
        appBuildConfig = appBuildConfig,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenUpdateBundleMissingUrlThenNoAttemptToUpdateMade() = runTest {
        val bundle = bundleForUpdateDialog(
            url = null,
            credentials = someLoginCredentials(),
            Password,
        )
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verifyUpdateNeverCalled()
    }

    @Test
    fun whenUpdateBundleMissingCredentialsThenNoAttemptToSaveMade() = runTest {
        val bundle =
            bundleForUpdateDialog(url = "example.com", credentials = null, Password)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verifyUpdateNeverCalled()
    }

    @Test
    fun whenUpdateBundleWellFormedThenCredentialsAreUpdated() = runTest {
        val loginCredentials = LoginCredentials(domain = "example.com", username = "foo", password = "bar")
        val bundle = bundleForUpdateDialog("example.com", loginCredentials, Password)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore).updateCredentials(
            eq("example.com"),
            eq(loginCredentials),
            eq(Password),
        )
        verifySaveNeverCalled()
    }

    private suspend fun verifySaveNeverCalled() {
        verify(autofillStore, never()).saveCredentials(any(), any())
    }

    private suspend fun verifyUpdateNeverCalled() {
        verify(autofillStore, never()).updateCredentials(any(), any(), any())
    }

    private fun someLoginCredentials() = LoginCredentials(domain = "example.com", username = "foo", password = "bar")

    private fun bundleForUpdateDialog(
        url: String?,
        credentials: LoginCredentials?,
        updateType: CredentialUpdateType,
    ): Bundle {
        return Bundle().also {
            if (url != null) it.putString(CredentialUpdateExistingCredentialsDialog.KEY_URL, url)
            if (credentials != null) it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS, credentials)
            it.putParcelable(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIAL_UPDATE_TYPE, updateType)
        }
    }
}
