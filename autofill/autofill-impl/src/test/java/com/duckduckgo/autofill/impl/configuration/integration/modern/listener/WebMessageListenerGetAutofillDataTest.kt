package com.duckduckgo.autofill.impl.configuration.integration.modern.listener

import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.InternalAutofillCapabilityChecker
import com.duckduckgo.autofill.impl.deduper.AutofillLoginDeduplicator
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillDataRequest
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.USERNAME
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillTriggerType.USER_INITIATED
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WebMessageListenerGetAutofillDataTest {

    private val shareableCredentials: ShareableCredentials = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val autofillCapabilityChecker: InternalAutofillCapabilityChecker = mock()
    private val requestParser: AutofillRequestParser = mock()
    private val webMessageReply: JavaScriptReplyProxy = mock()
    private val testCallback = TestWebMessageListenerCallback()
    private val mockWebView: WebView = mock()
    private val responseWriter: AutofillResponseWriter = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee = WebMessageListenerGetAutofillData(
        appCoroutineScope = coroutineTestRule.testScope,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillCapabilityChecker = autofillCapabilityChecker,
        requestParser = requestParser,
        autofillStore = autofillStore,
        shareableCredentials = shareableCredentials,
        loginDeduplicator = NoopDeduplicator(),
        responseWriter = responseWriter,
    )

    @Before
    fun setup() = runTest {
        testee.callback = testCallback

        whenever(mockWebView.url).thenReturn(REQUEST_URL)
        whenever(autofillCapabilityChecker.isAutofillEnabledByConfiguration(any())).thenReturn(true)
        whenever(autofillCapabilityChecker.canInjectCredentialsToWebView(any())).thenReturn(true)
        whenever(autofillCapabilityChecker.canSaveCredentialsFromWebView(any())).thenReturn(true)
        whenever(shareableCredentials.shareableCredentials(any())).thenReturn(emptyList())
        whenever(responseWriter.generateEmptyResponseGetAutofillData()).thenReturn("")
    }

    @Test
    fun whenGettingSavedPasswordsNoCredentialsAvailableThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenGettingSavedPasswordsWithCredentialsAvailableThenCredentialsAvailableCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(listOf(LoginCredentials(0, "example.com", "username", "password")))
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
    }

    @Test
    fun whenGettingSavedPasswordsWithCredentialsAvailableWithNullUsernameUsernameConvertedToEmptyString() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = "foo"),
                loginCredential(username = null, password = "bar"),
                loginCredential(username = "foo", password = "bar"),
            ),
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()

        // ensure the list of credentials now has two entries with empty string username (one for each null username)
        assertCredentialsContains({ it.username }, "", "")
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypeUsernameAndNoEntriesThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypeUsernameAndNoEntriesWithAUsernameThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = "foo"),
                loginCredential(username = null, password = "bar"),
            ),
        )
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypeUsernameAndSingleEntryWithAUsernameThenCredentialsAvailableCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = "foo"),
                loginCredential(username = null, password = "bar"),
                loginCredential(username = "foo", password = "bar"),
            ),
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.username }, "foo")
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypeUsernameAndMultipleEntriesWithAUsernameThenCorrectCallbackInvoked() = runTest {
        setupRequestForSubTypeUsername()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = "foo"),
                loginCredential(username = "username1", password = "bar"),
                loginCredential(username = null, password = "bar"),
                loginCredential(username = null, password = "bar"),
                loginCredential(username = "username2", password = null),
            ),
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.username }, "username1", "username2")
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypePasswordAndNoEntriesThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypePasswordAndNoEntriesWithAPasswordThenNoCredentialsCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = "foo", password = null),
                loginCredential(username = "bar", password = null),
            ),
        )
        initiateGetAutofillDataRequest()
        assertCredentialsUnavailable()
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypePasswordAndSingleEntryWithAPasswordThenCredentialsAvailableCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = null),
                loginCredential(username = "foobar", password = null),
                loginCredential(username = "foo", password = "bar"),
            ),
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.password }, "bar")
    }

    @Test
    fun whenGettingSavedPasswordsAndRequestSpecifiesSubtypePasswordAndMultipleEntriesWithAPasswordThenCorrectCallbackInvoked() = runTest {
        setupRequestForSubTypePassword()
        whenever(autofillStore.getCredentials(any())).thenReturn(
            listOf(
                loginCredential(username = null, password = null),
                loginCredential(username = "username2", password = null),
                loginCredential(username = "username1", password = "password1"),
                loginCredential(username = null, password = "password2"),
                loginCredential(username = null, password = "password3"),

            ),
        )
        initiateGetAutofillDataRequest()
        assertCredentialsAvailable()
        assertCredentialsContains({ it.password }, "password1", "password2", "password3")
    }

    private fun assertCredentialsUnavailable() {
        verify(responseWriter).generateEmptyResponseGetAutofillData()
        verify(webMessageReply).postMessage(any<String>())
    }

    private fun assertCredentialsAvailable() {
        assertNotNull("Callback has not been called", testCallback.credentialsAvailableToInject)
        assertTrue(testCallback.credentialsAvailableToInject!!)
    }

    private fun assertCredentialsContains(
        property: (LoginCredentials) -> String?,
        vararg expected: String?,
    ) {
        val numberExpected = expected.size
        val numberMatched = testCallback.credentialsToInject?.count { expected.contains(property(it)) }
        Assert.assertEquals("Wrong number of matched properties. Expected $numberExpected but found $numberMatched", numberExpected, numberMatched)
    }

    private fun initiateGetAutofillDataRequest(isMainFrame: Boolean = true) {
        testee.onPostMessage(
            webView = mockWebView,
            message = WebMessageCompat(""),
            sourceOrigin = REQUEST_ORIGIN,
            isMainFrame = isMainFrame,
            reply = webMessageReply,
        )
    }

    private fun loginCredential(
        username: String?,
        password: String?,
    ) = LoginCredentials(0, "example.com", username, password)

    private suspend fun setupRequestForSubTypeUsername() {
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(
            Result.success(AutofillDataRequest(CREDENTIALS, USERNAME, USER_INITIATED, null)),
        )
    }

    private suspend fun setupRequestForSubTypePassword() {
        whenever(requestParser.parseAutofillDataRequest(any())).thenReturn(
            Result.success(AutofillDataRequest(CREDENTIALS, PASSWORD, USER_INITIATED, null)),
        )
    }

    private class NoopDeduplicator : AutofillLoginDeduplicator {
        override fun deduplicate(originalUrl: String, logins: List<LoginCredentials>): List<LoginCredentials> = logins
    }

    companion object {
        private const val REQUEST_URL = "https://example.com"
        private val REQUEST_ORIGIN = REQUEST_URL.toUri()
    }
}
