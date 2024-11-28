package com.duckduckgo.subscriptions.impl.repository

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.serp_promo.FakeSerpPromo
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RealAuthRepositoryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authStore = FakeSubscriptionsDataStore()
    private val serpPromo = FakeSerpPromo()
    private val authRepository: AuthRepository = RealAuthRepository(authStore, coroutineRule.testDispatcherProvider, serpPromo)

    @Test
    fun whenClearAccountThenClearData() = runTest {
        authStore.email = "email@duck.com"
        authStore.externalId = "externalId"
        authStore.authToken = "authToken"
        authStore.accessToken = "accessToken"

        authRepository.setAuthToken(null)
        authRepository.setAccessToken(null)
        authRepository.setAccount(null)

        assertNull(authStore.accessToken)
        assertNull(authStore.authToken)
        assertNull(authStore.externalId)
        assertNull(authStore.email)
    }

    @Test
    fun whenSetAccessTokenThenInjectSerpPromoCookie() = runTest {
        authRepository.setAccessToken("token")

        assertEquals("token", serpPromo.cookie)
    }

    @Test
    fun whenClearSubscriptionThenClearData() = runTest {
        authStore.status = "expired"
        authStore.startedAt = 1000L
        authStore.expiresOrRenewsAt = 1000L
        authStore.platform = "google"
        authStore.productId = "productId"
        authStore.entitlements = "[]"

        authRepository.setSubscription(null)

        assertNull(authStore.status)
        assertNull(authStore.startedAt)
        assertNull(authStore.expiresOrRenewsAt)
        assertNull(authStore.platform)
        assertNull(authStore.productId)
        assertEquals("[]", authStore.entitlements)
    }

    @Test
    fun whenSetAccountThenSetData() = runTest {
        assertNull(authStore.email)
        assertNull(authStore.externalId)

        authRepository.setAccount(Account(externalId = "externalId", email = "john@example.com"))

        assertEquals("externalId", authStore.externalId)
        assertEquals("john@example.com", authStore.email)
    }

    @Test
    fun whenTokensThenReturnTokens() = runTest {
        assertNull(authStore.authToken)
        assertNull(authStore.accessToken)
        assertNull(authStore.accessTokenV2)
        assertNull(authStore.accessTokenV2ExpiresAt)
        assertNull(authStore.refreshTokenV2)
        assertNull(authStore.refreshTokenV2ExpiresAt)

        authStore.accessToken = "accessToken"
        authStore.authToken = "authToken"

        val accessTokenV2 = AccessToken(jwt = "jwt-access", expiresAt = Instant.parse("2024-10-21T10:15:30.00Z"))
        val refreshTokenV2 = RefreshToken(jwt = "jwt-refresh", expiresAt = Instant.parse("2024-10-21T10:15:30.00Z"))
        authRepository.setAccessTokenV2(accessTokenV2)
        authRepository.setRefreshTokenV2(refreshTokenV2)

        assertEquals("authToken", authRepository.getAuthToken())
        assertEquals("accessToken", authRepository.getAccessToken())
        assertEquals(accessTokenV2, authRepository.getAccessTokenV2())
        assertEquals(refreshTokenV2, authRepository.getRefreshTokenV2())
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
            serpPromo,
        )
        assertFalse(repository.canSupportEncryption())
    }
}
