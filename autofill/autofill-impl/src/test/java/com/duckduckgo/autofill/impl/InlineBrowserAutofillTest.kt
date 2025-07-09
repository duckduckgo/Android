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

package com.duckduckgo.autofill.impl

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillPrompt
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.EmailProtectionInContextSignupFlowListener
import com.duckduckgo.autofill.api.EmailProtectionUserPromptListener
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.InlineBrowserAutofillTest.FakeAutofillJavascriptInterface.Actions.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class InlineBrowserAutofillTest {

    private lateinit var testee: InlineBrowserAutofill
    private val automaticSavedLoginsMonitor: AutomaticSavedLoginsMonitor = mock()
    private lateinit var autofillJavascriptInterface: FakeAutofillJavascriptInterface

    private lateinit var testWebView: WebView

    private val emailProtectionInContextCallback: EmailProtectionUserPromptListener = mock()
    private val emailProtectionInContextSignupFlowCallback: EmailProtectionInContextSignupFlowListener = mock()

    private val testCallback = object : Callback {
        override suspend fun onCredentialsAvailableToInject(
            originalUrl: String,
            credentials: List<LoginCredentials>,
            triggerType: LoginTriggerType,
        ) {
        }

        override suspend fun onCredentialsAvailableToSave(
            currentUrl: String,
            credentials: LoginCredentials,
        ) {
        }

        override suspend fun onGeneratedPasswordAvailableToUse(
            originalUrl: String,
            username: String?,
            generatedPassword: String,
        ) {
        }

        override fun noCredentialsAvailable(originalUrl: String) {
        }

        override fun onCredentialsSaved(savedCredentials: LoginCredentials) {
        }

        override suspend fun showAutofillDialgo(event: AutofillPrompt) {
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        autofillJavascriptInterface = FakeAutofillJavascriptInterface()
        testWebView = WebView(getApplicationContext())
        testee = InlineBrowserAutofill(autofillInterface = autofillJavascriptInterface, autoSavedLoginsMonitor = automaticSavedLoginsMonitor)
    }

    @Test
    fun whenRemoveJsInterfaceThenRemoveReferenceToWebview() {
        testee.addJsInterface(testWebView, testCallback, emailProtectionInContextCallback, emailProtectionInContextSignupFlowCallback, "tabId")

        assertNotNull(autofillJavascriptInterface.webView)

        testee.removeJsInterface()

        assertNull(autofillJavascriptInterface.webView)
    }

    @Test
    fun whenInjectCredentialsNullThenInterfaceInjectNoCredentials() {
        testee.injectCredentials(null)

        assertEquals(NoCredentialsInjected, autofillJavascriptInterface.lastAction)
    }

    @Test
    fun whenInjectCredentialsThenInterfaceCredentialsInjected() {
        val toInject = LoginCredentials(
            id = 1,
            domain = "hello.com",
            username = "test",
            password = "test123",
        )
        testee.injectCredentials(toInject)

        assertEquals(CredentialsInjected(toInject), autofillJavascriptInterface.lastAction)
    }

    class FakeAutofillJavascriptInterface : AutofillJavascriptInterface {
        sealed class Actions {
            data class GetAutoFillData(val requestString: String) : Actions()
            data class CredentialsInjected(val credentials: LoginCredentials) : Actions()
            object NoCredentialsInjected : Actions()
        }

        var lastAction: Actions? = null

        override fun getAutofillData(requestString: String) {
            lastAction = GetAutoFillData(requestString)
        }

        override fun injectCredentials(credentials: LoginCredentials) {
            lastAction = CredentialsInjected(credentials)
        }

        override fun injectNoCredentials() {
            lastAction = NoCredentialsInjected
        }

        override fun closeEmailProtectionTab(data: String) {
        }

        override fun onNewAutofillDataAvailable(url: String?) {
        }

        override fun getIncontextSignupDismissedAt(data: String) {
        }

        override fun cancelRetrievingStoredLogins() {
        }

        override fun acceptGeneratedPassword() {
        }

        override fun rejectGeneratedPassword() {
        }

        override fun inContextEmailProtectionFlowFinished() {
        }

        override var callback: Callback? = null
        override var emailProtectionInContextCallback: EmailProtectionUserPromptListener? = null
        override var emailProtectionInContextSignupFlowCallback: EmailProtectionInContextSignupFlowListener? = null
        override var webView: WebView? = null
        override var autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor? = null
        override var tabId: String? = null
    }
}
