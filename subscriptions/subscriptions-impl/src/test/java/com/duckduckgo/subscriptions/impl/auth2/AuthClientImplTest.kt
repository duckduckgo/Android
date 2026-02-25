package com.duckduckgo.subscriptions.impl.auth2

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class AuthClientImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val authService: AuthService = mock()
    private val appBuildConfig: AppBuildConfig = mock { config ->
        whenever(config.applicationId).thenReturn("com.duckduckgo.android")
    }
    private val timeProvider = FakeTimeProvider()
    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)

    private val authClient = AuthClientImpl(
        authService = authService,
        appBuildConfig = appBuildConfig,
        timeProvider = timeProvider,
        privacyProFeature = { privacyProFeature },
        dispatchers = coroutinesTestRule.testDispatcherProvider,
    )

    @Test
    fun `when authorize success then returns sessionId parsed from Set-Cookie header`() = runTest {
        val sessionId = "fake auth session id"
        val codeChallenge = "fake code challenge"

        val mockResponse: Response<Unit> = mock {
            on { code() } doReturn 302
            on { headers() } doReturn Headers.headersOf("Set-Cookie", "ddg_auth_session_id=$sessionId; Path=/")
            on { isSuccessful } doReturn false // Retrofit treats non-2xx responses as unsuccessful
        }

        whenever(authService.authorize(any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse)

        val receivedSessionId = authClient.authorize(codeChallenge)
        assertEquals(sessionId, receivedSessionId)

        verify(authService).authorize(
            responseType = "code",
            codeChallenge = codeChallenge,
            codeChallengeMethod = "S256",
            clientId = "f4311287-0121-40e6-8bbd-85c36daf1837",
            redirectUri = "com.duckduckgo:/authcb",
            scope = "privacypro",
        )
    }

    @Test
    fun `when authorize responds with non-302 code then throws HttpException`() = runTest {
        val errorResponse = Response.error<Unit>(
            400,
            "{}".toResponseBody("application/json".toMediaTypeOrNull()),
        )

        whenever(authService.authorize(any(), any(), any(), any(), any(), any()))
            .thenReturn(errorResponse)

        try {
            authClient.authorize("fake code challenge")
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
        }
    }

    @Test
    fun `when createAccount success then returns authorization code parsed from Location header`() = runTest {
        val authorizationCode = "fake_authorization_code"

        val mockResponse: Response<Unit> = mock {
            on { code() } doReturn 302
            on { headers() } doReturn Headers.headersOf("Location", "https://example.com?code=$authorizationCode")
            on { isSuccessful } doReturn false // Retrofit treats non-2xx responses as unsuccessful
        }

        whenever(authService.createAccount(any())).thenReturn(mockResponse)

        assertEquals(authorizationCode, authClient.createAccount("fake auth session id"))
    }

    @Test
    fun `when createAccount HTTP error then throws HttpException`() = runTest {
        val errorResponse = Response.error<Unit>(
            400,
            "{}".toResponseBody("application/json".toMediaTypeOrNull()),
        )

        whenever(authService.createAccount(any())).thenReturn(errorResponse)

        try {
            authClient.createAccount("fake auth session id")
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
        }
    }

    @Test
    fun `when getTokens success then returns AuthenticationCredentials`() = runTest {
        val sessionId = "fake auth session id"
        val authorizationCode = "fake authorization code"
        val codeVerifier = "fake code verifier"
        val accessToken = "fake access token"
        val refreshToken = "fake refresh token"

        whenever(authService.token(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(TokensResponse(accessToken, refreshToken))

        val tokens = authClient.getTokens(sessionId, authorizationCode, codeVerifier)

        assertEquals(accessToken, tokens.accessToken)
        assertEquals(refreshToken, tokens.refreshToken)

        verify(authService).token(
            grantType = "authorization_code",
            clientId = "f4311287-0121-40e6-8bbd-85c36daf1837",
            codeVerifier = codeVerifier,
            code = authorizationCode,
            redirectUri = "com.duckduckgo:/authcb",
            refreshToken = null,
        )
    }

    @Test
    fun `when getTokens with refresh token then return AuthenticationCredentials`() = runTest {
        val accessToken = "fake access token"
        val refreshToken = "fake refresh token"

        whenever(authService.token(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(TokensResponse(accessToken, refreshToken))

        val credentials = authClient.getTokens("fake refresh token")

        assertEquals(accessToken, credentials.accessToken)
        assertEquals(refreshToken, credentials.refreshToken)

        verify(authService).token(
            grantType = "refresh_token",
            clientId = "f4311287-0121-40e6-8bbd-85c36daf1837",
            codeVerifier = null,
            code = null,
            redirectUri = null,
            refreshToken = refreshToken,
        )
    }

    @Test
    fun `when getJwks then return JWK set in JSON format`() = runTest {
        val jwks = """{"keys": [{"kty": "RSA", "kid": "fakeKeyId"}]}"""
        val jwksResponse = jwks.toResponseBody("application/json".toMediaTypeOrNull())

        whenever(authService.jwks()).thenReturn(jwksResponse)

        assertEquals(jwks, authClient.getJwks())
    }

    @Test
    fun `when login success then returns authorization code`() = runTest {
        val sessionId = "fake auth session id"
        val authorizationCode = "fake_authorization_code"
        val signature = "fake signature"
        val googleSignedData = "fake signed data"

        val mockResponse: Response<Unit> = mock {
            on { code() } doReturn 302
            on { headers() } doReturn Headers.headersOf("Location", "https://example.com?code=$authorizationCode")
            on { isSuccessful } doReturn false // Retrofit treats non-2xx responses as unsuccessful
        }

        whenever(authService.login(any(), any())).thenReturn(mockResponse)

        val storeLoginResponse = authClient.storeLogin(sessionId, signature, googleSignedData)

        assertEquals(authorizationCode, storeLoginResponse)

        verify(authService).login(
            cookie = "ddg_auth_session_id=$sessionId",
            body = StoreLoginBody(
                method = "signature",
                signature = signature,
                source = "google_play_store",
                googleSignedData = googleSignedData,
                googlePackageName = appBuildConfig.applicationId,
            ),
        )
    }

    @Test
    fun `when login HTTP error then throws HttpException`() = runTest {
        val errorResponse = Response.error<Unit>(
            400,
            "{}".toResponseBody("application/json".toMediaTypeOrNull()),
        )

        whenever(authService.login(any(), any())).thenReturn(errorResponse)

        try {
            authClient.storeLogin("fake auth session id", "fake signature", "fake signed data")
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
        }
    }

    @Test
    fun `when exchange token success then returns authorization code`() = runTest {
        val sessionId = "fake auth session id"
        val authorizationCode = "fake_authorization_code"
        val accessTokenV1 = "fake v1 access token"

        val mockResponse: Response<Unit> = mock {
            on { code() } doReturn 302
            on { headers() } doReturn Headers.headersOf("Location", "https://example.com?code=$authorizationCode")
            on { isSuccessful } doReturn false // Retrofit treats non-2xx responses as unsuccessful
        }

        whenever(authService.exchange(any(), any())).thenReturn(mockResponse)

        val response = authClient.exchangeV1AccessToken(accessTokenV1, sessionId)

        assertEquals(authorizationCode, response)

        verify(authService).exchange(
            authorization = "Bearer $accessTokenV1",
            cookie = "ddg_auth_session_id=$sessionId",
        )
    }

    @Test
    fun `when exchange token HTTP error then throws HttpException`() = runTest {
        val errorResponse = Response.error<Unit>(
            400,
            "{}".toResponseBody("application/json".toMediaTypeOrNull()),
        )

        whenever(authService.exchange(any(), any())).thenReturn(errorResponse)

        try {
            authClient.exchangeV1AccessToken("fake v1 access token", "fake auth session id")
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
        }
    }

    @Test
    fun `when logout error then does not throw any exception`() = runTest {
        val errorResponse = Response.error<Unit>(
            400,
            "{}".toResponseBody("application/json".toMediaTypeOrNull()),
        )

        whenever(authService.logout(any())).thenThrow(HttpException(errorResponse))

        authClient.tryLogout("fake v2 access token")
    }

    @Test
    fun `when JWKS not cached then fetches from network`() = runTest {
        val jwksJson = """{"keys": [{"kty": "RSA", "kid": "networkKey"}]}"""
        val responseBody = jwksJson.toResponseBody("application/json".toMediaTypeOrNull())

        whenever(authService.jwks()).thenReturn(responseBody)

        val result = authClient.getJwks()

        assertEquals(jwksJson, result)
        verify(authService).jwks()
    }

    @Test
    fun `when JWKS is cached and not expired then returns cached value`() = runTest {
        val jwksJson = """{"keys": [{"kty": "RSA", "kid": "cachedKey"}]}"""
        val responseBody = jwksJson.toResponseBody("application/json".toMediaTypeOrNull())

        whenever(authService.jwks()).thenReturn(responseBody)

        // Initial request
        val first = authClient.getJwks()
        assertEquals(jwksJson, first)

        // Advance time just before expiration
        timeProvider.currentTime += Duration.ofMinutes(59)

        val second = authClient.getJwks()
        assertEquals(jwksJson, second)

        // Verify network call happened only once
        verify(authService).jwks()
    }

    @Test
    fun `when JWKS cache is expired then fetches new value`() = runTest {
        val oldJwks = """{"keys": [{"kty": "RSA", "kid": "oldKey"}]}"""
        val newJwks = """{"keys": [{"kty": "RSA", "kid": "newKey"}]}"""

        whenever(authService.jwks())
            .thenReturn(oldJwks.toResponseBody("application/json".toMediaTypeOrNull()))
            .thenReturn(newJwks.toResponseBody("application/json".toMediaTypeOrNull()))

        // Initial call → old value cached
        val first = authClient.getJwks()
        assertEquals(oldJwks, first)

        // Advance time past expiration
        timeProvider.currentTime += Duration.ofMinutes(61)

        // Call again → should return new JWKS
        val second = authClient.getJwks()
        assertEquals(newJwks, second)

        verify(authService, times(2)).jwks()
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `when JWKS cache is disabled then always fetches from network`() = runTest {
        privacyProFeature.authApiV2JwksCache().setRawStoredState(State(false))

        val jwks1 = """{"keys": [{"kty": "RSA", "kid": "key1"}]}"""
        val jwks2 = """{"keys": [{"kty": "RSA", "kid": "key2"}]}"""

        whenever(authService.jwks())
            .thenReturn(jwks1.toResponseBody("application/json".toMediaTypeOrNull()))
            .thenReturn(jwks2.toResponseBody("application/json".toMediaTypeOrNull()))

        val first = authClient.getJwks()
        val second = authClient.getJwks()

        assertEquals(jwks1, first)
        assertEquals(jwks2, second)

        verify(authService, times(2)).jwks()
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var currentTime: Instant = Instant.parse("2024-10-28T00:00:00Z")

        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
    }
}
