package com.duckduckgo.subscriptions.impl.repository

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.WAITING
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RealAuthRepositoryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authStore = FakeSubscriptionsDataStore()
    private val authRepository: AuthRepository = RealAuthRepository(authStore, coroutineRule.testDispatcherProvider)

    @Test
    fun whenIsAuthenticatedAndNoAccessTokenThenReturnFalse() = runTest {
        authStore.authToken = "authToken"
        assertFalse(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenIsAuthenticatedAndNoAuthTokenThenReturnFalse() = runTest {
        authStore.accessToken = "accessToken"
        assertFalse(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenIsAuthenticatedAndNoAuthTokenAndAccessTokenThenReturnFalse() = runTest {
        assertFalse(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenIsAuthenticatedThenReturnTrue() = runTest {
        authStore.authToken = "authToken"
        authStore.accessToken = "accessToken"
        assertTrue(authRepository.isUserAuthenticated())
    }

    @Test
    fun whenClearAccountThenClearData() = runTest {
        authStore.email = "email@duck.com"
        authStore.externalId = "externalId"
        authStore.authToken = "authToken"
        authStore.accessToken = "accessToken"

        authRepository.clearAccount()

        assertNull(authStore.accessToken)
        assertNull(authStore.authToken)
        assertNull(authStore.externalId)
        assertNull(authStore.email)
    }

    @Test
    fun whenClearSubscriptionThenClearData() = runTest {
        authStore.status = "expired"
        authStore.startedAt = 1000L
        authStore.expiresOrRenewsAt = 1000L
        authStore.platform = "google"
        authStore.productId = "productId"
        authStore.entitlements = "[]"

        authRepository.clearSubscription()

        assertNull(authStore.status)
        assertNull(authStore.startedAt)
        assertNull(authStore.expiresOrRenewsAt)
        assertNull(authStore.platform)
        assertNull(authStore.productId)
        assertNull(authStore.entitlements)
    }

    @Test
    fun whenSaveAccountDataThenSetData() = runTest {
        assertNull(authStore.authToken)
        assertNull(authStore.externalId)

        authRepository.saveAccountData(authToken = "authToken", externalId = "externalId")

        assertEquals("authToken", authStore.authToken)
        assertEquals("externalId", authStore.externalId)
    }

    @Test
    fun whenTokensThenReturnTokens() = runTest {
        assertNull(authStore.authToken)
        assertNull(authStore.accessToken)

        authStore.accessToken = "accessToken"
        authStore.authToken = "authToken"

        assertEquals("authToken", authRepository.getAuthToken())
        assertEquals("accessToken", authRepository.getAccessToken())
    }

    @Test
    fun whenPurchaseToWaitingStatusThenStoreWaiting() = runTest {
        authRepository.purchaseToWaitingStatus()
        assertEquals(WAITING.statusName, authStore.status)
    }

    @Test
    fun whenGetStatusReturnCorrectStatus() = runTest {
        authStore.status = AUTO_RENEWABLE.statusName
        assertEquals(AUTO_RENEWABLE, authRepository.getStatus())
        authStore.status = NOT_AUTO_RENEWABLE.statusName
        assertEquals(NOT_AUTO_RENEWABLE, authRepository.getStatus())
        authStore.status = GRACE_PERIOD.statusName
        assertEquals(GRACE_PERIOD, authRepository.getStatus())
        authStore.status = INACTIVE.statusName
        assertEquals(INACTIVE, authRepository.getStatus())
        authStore.status = EXPIRED.statusName
        assertEquals(EXPIRED, authRepository.getStatus())
        authStore.status = WAITING.statusName
        assertEquals(WAITING, authRepository.getStatus())
        authStore.status = "test"
        assertEquals(UNKNOWN, authRepository.getStatus())
    }

    @Test
    fun whenCanSupportEncryptionThenReturnValue() = runTest {
        assertTrue(authRepository.canSupportEncryption())
    }

    @Test
    fun whenCanSupportEncryptionItCannotThenReturnFalse() = runTest {
        val repository: AuthRepository = RealAuthRepository(
            FakeSubscriptionsDataStore(supportEncryption = false),
            coroutineRule.testDispatcherProvider,
        )
        assertFalse(repository.canSupportEncryption())
    }
}
