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

package com.duckduckgo.autofill

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.autofill.JavascriptInjector
import com.duckduckgo.autofill.InlineBrowserAutofillTest.FakeAutofillJavascriptInterface.Actions.CredentialsInjected
import com.duckduckgo.autofill.InlineBrowserAutofillTest.FakeAutofillJavascriptInterface.Actions.GetAutoFillData
import com.duckduckgo.autofill.InlineBrowserAutofillTest.FakeAutofillJavascriptInterface.Actions.NoCredentialsInjected
import com.duckduckgo.autofill.domain.app.LoginCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class InlineBrowserAutofillTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: InlineBrowserAutofill
    private lateinit var autofillJavascriptInterface: FakeAutofillJavascriptInterface
    @Mock
    private lateinit var javascriptInjector: JavascriptInjector
    private lateinit var testWebView: WebView
    private val coroutineScope: CoroutineScope = TestScope()
    private val testCallback = object : Callback {
        override fun onCredentialsAvailableToInject(credentials: List<LoginCredentials>) {
        }

        override fun onCredentialsAvailableToSave(
            currentUrl: String,
            credentials: LoginCredentials
        ) {
        }

        override fun noCredentialsAvailable(originalUrl: String) {
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        autofillJavascriptInterface = FakeAutofillJavascriptInterface()
        testWebView = WebView(getApplicationContext())
        testee = InlineBrowserAutofill(
            autofillJavascriptInterface,
            javascriptInjector,
            coroutineScope
        )
    }

    @Test
    fun whenRemoveJsInterfaceThenRemoveReferenceToWebview() {
        testee.addJsInterface(testWebView, testCallback)

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
            password = "test123"
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

        override suspend fun getRuntimeConfiguration(
            rawJs: String,
            url: String?
        ): String = "fakeResult"

        override fun injectCredentials(credentials: LoginCredentials) {
            lastAction = CredentialsInjected(credentials)
        }

        override fun injectNoCredentials() {
            lastAction = NoCredentialsInjected
        }

        override var callback: Callback? = null
        override var webView: WebView? = null
    }
}
