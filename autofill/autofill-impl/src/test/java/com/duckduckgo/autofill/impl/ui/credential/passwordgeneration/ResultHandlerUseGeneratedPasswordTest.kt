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

package com.duckduckgo.autofill.impl.ui.credential.passwordgeneration

import android.os.Bundle
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.NoMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UsernameMatchDifferentPassword
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog.Companion.KEY_ACCEPTED
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog.Companion.KEY_PASSWORD
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog.Companion.KEY_URL
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog.Companion.KEY_USERNAME
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.engagement.DataAutofilledListener
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult.BackFillNotSupported
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult.BackFillSupported
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
class ResultHandlerUseGeneratedPasswordTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val autofillStore: InternalAutofillStore = mock()
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor = mock()
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector = mock()
    private val callback: AutofillEventListener = mock()
    private val usernameBackFiller: UsernameBackFiller = mock()
    private val webView: WebView = mock()

    private val testee = ResultHandlerUseGeneratedPassword(
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillStore = autofillStore,
        appCoroutineScope = coroutineTestRule.testScope,
        autoSavedLoginsMonitor = autoSavedLoginsMonitor,
        existingCredentialMatchDetector = existingCredentialMatchDetector,
        autofilledListeners = FakePluginPoint(),
        usernameBackFiller = usernameBackFiller,
    )

    @Before
    fun setup() = runTest {
        whenever(
            existingCredentialMatchDetector.determine(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(NoMatch)

        whenever(usernameBackFiller.isBackFillingUsernameSupported(anyOrNull(), any())).thenReturn(BackFillNotSupported)
    }

    @Test
    fun whenUserRejectedToUsePasswordThenCorrectCallbackInvoked() = runTest {
        val bundle = bundle("example.com", acceptedGeneratedPassword = false)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onRejectGeneratedPassword("example.com")
    }

    @Test
    fun whenUserAcceptedToUsePasswordNoAutoLoginInThenCorrectCallbackInvoked() = runTest {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(null)
        val bundle = bundle("example.com", acceptedGeneratedPassword = true, password = "pw")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onAcceptGeneratedPassword("example.com")
    }

    @Test
    fun whenUserAcceptedToUsePasswordNoAutoLoginInThenCredentialIsSaved() = runTest {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(null)
        val bundle = bundle("example.com", acceptedGeneratedPassword = true, password = "pw")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore).saveCredentials(any(), any())
    }

    @Test
    fun whenUserAcceptedToUsePasswordNoAutoLoginInThenAutoLoginIdUpdated() = runTest {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(null)
        whenever(autofillStore.saveCredentials(any(), any())).thenReturn(aLogin(1))

        val bundle = bundle("example.com", acceptedGeneratedPassword = true, password = "pw")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autoSavedLoginsMonitor).setAutoSavedLoginId(any(), any())
    }

    @Test
    fun whenUserAcceptedToUsePasswordNoAutoLoginNoUsernameProvidedButUsernameBackFilledThenBackFilledUsernameUsed() = runTest {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(null)
        "from-backfill".useBackFilledUsername()
        val bundle = bundle("example.com", acceptedGeneratedPassword = true, password = "pw")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore).saveCredentials(any(), eq(LoginCredentials(domain = "example.com", username = "from-backfill", password = "pw")))
    }

    @Test
    fun whenUserAcceptedToUsePasswordAutoLoginButNothingToUpdateThenCredentialIsSaved() = runTest {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(null)
        val bundle = bundle("example.com", acceptedGeneratedPassword = true, username = "from-js", password = "pw")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore).saveCredentials(any(), eq(LoginCredentials(domain = "example.com", username = "from-js", password = "pw")))
    }

    fun whenUserAcceptedToUsePasswordNoAutoLoginUsernameProvidedThenBackFilledUsernameNotUsed() = runTest {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(null)
        val bundle = bundle("example.com", acceptedGeneratedPassword = true, username = "from-js", password = "pw")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore).saveCredentials(any(), eq(LoginCredentials(domain = "example.com", username = "from-js", password = "pw")))
    }

    @Test
    fun whenUserAcceptedToUsePasswordAutoLoginIdNotFoundThenLoginSaved() = runTest {
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(1)
        whenever(autofillStore.getCredentialsWithId(1)).thenReturn(null)

        val bundle = bundle("example.com", acceptedGeneratedPassword = true, password = "pw")
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore).saveCredentials(any(), any())
    }

    @Test
    fun whenUserAcceptedToUsePasswordAutoLoginIdFoundAndAlreadyMatchesThenNothingSavedOrUpdated() = runTest {
        val testLogin = aLogin(id = 1)
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(1)
        whenever(autofillStore.getCredentialsWithId(1)).thenReturn(testLogin)

        val bundle = bundle(
            "example.com",
            acceptedGeneratedPassword = true,
            username = testLogin.username,
            password = testLogin.password,
        )
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore, never()).saveCredentials(any(), any())
        verify(autofillStore, never()).updateCredentials(any(), any())
    }

    @Test
    fun whenUserAcceptedToUsePasswordAutoLoginIdFoundAndDoesNotMatchUsernameThenUpdated() = runTest {
        val testLogin = aLogin(id = 1)
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(1)
        whenever(autofillStore.getCredentialsWithId(1)).thenReturn(testLogin)

        val bundle = bundle(
            "example.com",
            acceptedGeneratedPassword = true,
            username = "different-username",
            password = testLogin.password,
        )
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore, never()).saveCredentials(any(), any())
        verify(autofillStore).updateCredentials(any(), any())
    }

    @Test
    fun whenUserAcceptedToUsePasswordAutoLoginIdFoundAndDoesNotMatchPasswordThenUpdated() = runTest {
        val testLogin = aLogin(id = 1)
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(1)
        whenever(autofillStore.getCredentialsWithId(1)).thenReturn(testLogin)

        val bundle = bundle(
            "example.com",
            acceptedGeneratedPassword = true,
            username = testLogin.username,
            password = "different-password",
        )
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore, never()).saveCredentials(any(), any())
        verify(autofillStore).updateCredentials(any(), any())
    }

    @Test
    fun whenUserAcceptedToUsePasswordButPasswordIsNullThenCorrectCallbackNotInvoked() = runTest {
        val bundle = bundle("example.com", acceptedGeneratedPassword = true, password = null)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback, never()).onAcceptGeneratedPassword("example.com")
    }

    @Test
    fun whenUserAcceptedToUsePasswordWhenAlreadyAMatchingUsernameDifferentPasswordSavedThenNoAction() = runTest {
        val bundle = bundle("example.com", acceptedGeneratedPassword = true, password = "pw")
        whenever(autoSavedLoginsMonitor.getAutoSavedLoginId(any())).thenReturn(null)
        whenever(existingCredentialMatchDetector.determine(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(UsernameMatchDifferentPassword)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(autofillStore, never()).saveCredentials(any(), any())
        verify(autofillStore, never()).updateCredentials(any(), any())
    }

    @Test
    fun whenBundleMissingUrlThenCallbackNotInvoked() = runTest {
        val bundle = bundle(url = null, acceptedGeneratedPassword = true)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verifyNoInteractions(callback)
    }

    private fun bundle(
        url: String?,
        acceptedGeneratedPassword: Boolean,
        username: String? = null,
        password: String? = null,
    ): Bundle {
        return Bundle().also {
            it.putString(KEY_URL, url)
            it.putBoolean(KEY_ACCEPTED, acceptedGeneratedPassword)
            it.putString(KEY_USERNAME, username)
            it.putString(KEY_PASSWORD, password)
        }
    }

    private suspend fun String.useBackFilledUsername() {
        whenever(usernameBackFiller.isBackFillingUsernameSupported(anyOrNull(), anyOrNull())).thenReturn(BackFillSupported(this))
    }

    private fun aLogin(id: Long = 0): LoginCredentials {
        return LoginCredentials(id = id, domain = "example.com", username = "user", password = "pw")
    }

    private class FakePluginPoint : PluginPoint<DataAutofilledListener> {
        override fun getPlugins(): Collection<DataAutofilledListener> = emptyList()
    }
}
