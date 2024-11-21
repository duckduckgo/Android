package com.duckduckgo.subscriptions.impl

import android.annotation.SuppressLint
import android.content.Context
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.PricingPhase
import com.android.billingclient.api.ProductDetails.PricingPhases
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.authjwt.api.AccessTokenClaims
import com.duckduckgo.authjwt.api.AuthJwtValidator
import com.duckduckgo.authjwt.api.Entitlement
import com.duckduckgo.authjwt.api.RefreshTokenClaims
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.*
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.auth2.AuthClient
import com.duckduckgo.subscriptions.impl.auth2.BackgroundTokenRefresh
import com.duckduckgo.subscriptions.impl.auth2.PkceGenerator
import com.duckduckgo.subscriptions.impl.auth2.PkceGeneratorImpl
import com.duckduckgo.subscriptions.impl.auth2.TokenPair
import com.duckduckgo.subscriptions.impl.billing.PlayBillingManager
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.FakeSubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.repository.RealAuthRepository
import com.duckduckgo.subscriptions.impl.serp_promo.FakeSerpPromo
import com.duckduckgo.subscriptions.impl.services.AccessTokenResponse
import com.duckduckgo.subscriptions.impl.services.AccountResponse
import com.duckduckgo.subscriptions.impl.services.AuthService
import com.duckduckgo.subscriptions.impl.services.ConfirmationEntitlement
import com.duckduckgo.subscriptions.impl.services.ConfirmationResponse
import com.duckduckgo.subscriptions.impl.services.CreateAccountResponse
import com.duckduckgo.subscriptions.impl.services.EntitlementResponse
import com.duckduckgo.subscriptions.impl.services.PortalResponse
import com.duckduckgo.subscriptions.impl.services.StoreLoginResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.ValidateTokenResponse
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import java.lang.Exception
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

@RunWith(Parameterized::class)
class RealSubscriptionsManagerTest(private val authApiV2Enabled: Boolean) {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authService: AuthService = mock()
    private val subscriptionsService: SubscriptionsService = mock()
    private val authDataStore: SubscriptionsDataStore = FakeSubscriptionsDataStore()
    private val serpPromo = FakeSerpPromo()
    private val authRepository = RealAuthRepository(authDataStore, coroutineRule.testDispatcherProvider, serpPromo)
    private val emailManager: EmailManager = mock()
    private val playBillingManager: PlayBillingManager = mock()
    private val context: Context = mock()
    private val pixelSender: SubscriptionPixelSender = mock()

