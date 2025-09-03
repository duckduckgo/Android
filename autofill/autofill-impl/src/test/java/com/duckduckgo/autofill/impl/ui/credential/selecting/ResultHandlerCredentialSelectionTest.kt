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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.os.Bundle
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.NoMatch
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.deviceauth.FakeAuthenticator
import com.duckduckgo.autofill.impl.engagement.DataAutofilledListener
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class ResultHandlerCredentialSelectionTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pixel: Pixel = mock()
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector = mock()
    private val callback: AutofillEventListener = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private lateinit var deviceAuthenticator: FakeAuthenticator
    private lateinit var testee: ResultHandlerCredentialSelection
    private val autofillStore: InternalAutofillStore = mock()
    private val webView: WebView = mock()

    @Before
    fun setup() = runTest {
        whenever(
            existingCredentialMatchDetector.determine(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(NoMatch)
    }

    @Test
    fun whenUserRejectedToUseCredentialThenCorrectCallbackInvoked() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleForUserCancelling("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onNoCredentialsChosenForAutofill("example.com")
    }

    @Test
    fun whenUserAcceptedToUseCredentialsAndSuccessfullyAuthenticatedThenCorrectCallbackInvoked() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleForUserAcceptingToAutofill("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onShareCredentialsForAutofill("example.com", aLogin())
    }

    @Test
    fun whenUserAcceptedToUseCredentialsAndCancelsAuthenticationThenCorrectCallbackInvoked() = runTest {
        configureCancelledAuth()
        val bundle = bundleForUserAcceptingToAutofill("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onNoCredentialsChosenForAutofill("example.com")
    }

    @Test
    fun whenUserAcceptedToUseCredentialsAndAuthenticationFailsThenCorrectCallbackInvoked() = runTest {
        configureFailedAuth()
        val bundle = bundleForUserAcceptingToAutofill("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onNoCredentialsChosenForAutofill("example.com")
    }

    @Test
    fun whenUserAcceptedToUseCredentialsButMissingInBundleThenNoCallbackInvoked() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleMissingCredentials("example.com")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verifyNoInteractions(callback)
    }

    @Test
    fun whenMissingUrlThenNoCallbackInvoked() = runTest {
        configureSuccessfulAuth()
        val bundle = bundleMissingUrl()
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verifyNoInteractions(callback)
    }

    private fun bundleForUserCancelling(url: String?): Bundle {
        return Bundle().also {
            it.putString(CredentialAutofillPickerDialog.KEY_URL, url)
            it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, true)
        }
    }

    private fun bundleForUserAcceptingToAutofill(url: String?): Bundle {
        return Bundle().also {
            it.putString(CredentialAutofillPickerDialog.KEY_URL, url)
            it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, false)
            it.putParcelable(CredentialAutofillPickerDialog.KEY_CREDENTIALS, aLogin())
        }
    }

    private fun bundleMissingUrl(): Bundle = Bundle()
    private fun bundleMissingCredentials(url: String?): Bundle {
        return Bundle().also {
            it.putString(CredentialAutofillPickerDialog.KEY_URL, url)
        }
    }

    private fun aLogin(): LoginCredentials {
        return LoginCredentials(domain = "example.com", username = "foo", password = "bar")
    }

    private fun configureSuccessfulAuth() {
        deviceAuthenticator = FakeAuthenticator.AuthorizeEverything()
        instantiateClassUnderTest(deviceAuthenticator)
    }

    private fun configureFailedAuth() {
        deviceAuthenticator = FakeAuthenticator.FailEverything()
        instantiateClassUnderTest(deviceAuthenticator)
    }

    private fun configureCancelledAuth() {
        deviceAuthenticator = FakeAuthenticator.CancelEverything()
        instantiateClassUnderTest(deviceAuthenticator)
    }

    private fun instantiateClassUnderTest(deviceAuthenticator: FakeAuthenticator) {
        testee = ResultHandlerCredentialSelection(
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            pixel = pixel,
            deviceAuthenticator = deviceAuthenticator,
            appBuildConfig = appBuildConfig,
            autofillStore = autofillStore,
            autofilledListeners = FakePluginPoint(),
        )
    }

    private class FakePluginPoint : PluginPoint<DataAutofilledListener> {
        override fun getPlugins(): Collection<DataAutofilledListener> {
            return emptyList()
        }
    }
}
