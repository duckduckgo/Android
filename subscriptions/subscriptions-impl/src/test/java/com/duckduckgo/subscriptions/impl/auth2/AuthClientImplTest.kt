package com.duckduckgo.subscriptions.impl.auth2

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class AuthClientImplTest {

    private val authService: AuthService = mock()
    private val authClient = AuthClientImpl(authService)

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
}
