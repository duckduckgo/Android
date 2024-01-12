package com.duckduckgo.subscriptions.impl.repository

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RealAuthRepositoryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authStore = FakeAuthDataStore()
    private val authRepository: AuthRepository = RealAuthRepository(authStore)

    @Test
    fun whenIsAuthenticatedAndNoAccessTokenThenReturnFalse() {
        authStore.authToken = "authToken"
        assertFalse(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenIsAuthenticatedAndNoAuthTokenThenReturnFalse() {
        authStore.accessToken = "accessToken"
        assertFalse(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenIsAuthenticatedAndNoAuthTokenAndAccessTokenThenReturnFalse() {
        assertFalse(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenIsAuthenticatedThenReturnTrue() {
        authStore.authToken = "authToken"
        authStore.accessToken = "accessToken"
        assertTrue(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenSignOutThenClearData() = runTest {
        authStore.accessToken = "accessToken"
        authStore.authToken = "accessToken"
        authStore.platform = "android"
        authStore.email = "email@duck.com"
        authStore.externalId = "externalId"
        authStore.expiresOrRenewsAt = 1234L

        authRepository.signOut()

        assertNull(authStore.accessToken)
        assertNull(authStore.authToken)
        assertNull(authStore.platform)
        assertNull(authStore.email)
        assertNull(authStore.externalId)
        assertEquals(0L, authStore.expiresOrRenewsAt)
    }

    @Test
    fun whenClearSubscriptionDataThenClearData() = runTest {
        authStore.platform = "android"
        authStore.expiresOrRenewsAt = 1234L

        authRepository.clearSubscriptionData()

        assertNull(authStore.platform)
        assertEquals(0L, authStore.expiresOrRenewsAt)
    }

    @Test
    fun whenAuthenticateThenSetData() = runTest {
        assertNull(authStore.accessToken)
        assertNull(authStore.authToken)
        assertNull(authStore.email)
        assertNull(authStore.externalId)

        authRepository.authenticate(authToken = "authToken", accessToken = "accessToken", email = "email", externalId = "externalId")

        assertEquals("authToken", authStore.authToken)
        assertEquals("accessToken", authStore.accessToken)
        assertEquals("email", authStore.email)
        assertEquals("externalId", authStore.externalId)
    }

    @Test
    fun whenTokensThenReturnTokens() {
        var tokens = authRepository.tokens()
        assertNull(tokens.authToken)
        assertNull(tokens.accessToken)

        authStore.accessToken = "accessToken"
        authStore.authToken = "authToken"

        tokens = authRepository.tokens()
        assertEquals("authToken", tokens.authToken)
        assertEquals("accessToken", tokens.accessToken)
    }

    @Test
    fun whenSaveSubscriptionDataThenStoreSubscriptionValues() = runTest {
        assertNull(authStore.platform)
        assertEquals(0L, authStore.expiresOrRenewsAt)
        authRepository.saveSubscriptionData("android", 1234L)
        assertEquals("android", authStore.platform)
        assertEquals(1234L, authStore.expiresOrRenewsAt)
    }
}
