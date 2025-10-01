package com.duckduckgo.subscriptions.impl.auth

import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor.Chain
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import retrofit2.Invocation
import java.io.IOException
import java.lang.reflect.Method

class AuthInterceptorTest {

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val interceptor = AuthInterceptor(subscriptionsManager)

    @Test
    fun `when @AuthRequired annotation is present then authorization header is added`() = runTest {
        val token = "fake_token"
        whenever(subscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Success(token))
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringAuthentication")

        val response = interceptor.intercept(fakeChain(serviceMethod = annotatedMethod))

        assertEquals("Bearer fake_token", response.request.header("Authorization"))
    }

    @Test
    fun `when @AuthRequired annotation is not present then authorization header not is added`() {
        val nonAnnotatedMethod = FakeApiService::class.java.getMethod("endpointNotRequiringAuthentication")

        val response = interceptor.intercept(fakeChain(serviceMethod = nonAnnotatedMethod))

        assertEquals(null, response.request.header("Authorization"))
        verifyNoInteractions(subscriptionsManager)
    }

    @Test(expected = IOException::class)
    fun `when @AuthRequired annotation is present and token is not available then IOException is thrown`() = runTest {
        whenever(subscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Failure("unauthenticated"))
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringAuthentication")

        interceptor.intercept(fakeChain(serviceMethod = annotatedMethod))
    }

    private fun fakeChain(url: String = "https://example.com", serviceMethod: Method): Chain {
        return object : FakeChain(url) {
            override fun request(): Request {
                return Request.Builder()
                    .url(url)
                    .tag(Invocation::class.java, Invocation.of(serviceMethod, emptyList<Unit>()))
                    .build()
            }
        }
    }

    private interface FakeApiService {
        @AuthRequired
        fun endpointRequiringAuthentication()
        fun endpointNotRequiringAuthentication()
    }
}