    @SuppressLint("DenyListedApi")
    private val privacyProFeature: PrivacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)
        .apply { authApiV2().setRawStoredState(State(authApiV2Enabled)) }
    private val authClient: AuthClient = mock()
    private val pkceGenerator: PkceGenerator = PkceGeneratorImpl()
    private val authJwtValidator: AuthJwtValidator = mock()
    private val timeProvider = FakeTimeProvider()
    private val backgroundTokenRefresh: BackgroundTokenRefresh = mock()
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Before
    fun before() = runTest {
        whenever(emailManager.getToken()).thenReturn(null)
        whenever(context.packageName).thenReturn("packageName")
        subscriptionsManager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotSignedInAndNotPurchaseStoredThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotSignedInAndPurchaseStoredThenReturnSubscriptionAndStoreData() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenAccessTokenSucceeds()
        givenV2AccessTokenRefreshSucceeds()

        subscriptionsManager.recoverSubscriptionFromStore() as RecoverSubscriptionResult.Success

        if (authApiV2Enabled) {
            verify(authClient).storeLogin(any(), any(), any())
            assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
            assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
        } else {
            verify(authService).storeLogin(any())
            assertEquals("authToken", authDataStore.authToken)
        }
        assertTrue(authRepository.getEntitlements().firstOrNull { it.product == NetP.value } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenStoreLoginFails()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserSignedInWithNotPurchasesThenReturnFailure() = runTest {
        givenUserIsSignedIn()
        givenAccessTokenSucceeds()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfValidateTokenSucceedsThenReturnExternalId() = runTest {
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenAccessTokenSucceeds()
        givenV2AccessTokenRefreshSucceeds()

        subscriptionsManager.recoverSubscriptionFromStore() as RecoverSubscriptionResult.Success

        assertEquals("1234", authDataStore.externalId)
        assertTrue(authRepository.getEntitlements().firstOrNull { it.product == NetP.value } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfSubscriptionExpiredThenReturnFailure() = runTest {
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionExists(EXPIRED)
        givenAccessTokenSucceeds()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsSignedIn()
        givenValidateTokenFails("failure")

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.recoverSubscriptionFromStore()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            if (authApiV2Enabled) {
                assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
                assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
            } else {
                assertEquals("accessToken", authDataStore.accessToken)
            }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFetchAndStoreAllDataIfUserNotSignedInThenReturnFalse() = runTest {
        givenUserIsNotSignedIn()

        val value = subscriptionsManager.fetchAndStoreAllData()

        assertFalse(value)
    }

    @Test
    fun whenFetchAndStoreAllDataIfTokenIsValidThenReturnSubscription() = runTest {
        assumeFalse(authApiV2Enabled) // fetchAndStoreAllData() is deprecated and won't be used with auth v2 enabled

        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithEntitlements()

        subscriptionsManager.fetchAndStoreAllData()
        assertEquals("1234", authDataStore.externalId)
        assertTrue(authRepository.getEntitlements().firstOrNull { it.product == NetP.value } != null)
    }

    @Test
    fun whenFetchAndStoreAllDataIfTokenIsValidThenReturnEmitEntitlements() = runTest {
        assumeFalse(authApiV2Enabled) // fetchAndStoreAllData() is deprecated and won't be used with auth v2 enabled

        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithEntitlements()

        subscriptionsManager.fetchAndStoreAllData()
        subscriptionsManager.entitlements.test {
            assertTrue(awaitItem().size == 1)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFetchAndStoreAllDataIfSubscriptionFailsThenReturnNull() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionFails()

        assertFalse(subscriptionsManager.fetchAndStoreAllData())
    }

    @Test
    fun whenFetchAndStoreAllDataIfSubscriptionFailsWith401ThenSignOutAndReturnNull() = runTest {
        assumeFalse(authApiV2Enabled) // fetchAndStoreAllData() is deprecated and won't be used with auth v2 enabled

        givenUserIsSignedIn()
        givenSubscriptionFails(httpResponseCode = 401)

        val dataFetched = subscriptionsManager.fetchAndStoreAllData()

        assertFalse(dataFetched)
        assertFalse(subscriptionsManager.isSignedIn.first())
        assertNull(subscriptionsManager.getSubscription())
        assertNull(subscriptionsManager.getAccount())
        assertNull(authRepository.getAuthToken())
        assertNull(authRepository.getAccessToken())
    }

    @Test
    fun whenPurchaseFlowIfUserNotSignedInAndNotPurchaseStoredThenCreateAccount() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountSucceeds()

        subscriptionsManager.purchase(mock(), planId = "")

        if (authApiV2Enabled) {
            verify(authClient).authorize(any())
            verify(authClient).createAccount(any())
            verify(authClient).getTokens(any(), any(), any())
        } else {
            verify(authService).createAccount(any())
        }
    }

    @Test
    fun whenPurchaseFlowIfUserNotSignedInAndNotPurchaseStoredAndSignedInEmailThenCreateAccountWithEmailToken() = runTest {
        assumeFalse(authApiV2Enabled) // passing email token when creating account is no longer a thing in api v2

        whenever(emailManager.getToken()).thenReturn("emailToken")
        givenUserIsNotSignedIn()

        subscriptionsManager.purchase(mock(), planId = "")

        verify(authService).createAccount("Bearer emailToken")
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountFailsThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountFails()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), planId = "")
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountSucceedsThenBillingFlowUsesCorrectExternalId() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountSucceeds()
        givenValidateTokenSucceedsNoEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), planId = "")

        verify(playBillingManager).launchBillingFlow(any(), any(), externalId = eq("1234"))
    }

    @Test
    fun whenPurchaseFlowIfUserNotSignedInAndPurchaseNotActiveInStoreThenGetIdFromPurchase() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements(status = "Expired")
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), "")

        verify(playBillingManager).launchBillingFlow(any(), any(), externalId = eq("1234"))
    }

    @Test
    fun whenPurchaseFlowIfUserNotSignedInAndPurchaseActiveInStoreThenRecoverSubscription() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), planId = "")
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(playBillingManager, never()).launchBillingFlow(any(), any(), any())
            assertTrue(awaitItem() is CurrentPurchase.Recovered)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenStoreLoginFails()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), planId = "")
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfUserSignedInThenValidateToken() = runTest {
        assumeFalse(authApiV2Enabled) // there is no /validate-token endpoint in v2 API

        givenUserIsSignedIn()

        subscriptionsManager.purchase(mock(), planId = "")

        verify(authService).validateToken(any())
    }

    @Test
    fun whenPurchaseFlowIfValidateTokenSucceedsThenBillingFlowUsesCorrectExternalIdAndEmitStates() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithoutEntitlements(status = "Expired")

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), planId = "")
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(playBillingManager).launchBillingFlow(any(), any(), externalId = eq("1234"))
            assertTrue(awaitItem() is CurrentPurchase.PreFlowFinished)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountFailsReturnFailure() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountFails()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), planId = "")
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfNullSubscriptionAndSignedInThenDoNotCreateAccount() = runTest {
        givenUserIsSignedIn()
        givenValidateTokenFails("failure")

        subscriptionsManager.purchase(mock(), "")

        verify(authService, never()).createAccount(any())
    }

    @Test
    fun whenPurchaseFlowIfAccountCreatedThenSetTokens() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), planId = "")
        if (authApiV2Enabled) {
            assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
            assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
            assertNull(authDataStore.accessToken)
            assertNull(authDataStore.authToken)
        } else {
            assertNull(authDataStore.accessTokenV2)
            assertNull(authDataStore.refreshTokenV2)
            assertEquals("accessToken", authDataStore.accessToken)
            assertEquals("authToken", authDataStore.authToken)
        }
    }

    @Test
    fun whenPurchaseFlowIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), planId = "")
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            if (authApiV2Enabled) {
                assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
                assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
                assertNull(authDataStore.accessToken)
                assertNull(authDataStore.authToken)
            } else {
                assertNull(authDataStore.accessTokenV2)
                assertNull(authDataStore.refreshTokenV2)
                assertEquals("accessToken", authDataStore.accessToken)
                assertEquals("authToken", authDataStore.authToken)
            }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test(expected = Exception::class)
    fun whenExchangeTokenFailsTokenThenReturnThrow() = runTest {
        givenAccessTokenFails()

        subscriptionsManager.exchangeAuthToken("authToken")
    }

    @Test
    fun whenExchangeTokenIfAccessTokenThenExchangeTokenAndStore() = runTest {
        givenAccessTokenSucceeds()

        val result = subscriptionsManager.exchangeAuthToken("authToken")
        assertEquals("accessToken", authDataStore.accessToken)
        assertEquals("accessToken", result)
    }

    @Test
    fun whenSubscribedToSubscriptionStatusThenEmit() = runTest {
        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )

        manager.subscriptionStatus.test {
            assertEquals(UNKNOWN, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSubscribedToSubscriptionStatusAndSubscriptionExistsThenEmit() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionExists()
        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )

        manager.subscriptionStatus.test {
            assertEquals(AUTO_RENEWABLE, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseSuccessfulThenPurchaseCheckedAndSuccessEmit() = runTest {
        givenUserIsSignedIn()
        givenConfirmPurchaseSucceeds()
        givenV2AccessTokenRefreshSucceeds()

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(playBillingManager.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Purchased("validToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)
            cancelAndConsumeRemainingEvents()
        }

        manager.entitlements.test {
            flowTest.emit(PurchaseState.Purchased("validToken", "packageName"))
            assertTrue(awaitItem().size == 1)
            cancelAndConsumeRemainingEvents()
        }

        manager.subscriptionStatus.test {
            flowTest.emit(PurchaseState.Purchased("validToken", "packageName"))
            assertEquals(AUTO_RENEWABLE, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFailedThenPurchaseCheckedAndWaitingEmit() = runTest {
        givenUserIsSignedIn()
        givenValidateTokenFails("failure")
        givenConfirmPurchaseFails()

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(playBillingManager.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Purchased("validateToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Waiting)
            cancelAndConsumeRemainingEvents()
        }

        manager.subscriptionStatus.test {
            flowTest.emit(PurchaseState.Purchased("validateToken", "packageName"))
            assertEquals(WAITING, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseCanceledThenEmitCanceled() = runTest {
        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(playBillingManager.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Canceled)
            assertTrue(awaitItem() is CurrentPurchase.Canceled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetAccessTokenIfUserIsSignedInThenReturnSuccess() = runTest {
        givenUserIsSignedIn()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        val actualAccessToken = (result as AccessTokenResult.Success).accessToken
        val expectedAccessToken = if (authApiV2Enabled) FAKE_ACCESS_TOKEN_V2 else "accessToken"
        assertEquals(expectedAccessToken, actualAccessToken)
    }

    @Test
    fun whenGetAccessTokenIfUserIsSignedInThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredThenGetNewTokenAndReturnSuccess() = runTest {
        assumeTrue(authApiV2Enabled)

        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertEquals("new access token", (result as AccessTokenResult.Success).accessToken)
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredAndRefreshFailsThenGetNewTokenAndReturnFailure() = runTest {
        assumeTrue(authApiV2Enabled)

        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredAndRefreshFailsWithAuthErrorThenGetNewTokenUsingStoreLoginAndReturnSuccess() = runTest {
        assumeTrue(authApiV2Enabled)

        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails(authenticationError = true)
        givenPurchaseStored()
        givenStoreLoginSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertEquals("new access token", (result as AccessTokenResult.Success).accessToken)
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredAndRefreshFailsWithAuthErrorAndStoreRecoveryNotPossibleThenSignOutAndReturnFailure() = runTest {
        assumeTrue(authApiV2Enabled)

        givenUserIsSignedIn()
        givenSubscriptionExists()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails(authenticationError = true)
        givenPurchaseStored()
        givenStoreLoginFails()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        assertFalse(subscriptionsManager.isSignedIn())
        assertNull(authRepository.getAccessTokenV2())
        assertNull(authRepository.getRefreshTokenV2())
        assertNull(authRepository.getAccount())
        assertNull(authRepository.getSubscription())
    }

    @Test
    fun whenGetAccessTokenIfSignedInWithV1ThenExchangesTokenForV2AndReturnsTrue() = runTest {
        assumeTrue(authApiV2Enabled)

        givenUserIsSignedIn(useAuthV2 = false)
        givenV1AccessTokenExchangeSuccess()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertEquals(FAKE_ACCESS_TOKEN_V2, (result as AccessTokenResult.Success).accessToken)
        assertEquals(FAKE_ACCESS_TOKEN_V2, authRepository.getAccessTokenV2()?.jwt)
        assertEquals(FAKE_REFRESH_TOKEN_V2, authRepository.getRefreshTokenV2()?.jwt)
        assertNull(authRepository.getAccessToken())
        assertNull(authRepository.getAuthToken())
    }

    @Test
    fun whenGetAuthTokenIfUserSignedInAndValidTokenThenReturnSuccess() = runTest {
        givenUserIsSignedIn()
        givenValidateTokenSucceedsWithEntitlements()

        val result = subscriptionsManager.getAuthToken()

        assertTrue(result is AuthTokenResult.Success)

        val actualAuthToken = (result as AuthTokenResult.Success).authToken
        val expectedAuthToken = if (authApiV2Enabled) FAKE_ACCESS_TOKEN_V2 else "authToken"
        assertEquals(expectedAuthToken, actualAuthToken)
    }

    @Test
    fun whenGetAuthTokenIfUserNotSignedInThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()

        val result = subscriptionsManager.getAuthToken()

        assertTrue(result is AuthTokenResult.Failure)
    }

    @Test
    fun whenGetAuthTokenIfUserSignedInWithSubscriptionAndTokenExpiredAndEntitlementsExistsThenReturnSuccess() = runTest {
        assumeFalse(authApiV2Enabled) // getAuthToken() is deprecated and with auth v2 enabled will just delegate to getAccessToken()

        authDataStore.externalId = "1234"
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithEntitlements()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenAccessTokenSucceeds()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthTokenResult.Success)
        assertEquals("authToken", (result as AuthTokenResult.Success).authToken)
    }

    @Test
    fun whenGetAuthTokenIfUserSignedInWithSubscriptionAndTokenExpiredAndEntitlementsExistsAndExternalIdDifferentThenReturnFailure() = runTest {
        assumeFalse(authApiV2Enabled) // getAuthToken() is deprecated and with auth v2 enabled will just delegate to getAccessToken()

        authDataStore.externalId = "test"
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithEntitlements()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenAccessTokenSucceeds()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthTokenResult.Failure.TokenExpired)
        assertEquals("authToken", (result as AuthTokenResult.Failure.TokenExpired).authToken)
    }

    @Test
    fun whenGetAuthTokenIfUserSignedInWithSubscriptionAndTokenExpiredAndEntitlementsDoNotExistThenReturnFailure() = runTest {
        assumeFalse(authApiV2Enabled) // getAuthToken() is deprecated and with auth v2 enabled will just delegate to getAccessToken()

        givenUserIsSignedIn()
        givenValidateTokenSucceedsNoEntitlements()
        givenValidateTokenFailsAndThenSucceedsWithNoEntitlements("""{ "error": "expired_token" }""")
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenAccessTokenSucceeds()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthTokenResult.Failure.TokenExpired)
        assertEquals("authToken", (result as AuthTokenResult.Failure.TokenExpired).authToken)
    }

    @Test
    fun whenGetAuthTokenIfUserSignedInAndTokenExpiredAndNoPurchaseInTheStoreThenReturnFailure() = runTest {
        assumeFalse(authApiV2Enabled) // getAuthToken() is deprecated and with auth v2 enabled will just delegate to getAccessToken()

        givenUserIsSignedIn()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")

        val result = subscriptionsManager.getAuthToken()

        verify(authService, never()).storeLogin(any())
        assertTrue(result is AuthTokenResult.Failure.TokenExpired)
        assertEquals("authToken", (result as AuthTokenResult.Failure.TokenExpired).authToken)
    }

    @Test
    fun whenGetAuthTokenIfUserSignedInAndTokenExpiredAndPurchaseNotValidThenReturnFailure() = runTest {
        assumeFalse(authApiV2Enabled) // getAuthToken() is deprecated and with auth v2 enabled will just delegate to getAccessToken()

        givenUserIsSignedIn()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")
        givenStoreLoginFails()
        givenPurchaseStored()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthTokenResult.Failure.TokenExpired)
        assertEquals("authToken", (result as AuthTokenResult.Failure.TokenExpired).authToken)
    }

    @Test
    fun whenGetSubscriptionThenReturnCorrectStatus() = runTest {
        assumeFalse(authApiV2Enabled) // fetchAndStoreAllData() is deprecated and won't be used with auth v2 enabled

        givenUserIsSignedIn()
        givenValidateTokenSucceedsWithEntitlements()

        givenSubscriptionSucceedsWithEntitlements("Auto-Renewable")
        subscriptionsManager.fetchAndStoreAllData()
        assertEquals(AUTO_RENEWABLE, subscriptionsManager.getSubscription()?.status)

        givenSubscriptionSucceedsWithEntitlements("Not Auto-Renewable")
        subscriptionsManager.fetchAndStoreAllData()
        assertEquals(NOT_AUTO_RENEWABLE, subscriptionsManager.getSubscription()?.status)

        givenSubscriptionSucceedsWithEntitlements("Grace Period")
        subscriptionsManager.fetchAndStoreAllData()
        assertEquals(GRACE_PERIOD, subscriptionsManager.getSubscription()?.status)

        givenSubscriptionSucceedsWithEntitlements("Inactive")
        subscriptionsManager.fetchAndStoreAllData()
        assertEquals(INACTIVE, subscriptionsManager.getSubscription()?.status)

        givenSubscriptionSucceedsWithEntitlements("Expired")
        subscriptionsManager.fetchAndStoreAllData()
        assertEquals(EXPIRED, subscriptionsManager.getSubscription()?.status)

        givenSubscriptionSucceedsWithEntitlements("test")
        subscriptionsManager.fetchAndStoreAllData()
        assertEquals(UNKNOWN, subscriptionsManager.getSubscription()?.status)
    }

    @Test
    fun whenGetPortalAndUserSignedInReturnUrl() = runTest {
        givenUserIsSignedIn()
        givenUrlPortalSucceeds()

        assertEquals("example.com", subscriptionsManager.getPortalUrl())
    }

    @Test
    fun whenGetPortalAndUserIsNotSignedInReturnNull() = runTest {
        givenUserIsNotSignedIn()

        assertNull(subscriptionsManager.getPortalUrl())
    }

    @Test
    fun whenGetPortalFailsReturnNull() = runTest {
        givenUserIsSignedIn()
        givenUrlPortalFails()

        assertNull(subscriptionsManager.getPortalUrl())
    }

    @Test
    fun whenSignOutThenCallRepositorySignOut() = runTest {
        val mockRepo: AuthRepository = mock()
        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            mockRepo,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )
        manager.signOut()
        verify(mockRepo).setSubscription(null)
        verify(mockRepo).setAccount(null)
        verify(mockRepo).setAuthToken(null)
        verify(mockRepo).setAccessToken(null)
        verify(mockRepo).setEntitlements(emptyList())
        verify(mockRepo).setAccessTokenV2(null)
        verify(mockRepo).setRefreshTokenV2(null)
    }

    @Test
    fun whenSignOutEmitFalseForIsSignedIn() = runTest {
        givenSubscriptionExists()
        givenUserIsSignedIn()

        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            subscriptionsManager.signOut()
            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSignOutThenEmitUnknown() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionExists()

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )

        manager.subscriptionStatus.test {
            assertEquals(AUTO_RENEWABLE, awaitItem())
            manager.signOut()
            assertEquals(UNKNOWN, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSignOutThenEmitEmptyEntitlements() = runTest {
        givenSubscriptionExists()
        givenUserIsSignedIn()

        subscriptionsManager.entitlements.test {
            assertFalse(expectMostRecentItem().isEmpty())
            subscriptionsManager.signOut()
            assertTrue(expectMostRecentItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseIsSuccessfulThenPixelIsSent() = runTest {
        givenUserIsSignedIn()
        givenValidateTokenSucceedsWithEntitlements()
        givenConfirmPurchaseSucceeds()
        givenV2AccessTokenRefreshSucceeds()

        whenever(playBillingManager.purchaseState).thenReturn(flowOf(PurchaseState.Purchased("any", "any")))

        subscriptionsManager.currentPurchaseState.test {
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)

            verify(pixelSender).reportPurchaseSuccess()
            verify(pixelSender).reportSubscriptionActivated()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSubscriptionIsRestoredOnPurchaseAttemptThenPixelIsSent() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), planId = "")
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Recovered)

            verify(pixelSender).reportRestoreAfterPurchaseAttemptSuccess()
            verify(pixelSender).reportSubscriptionActivated()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFailsThenPixelIsSent() = runTest {
        givenUserIsSignedIn()
        givenValidateTokenFails("failure")
        givenConfirmPurchaseFails()

        whenever(playBillingManager.purchaseState).thenReturn(flowOf(PurchaseState.Purchased("validateToken", "packageName")))

        subscriptionsManager.currentPurchaseState.test {
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Waiting)
            assertEquals(WAITING.statusName, authDataStore.status)
            verify(pixelSender).reportPurchaseFailureBackend()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountFailsThenPixelIsSent() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountFails()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), planId = "")
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)

            verify(pixelSender).reportPurchaseFailureAccountCreation()
            verify(pixelSender).reportPurchaseFailureOther()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetSubscriptionOfferThenReturnValue() = runTest {
        val productDetails: ProductDetails = mock { productDetails ->
            whenever(productDetails.productId).thenReturn(SubscriptionsConstants.BASIC_SUBSCRIPTION)

            val pricingPhaseList: List<PricingPhase> = listOf(
                mock { pricingPhase ->
                    whenever(pricingPhase.formattedPrice).thenReturn("1$")
                },
            )

            val pricingPhases: PricingPhases = mock { pricingPhases ->
                whenever(pricingPhases.pricingPhaseList).thenReturn(pricingPhaseList)
            }

            val monthlyOffer: ProductDetails.SubscriptionOfferDetails = mock { offer ->
                whenever(offer.basePlanId).thenReturn(MONTHLY_PLAN)
                whenever(offer.pricingPhases).thenReturn(pricingPhases)
            }

            val yearlyOffer: ProductDetails.SubscriptionOfferDetails = mock { offer ->
                whenever(offer.basePlanId).thenReturn(YEARLY_PLAN)
                whenever(offer.pricingPhases).thenReturn(pricingPhases)
            }

            whenever(productDetails.subscriptionOfferDetails).thenReturn(listOf(monthlyOffer, yearlyOffer))
        }

        whenever(playBillingManager.products).thenReturn(listOf(productDetails))

        val subscriptionOffer = subscriptionsManager.getSubscriptionOffer()!!

        with(subscriptionOffer) {
            assertEquals(MONTHLY_PLAN, monthlyPlanId)
            assertEquals("1$", monthlyFormattedPrice)
            assertEquals(YEARLY_PLAN, yearlyPlanId)
            assertEquals("1$", yearlyFormattedPrice)
        }
    }

    @Test
    fun whenCanSupportEncryptionThenReturnTrue() = runTest {
        assertTrue(subscriptionsManager.canSupportEncryption())
    }

    @Test
    fun whenCanSupportEncryptionIfCannotThenReturnFalse() = runTest {
        val authDataStore: SubscriptionsDataStore = FakeSubscriptionsDataStore(supportEncryption = false)
        val authRepository = RealAuthRepository(authDataStore, coroutineRule.testDispatcherProvider, serpPromo)
        subscriptionsManager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { privacyProFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
        )

        assertFalse(subscriptionsManager.canSupportEncryption())
    }

    @Test
    fun whenNotSignedInThenIsSignedInReturnsFalse() = runTest {
        givenUserIsNotSignedIn()
        assertFalse(subscriptionsManager.isSignedIn.first())
    }

    @Test
    fun whenSignedInThenIsSignedInReturnsTrue() = runTest {
        givenUserIsSignedIn()
        assertTrue(subscriptionsManager.isSignedIn.first())
    }

    @Test
    fun whenEntitlementsExistAndSubscriptionIsInactiveThenEntitlementsReturnsEmptyList() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithEntitlements(status = INACTIVE.statusName)

        subscriptionsManager.fetchAndStoreAllData()

        subscriptionsManager.entitlements.test {
            val entitlements = expectMostRecentItem()
            assertTrue(entitlements.isEmpty())
        }
    }

    private suspend fun givenUrlPortalSucceeds() {
        whenever(subscriptionsService.portal()).thenReturn(PortalResponse("example.com"))
    }

    private suspend fun givenUrlPortalFails() {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(subscriptionsService.portal()).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenSubscriptionFails(httpResponseCode: Int = 400) {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(subscriptionsService.subscription()).thenThrow(HttpException(Response.error<String>(httpResponseCode, exception)))
    }

    private suspend fun givenSubscriptionSucceedsWithoutEntitlements(status: String = "Auto-Renewable") {
        givenValidateTokenSucceedsNoEntitlements()
        whenever(subscriptionsService.subscription()).thenReturn(
            SubscriptionResponse(
                productId = MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = status,
            ),
        )
    }

    private suspend fun givenSubscriptionSucceedsWithEntitlements(status: String = "Auto-Renewable") {
        givenValidateTokenSucceedsWithEntitlements()
        whenever(subscriptionsService.subscription()).thenReturn(
            SubscriptionResponse(
                productId = MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = status,
            ),
        )
    }

    private fun givenUserIsNotSignedIn() {
        authDataStore.accessToken = null
        authDataStore.authToken = null
        authDataStore.accessTokenV2 = null
        authDataStore.accessTokenV2ExpiresAt = null
        authDataStore.refreshTokenV2 = null
        authDataStore.refreshTokenV2ExpiresAt = null
    }

    private fun givenUserIsSignedIn(useAuthV2: Boolean = authApiV2Enabled) {
        if (useAuthV2) {
            authDataStore.accessTokenV2 = FAKE_ACCESS_TOKEN_V2
            authDataStore.accessTokenV2ExpiresAt = timeProvider.currentTime + Duration.ofHours(4)
            authDataStore.refreshTokenV2 = FAKE_REFRESH_TOKEN_V2
            authDataStore.refreshTokenV2ExpiresAt = timeProvider.currentTime + Duration.ofDays(30)
            authDataStore.externalId = "1234"
        } else {
            authDataStore.accessToken = "accessToken"
            authDataStore.authToken = "authToken"
        }
    }

    private suspend fun givenCreateAccountFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.createAccount(any())).thenThrow(HttpException(Response.error<String>(400, exception)))

        whenever(authClient.authorize(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
        whenever(authClient.createAccount(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenCreateAccountSucceeds() {
        whenever(authService.createAccount(any())).thenReturn(
            CreateAccountResponse(
                authToken = "authToken",
                externalId = "1234",
                status = "ok",
            ),
        )

        whenever(authClient.authorize(any())).thenReturn("fake session id")
        whenever(authClient.createAccount(any())).thenReturn("fake authorization code")
        whenever(authClient.getTokens(any(), any(), any()))
            .thenReturn(TokenPair(FAKE_ACCESS_TOKEN_V2, FAKE_REFRESH_TOKEN_V2))

        givenValidateV2TokensSucceeds()
    }

    private fun givenSubscriptionExists(status: SubscriptionStatus = AUTO_RENEWABLE) {
        authDataStore.platform = "google"
        authDataStore.productId = "productId"
        authDataStore.entitlements = """[{"product":"Network Protection", "name":"subscriber"}]"""
        authDataStore.status = status.statusName
        authDataStore.startedAt = 1000L
        authDataStore.expiresOrRenewsAt = 1000L
    }

    private suspend fun givenValidateTokenFailsAndThenSucceeds(failure: String) {
        val exception = failure.toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.validateToken(any()))
            .thenThrow(HttpException(Response.error<String>(400, exception)))
            .thenReturn(
                ValidateTokenResponse(
                    account = AccountResponse(
                        email = "accessToken",
                        externalId = "1234",
                        entitlements = listOf(
                            EntitlementResponse("id", "name", "testProduct"),
                        ),
                    ),
                ),
            )
    }

    private suspend fun givenValidateTokenFailsAndThenSucceedsWithNoEntitlements(failure: String) {
        val exception = failure.toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.validateToken(any()))
            .thenThrow(HttpException(Response.error<String>(400, exception)))
            .thenReturn(
                ValidateTokenResponse(
                    account = AccountResponse(
                        email = "accessToken",
                        externalId = "1234",
                        entitlements = listOf(),
                    ),
                ),
            )
    }

    private suspend fun givenStoreLoginFails() {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.storeLogin(any())).thenThrow(HttpException(Response.error<String>(400, exception)))

        whenever(authClient.authorize(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
        whenever(authClient.storeLogin(any(), any(), any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenValidateTokenSucceedsWithEntitlements() {
        whenever(authService.validateToken(any())).thenReturn(
            ValidateTokenResponse(
                account = AccountResponse(
                    email = "email",
                    externalId = "1234",
                    entitlements = listOf(
                        EntitlementResponse("id", NetP.value, NetP.value),
                    ),
                ),
            ),
        )
    }

    private suspend fun givenValidateTokenSucceedsNoEntitlements() {
        whenever(authService.validateToken(any())).thenReturn(
            ValidateTokenResponse(
                account = AccountResponse(
                    email = "accessToken",
                    externalId = "1234",
                    entitlements = emptyList(),
                ),
            ),
        )
    }

    private suspend fun givenValidateTokenFails(failure: String) {
        val exception = failure.toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.validateToken(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private fun givenPurchaseStored() {
        val purchaseRecord = PurchaseHistoryRecord(
            """
        {"purchaseToken": "validToken", "productId": "test", "purchaseTime":1, "quantity":1}
        """,
            "signature",
        )
        whenever(playBillingManager.products).thenReturn(emptyList())
        whenever(playBillingManager.purchaseHistory).thenReturn(listOf(purchaseRecord))
    }

    private suspend fun givenStoreLoginSucceeds(newAccessToken: String = FAKE_ACCESS_TOKEN_V2) {
        whenever(authService.storeLogin(any())).thenReturn(
            StoreLoginResponse(
                authToken = "authToken",
                externalId = "1234",
                email = "test@duck.com",
                status = "ok",
            ),
        )

        whenever(authClient.authorize(any())).thenReturn("fake session id")
        whenever(authClient.storeLogin(any(), any(), any())).thenReturn("fake authorization code")
        whenever(authClient.getTokens(any(), any(), any()))
            .thenReturn(TokenPair(newAccessToken, FAKE_REFRESH_TOKEN_V2))
        whenever(authClient.getJwks()).thenReturn("fake jwks")

        givenValidateV2TokensSucceeds()
    }

    private suspend fun givenV1AccessTokenExchangeSuccess() {
        whenever(authClient.authorize(any())).thenReturn("fake session id")
        whenever(authClient.exchangeV1AccessToken(any(), any())).thenReturn("fake authorization code")
        whenever(authClient.getTokens(any(), any(), any())).thenReturn(TokenPair(FAKE_ACCESS_TOKEN_V2, FAKE_REFRESH_TOKEN_V2))
        whenever(authClient.getJwks()).thenReturn("fake jwks")
        givenValidateV2TokensSucceeds()
    }

    private suspend fun givenAccessTokenSucceeds() {
        whenever(authService.accessToken(any())).thenReturn(AccessTokenResponse("accessToken"))
    }

    private suspend fun givenAccessTokenFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.accessToken(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenConfirmPurchaseFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(subscriptionsService.confirm(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenConfirmPurchaseSucceeds() {
        whenever(subscriptionsService.confirm(any())).thenReturn(
            ConfirmationResponse(
                email = "test@duck.com",
                entitlements = listOf(
                    ConfirmationEntitlement(NetP.value, NetP.value),
                ),
                subscription = SubscriptionResponse(
                    productId = "id",
                    platform = "google",
                    status = "Auto-Renewable",
                    startedAt = 1000000L,
                    expiresOrRenewsAt = 1000000L,
                ),
            ),
        )
    }

    private suspend fun givenV2AccessTokenRefreshSucceeds(
        newAccessToken: String = FAKE_ACCESS_TOKEN_V2,
        newRefreshToken: String = FAKE_REFRESH_TOKEN_V2,
    ) {
        whenever(authClient.getTokens(any()))
            .thenReturn(TokenPair(newAccessToken, newRefreshToken))
        whenever(authClient.getJwks()).thenReturn("fake jwks")

        givenValidateV2TokensSucceeds()
    }

    private suspend fun givenV2AccessTokenRefreshFails(authenticationError: Boolean = false) {
        val exception = if (authenticationError) {
            val responseBody = "failure".toResponseBody("text/json".toMediaTypeOrNull())
            HttpException(Response.error<Void>(401, responseBody))
        } else {
            RuntimeException()
        }
        whenever(authClient.getTokens(any())).thenThrow(exception)
    }

    private suspend fun givenValidateV2TokensSucceeds() {
        whenever(authClient.getJwks()).thenReturn("fake jwks")

        whenever(authJwtValidator.validateAccessToken(any(), any())).thenReturn(
            AccessTokenClaims(
                expiresAt = Instant.now() + Duration.ofHours(4),
                accountExternalId = "1234",
                email = null,
                entitlements = listOf(Entitlement(product = NetP.value, name = "subscriber")),
            ),
        )

        whenever(authJwtValidator.validateRefreshToken(any(), any())).thenReturn(
            RefreshTokenClaims(
                expiresAt = Instant.now() + Duration.ofDays(30),
                accountExternalId = "1234",
            ),
        )
    }

    private suspend fun givenAccessTokenIsExpired() {
        val accessToken = authRepository.getAccessTokenV2() ?: return
        authRepository.setAccessTokenV2(accessToken.copy(expiresAt = timeProvider.currentTime - Duration.ofHours(1)))
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var currentTime: Instant = Instant.parse("2024-10-28T00:00:00Z")

        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
    }

    private companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "authApiV2Enabled={0}")
        fun data(): Collection<Array<Boolean>> = listOf(arrayOf(true), arrayOf(false))

        const val FAKE_ACCESS_TOKEN_V2 = "fake access token"
        const val FAKE_REFRESH_TOKEN_V2 = "fake refresh token"
    }
}
