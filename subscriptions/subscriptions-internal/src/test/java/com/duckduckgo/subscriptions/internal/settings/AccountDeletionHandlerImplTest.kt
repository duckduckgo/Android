package com.duckduckgo.subscriptions.internal.settings

import com.duckduckgo.subscriptions.impl.AuthTokenResult
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.services.AuthService
import com.duckduckgo.subscriptions.impl.services.DeleteAccountResponse
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

class AccountDeletionHandlerImplTest {

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val authService: AuthService = mock()

    private val subject = AccountDeletionHandlerImpl(subscriptionsManager, authService)

    @Test
    fun whenDeleteAccountIfUserAuthenticatedAndValidTokenThenReturnTrue() = runTest {
        givenUserIsAuthenticated()
        givenDeleteAccountSucceeds()

        val accountDeleted = subject.deleteAccountAndSignOut()

        assertTrue(accountDeleted)
        verify(authService).delete(eq("Bearer token"))
        verify(subscriptionsManager).signOut()
    }

    @Test
    fun whenDeleteAccountIfUserNotAuthenticatedThenReturnFalse() = runTest {
        givenUserIsNotAuthenticated()
        givenDeleteAccountFails()

        val accountDeleted = subject.deleteAccountAndSignOut()

        assertFalse(accountDeleted)
        verify(subscriptionsManager).getAuthToken()
        verify(subscriptionsManager, never()).signOut()
        verify(authService, never()).delete(any())
    }

    @Test
    fun whenDeleteAccountFailsThenReturnFalse() = runTest {
        givenUserIsAuthenticated()
        givenDeleteAccountFails()

        val accountDeleted = subject.deleteAccountAndSignOut()

        assertFalse(accountDeleted)
        verify(subscriptionsManager).getAuthToken()
        verify(authService).delete(eq("Bearer token"))
        verify(subscriptionsManager, never()).signOut()
    }

    private suspend fun givenUserIsAuthenticated() {
        whenever(subscriptionsManager.getAuthToken()).thenReturn(AuthTokenResult.Success("token"))
    }

    private suspend fun givenUserIsNotAuthenticated() {
        whenever(subscriptionsManager.getAuthToken()).thenReturn(null)
    }

    private suspend fun givenDeleteAccountSucceeds() {
        whenever(authService.delete(any())).thenReturn(DeleteAccountResponse(status = "deleted"))
    }

    private suspend fun givenDeleteAccountFails() {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.delete(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }
}
