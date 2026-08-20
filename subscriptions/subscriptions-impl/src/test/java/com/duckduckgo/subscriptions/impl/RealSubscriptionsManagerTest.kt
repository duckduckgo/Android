package com.duckduckgo.subscriptions.impl

import android.annotation.SuppressLint
import android.content.Context
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.PricingPhase
import com.android.billingclient.api.ProductDetails.PricingPhases
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.android.billingclient.api.Purchase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FixedLocaleRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.api.model.Entitlement
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ADVANCED_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PRO_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.NETP
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PRO_PLAN_US
import com.duckduckgo.subscriptions.impl.auth2.AccessTokenClaims
import com.duckduckgo.subscriptions.impl.auth2.AuthClient
import com.duckduckgo.subscriptions.impl.auth2.AuthJwtValidator
import com.duckduckgo.subscriptions.impl.auth2.BackgroundTokenRefresh
import com.duckduckgo.subscriptions.impl.auth2.CrossProcessLock
import com.duckduckgo.subscriptions.impl.auth2.PkceGenerator
import com.duckduckgo.subscriptions.impl.auth2.PkceGeneratorImpl
import com.duckduckgo.subscriptions.impl.auth2.RefreshTokenClaims
import com.duckduckgo.subscriptions.impl.auth2.TokenPair
import com.duckduckgo.subscriptions.impl.billing.LatestPurchaseResult
import com.duckduckgo.subscriptions.impl.billing.PlayBillingManager
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Canceled
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Failure
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Purchased
import com.duckduckgo.subscriptions.impl.billing.SubscriptionReplacementMode
import com.duckduckgo.subscriptions.impl.notification.VpnReminderNotificationScheduler
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Account
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.FakeSubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.repository.RealAuthRepository
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.serp_promo.FakeSerpPromo
import com.duckduckgo.subscriptions.impl.services.ActiveOfferResponse
import com.duckduckgo.subscriptions.impl.services.ConfirmationEntitlement
import com.duckduckgo.subscriptions.impl.services.ConfirmationResponse
import com.duckduckgo.subscriptions.impl.services.PendingPlanResponse
import com.duckduckgo.subscriptions.impl.services.PortalResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.wideevents.AuthTokenRefreshWideEvent
import com.duckduckgo.subscriptions.impl.wideevents.FreeTrialConversionWideEvent
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionPurchaseWideEvent
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionRestoreWideEvent
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionSwitchWideEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.io.Closeable
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class RealSubscriptionsManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val fixedLocalRule = FixedLocaleRule()

    private val subscriptionsService: SubscriptionsService = mock()
    private val authDataStore: FakeSubscriptionsDataStore = FakeSubscriptionsDataStore()
    private val serpPromo = FakeSerpPromo()

    @SuppressLint("DenyListedApi")
    private val subscriptionsFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)
        .apply {
            serializeTokenRefresh().setRawStoredState(State(true))
        }
    private val authRepository = RealAuthRepository(authDataStore, coroutineRule.testDispatcherProvider, serpPromo, { subscriptionsFeature })
    private val playBillingManager: PlayBillingManager = mock()
    private val context: Context = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val subscriptionPurchaseWideEvent: SubscriptionPurchaseWideEvent = mock()
    private val tokenRefreshWideEvent: AuthTokenRefreshWideEvent = mock()
    private val subscriptionSwitchWideEvent: SubscriptionSwitchWideEvent = mock()
    private val freeTrialConversionWideEvent: FreeTrialConversionWideEvent = mock()
    private val subscriptionRestoreWideEvent: SubscriptionRestoreWideEvent = mock()
    private val vpnReminderNotificationScheduler: VpnReminderNotificationScheduler = mock()

    private val authClient: AuthClient = mock()
    private val pkceGenerator: PkceGenerator = PkceGeneratorImpl()
    private val authJwtValidator: AuthJwtValidator = mock()
    private val timeProvider = FakeTimeProvider()
    private val backgroundTokenRefresh: BackgroundTokenRefresh = mock()
    private val crossProcessLock: CrossProcessLock = mock()
    private lateinit var subscriptionsManager: RealSubscriptionsManager

    @Before
    fun before() = runTest {
        whenever(context.packageName).thenReturn("packageName")
        whenever(playBillingManager.purchaseState).thenReturn(flowOf())
        whenever(crossProcessLock.acquire(any(), any())).thenReturn(Result.success(FakeLockHandle()))
        subscriptionsManager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
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
        givenV2AccessTokenRefreshSucceeds()

        subscriptionsManager.recoverSubscriptionFromStore() as RecoverSubscriptionResult.Success

        verify(authClient).storeLogin(any(), any(), any())
        assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
        assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
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

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfStoreLoginSucceedsThenReturnExternalId() = runTest {
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
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

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfStoreLoginSucceedsButSubscriptionNotActiveThenDoesNotEmitPixel() = runTest {
        givenUserIsNotSignedIn()
        givenActivePurchase()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements(status = "Expired")
        givenV2AccessTokenRefreshSucceeds()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
        assertEquals("SubscriptionNotFound", (result as RecoverSubscriptionResult.Failure).message)
        verify(pixelSender, never()).reportRecoverSubscriptionNoActivePurchase()
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfNoPurchaseStoredThenReturnFailure() = runTest {
        givenUserIsSignedIn()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()

        subscriptionsManager.recoverSubscriptionFromStore()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
            assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRecoverSubscriptionFromStoreWithUseQueryPurchasesAndActivePurchaseThenSuccess() = runTest {
        givenUserIsNotSignedIn()
        givenActivePurchase()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenV2AccessTokenRefreshSucceeds()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Success)
        verify(authClient).storeLogin(any(), any(), any())
        verify(pixelSender, never()).reportRecoverSubscriptionNoActivePurchase()
    }

    @Test
    fun whenRecoverSubscriptionFromStoreWithUseQueryPurchasesAndNoActivePurchaseThenSubscriptionNotFoundFailure() = runTest {
        givenUserIsNotSignedIn()
        givenNoActivePurchase()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
        assertEquals("SubscriptionNotFound", (result as RecoverSubscriptionResult.Failure).message)
        verify(authClient, never()).storeLogin(any(), any(), any())
        verify(pixelSender).reportRecoverSubscriptionNoActivePurchase()
    }

    @Test
    fun whenRecoverSubscriptionFromStoreWithUseQueryPurchasesAndPurchaseInfoUnknownThenGenericFailure() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseInfoUnknown()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
        assertEquals(
            "Store login error: PurchaseInfoNotAvailable: billing_client_not_ready",
            (result as RecoverSubscriptionResult.Failure).message,
        )
        verify(authClient, never()).storeLogin(any(), any(), any())
        verify(pixelSender, never()).reportRecoverSubscriptionNoActivePurchase()
    }

    @Test
    fun whenRefreshTokenWithUseQueryPurchasesAndNoActivePurchaseThenSignsOut() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionExists()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails(errorCode = "invalid_token")
        givenNoActivePurchase()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        assertFalse(subscriptionsManager.isSignedIn())
        assertNull(authRepository.getAccessTokenV2())
        assertNull(authRepository.getRefreshTokenV2())
        verify(pixelSender).reportAuthV2InvalidRefreshTokenDetected()
        verify(pixelSender).reportAuthV2InvalidRefreshTokenSignedOut()
        verify(pixelSender, never()).reportAuthV2InvalidRefreshTokenRecovered()
    }

    @Test
    fun whenRefreshTokenWithUseQueryPurchasesAndPurchaseInfoUnknownThenDoesNotSignOut() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionExists()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails(errorCode = "invalid_token")
        givenPurchaseInfoUnknown()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        assertTrue(subscriptionsManager.isSignedIn())
        assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
        verify(pixelSender).reportAuthV2InvalidRefreshTokenDetected()
        verify(pixelSender, never()).reportAuthV2InvalidRefreshTokenSignedOut()
        verify(pixelSender, never()).reportAuthV2InvalidRefreshTokenRecovered()
    }

    @Test
    fun whenRefreshTokenWithUseQueryPurchasesAndActivePurchaseThenRecoversTokens() = runTest {
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails(errorCode = "invalid_token")
        givenActivePurchase()
        givenStoreLoginSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertEquals("new access token", (result as AccessTokenResult.Success).accessToken)
        verify(pixelSender).reportAuthV2InvalidRefreshTokenDetected()
        verify(pixelSender).reportAuthV2InvalidRefreshTokenRecovered()
    }

    @Test
    fun whenPurchaseFlowIfUserIsSignedInAndSubscriptionFailsWith401ThenSignOutAndCreateNewAccount() = runTest {
        givenUserIsSignedIn(accountExternalId = "5678")
        givenSubscriptionFails(httpResponseCode = 401)
        givenCreateAccountSucceeds()
        val accountExternalId = authDataStore.externalId

        purchase()

        verify(authClient).authorize(any())
        verify(authClient).createAccount(any())
        verify(authClient).getTokens(any(), any(), any())

        assertNotEquals(accountExternalId, authDataStore.externalId)
    }

    @Test
    fun whenPurchaseFlowIfUserNotSignedInAndNotPurchaseStoredThenCreateAccount() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountSucceeds()

        purchase()

        verify(authClient).authorize(any())
        verify(authClient).createAccount(any())
        verify(authClient).getTokens(any(), any(), any())
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountFailsThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountFails()

        subscriptionsManager.currentPurchaseState.test {
            purchase()
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountSucceedsThenBillingFlowUsesCorrectExternalId() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountSucceeds()

        purchase()

        verify(playBillingManager).launchBillingFlow(any(), any(), externalId = eq("1234"), isNull())
    }

    @Test
    fun whenPurchaseFlowIfUserNotSignedInAndPurchaseNotActiveInStoreThenGetIdFromPurchase() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements(status = "Expired")

        purchase()

        verify(playBillingManager).launchBillingFlow(any(), any(), externalId = eq("1234"), isNull())
    }

    @Test
    fun whenPurchaseFlowIfUserNotSignedInAndPurchaseActiveInStoreThenRecoverSubscription() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()

        subscriptionsManager.currentPurchaseState.test {
            purchase()
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(playBillingManager, never()).launchBillingFlow(any(), any(), any(), isNull())
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
            purchase()
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfUserSignedInThenBillingFlowUsesCorrectExternalIdAndEmitStates() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithoutEntitlements(status = "Expired")

        subscriptionsManager.currentPurchaseState.test {
            purchase()
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(playBillingManager).launchBillingFlow(any(), any(), externalId = eq("1234"), isNull())
            assertTrue(awaitItem() is CurrentPurchase.PreFlowFinished)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountFailsReturnFailure() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountFails()

        subscriptionsManager.currentPurchaseState.test {
            purchase()
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfNullSubscriptionAndSignedInThenDoNotCreateAccount() = runTest {
        givenUserIsSignedIn()

        purchase()

        verify(authClient, never()).createAccount(any())
    }

    @Test
    fun whenPurchaseFlowIfAccountCreatedThenSetTokens() = runTest {
        givenUserIsNotSignedIn()
        givenCreateAccountSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()

        purchase()
        assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
        assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
        assertNull(authDataStore.accessToken)
        assertNull(authDataStore.authToken)
    }

    @Test
    fun whenPurchaseFlowIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()

        purchase()

        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals(FAKE_ACCESS_TOKEN_V2, authDataStore.accessTokenV2)
            assertEquals(FAKE_REFRESH_TOKEN_V2, authDataStore.refreshTokenV2)
            assertNull(authDataStore.accessToken)
            assertNull(authDataStore.authToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseIfSignedInAndSubscriptionRefreshFailsWith400StatusThenLaunchesPurchaseFlow() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionFails(httpResponseCode = 400)

        purchase()

        verify(playBillingManager).launchBillingFlow(any(), any(), any(), isNull())
    }

    @Test
    fun whenPurchaseIfSignedInAndSubscriptionRefreshFailsWith404StatusThenLaunchesPurchaseFlow() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionFails(httpResponseCode = 404)

        purchase()

        verify(playBillingManager).launchBillingFlow(any(), any(), any(), isNull())
    }

    @Test
    fun whenSubscribedToSubscriptionStatusThenEmit() = runTest {
        whenever(playBillingManager.purchaseState).thenReturn(flowOf())
        val manager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
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
        whenever(playBillingManager.purchaseState).thenReturn(flowOf())
        val manager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
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
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(Purchased("validToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)
            cancelAndConsumeRemainingEvents()
        }

        manager.entitlements.test {
            flowTest.emit(Purchased("validToken", "packageName"))
            assertTrue(awaitItem().size == 1)
            cancelAndConsumeRemainingEvents()
        }

        manager.subscriptionStatus.test {
            flowTest.emit(Purchased("validToken", "packageName"))
            assertEquals(AUTO_RENEWABLE, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseSuccessfulWithPendingPlanThenPendingPlanIsStored() = runTest {
        givenUserIsSignedIn()
        givenConfirmPurchaseSucceedsWithPendingPlan()
        givenV2AccessTokenRefreshSucceeds()

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(playBillingManager.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(Purchased("validToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)
            cancelAndConsumeRemainingEvents()
        }

        val subscription = authRepository.getSubscription()
        assertNotNull(subscription)
        assertEquals(1, subscription!!.pendingPlans.size)
        assertEquals(YEARLY_PLAN_US, subscription.pendingPlans[0].productId)
        assertEquals("Yearly", subscription.pendingPlans[0].billingPeriod)
        assertEquals(2000000L, subscription.pendingPlans[0].effectiveAt)
        assertEquals("scheduled", subscription.pendingPlans[0].status)
    }

    @Test
    fun whenPurchaseFailedThenPurchaseCheckedAndWaitingEmit() = runTest {
        givenUserIsSignedIn()
        givenConfirmPurchaseFails()

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(playBillingManager.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(Purchased("validateToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Waiting)
            cancelAndConsumeRemainingEvents()
        }

        manager.subscriptionStatus.test {
            flowTest.emit(Purchased("validateToken", "packageName"))
            assertEquals(WAITING, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseCanceledThenEmitCanceled() = runTest {
        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(playBillingManager.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(Canceled)
            assertTrue(awaitItem() is CurrentPurchase.Canceled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFailedThenEmitFailure() = runTest {
        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(playBillingManager.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(Failure("BILLING_UNAVAILABLE"))
            val result = awaitItem()
            assertTrue(result is CurrentPurchase.Failure)
            assertEquals("BILLING_UNAVAILABLE", (result as CurrentPurchase.Failure).message)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetAccessTokenIfUserIsSignedInThenReturnSuccess() = runTest {
        givenUserIsSignedIn()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        val actualAccessToken = (result as AccessTokenResult.Success).accessToken
        assertEquals(FAKE_ACCESS_TOKEN_V2, actualAccessToken)
    }

    @Test
    fun whenGetAccessTokenIfUserIsSignedInThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredThenGetNewTokenAndReturnSuccess() = runTest {
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertEquals("new access token", (result as AccessTokenResult.Success).accessToken)
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredAndRefreshFailsThenGetNewTokenAndReturnFailure() = runTest {
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredAndRefreshFailsWithAuthErrorThenGetNewTokenUsingStoreLoginAndReturnSuccess() = runTest {
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails(errorCode = "invalid_token")
        givenPurchaseStored()
        givenStoreLoginSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertEquals("new access token", (result as AccessTokenResult.Success).accessToken)
        verify(pixelSender).reportAuthV2InvalidRefreshTokenDetected()
        verify(pixelSender).reportAuthV2InvalidRefreshTokenRecovered()
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredAndRefreshFailsWithAuthErrorAndStoreRecoveryNotPossibleThenSignOutAndReturnFailure() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionExists()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails(errorCode = "invalid_token")
        givenPurchaseStored()
        givenStoreLoginFails()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        assertFalse(subscriptionsManager.isSignedIn())
        assertNull(authRepository.getAccessTokenV2())
        assertNull(authRepository.getRefreshTokenV2())
        assertNull(authRepository.getAccount())
        assertNull(authRepository.getSubscription())
        verify(pixelSender).reportAuthV2InvalidRefreshTokenDetected()
        verify(pixelSender).reportAuthV2InvalidRefreshTokenSignedOut()
    }

    @Test
    fun whenGetAccessTokenIfAccessTokenIsExpiredAndRefreshFailsWithUnknownAccountErrorThenSignOutAndReturnFailure() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionExists()
        givenAccessTokenIsExpired()

        // Simulating the scenario where account was removed from BE.
        givenV2AccessTokenRefreshFails(errorCode = "unknown_account")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)

        // Verify user was signed out.
        assertFalse(subscriptionsManager.isSignedIn())
        assertNull(authRepository.getAccessTokenV2())
        assertNull(authRepository.getRefreshTokenV2())
        assertNull(authRepository.getAccount())
        assertNull(authRepository.getSubscription())

        // Store login has 0 chance of success when account doesn't exist, so there should be no attempt.
        verify(authClient, never()).authorize(any())
        verify(authClient, never()).storeLogin(any(), any(), any())

        // This isn't the case of invalid refresh token, so the related pixels should not be sent.
        verify(pixelSender, never()).reportAuthV2InvalidRefreshTokenDetected()
        verify(pixelSender, never()).reportAuthV2InvalidRefreshTokenSignedOut()
        verify(pixelSender, never()).reportAuthV2InvalidRefreshTokenRecovered()
    }

    @Test
    @SuppressLint("DenyListedApi")
    fun whenSerializeTokenRefreshDisabledThenRefreshDoesNotUseCrossProcessLock() = runTest {
        subscriptionsFeature.serializeTokenRefresh().setRawStoredState(State(false))
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        verifyNoInteractions(crossProcessLock)
        verify(tokenRefreshWideEvent).onStart(any(), eq(false))
        verify(tokenRefreshWideEvent, never()).onCrossProcessLockAcquired(any())
    }

    @Test
    fun whenRefreshSucceedsThenCrossProcessLockIsReleased() = runTest {
        val lockHandle = FakeLockHandle()
        val acquireResult = Result.success(lockHandle)
        whenever(crossProcessLock.acquire(any(), any())).thenReturn(acquireResult)
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertTrue(lockHandle.closed)
        verify(tokenRefreshWideEvent).onCrossProcessLockAcquired(acquireResult)
    }

    @Test
    fun whenRefreshFailsThenCrossProcessLockIsReleased() = runTest {
        val lockHandle = FakeLockHandle()
        whenever(crossProcessLock.acquire(any(), any())).thenReturn(Result.success(lockHandle))
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshFails()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        assertTrue(lockHandle.closed)
    }

    @Test
    fun whenCrossProcessLockAcquisitionTimesOutThenRefreshStillRuns() = runTest {
        val acquireResult = Result.failure<Closeable>(timeoutCancellationException())
        whenever(crossProcessLock.acquire(any(), any())).thenReturn(acquireResult)
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        verify(tokenRefreshWideEvent).onCrossProcessLockAcquired(acquireResult)
    }

    @Test
    fun whenCrossProcessLockAcquisitionFailsThenRefreshStillRuns() = runTest {
        val acquireResult = Result.failure<Closeable>(IOException())
        whenever(crossProcessLock.acquire(any(), any())).thenReturn(acquireResult)
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshSucceeds(newAccessToken = "new access token")

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        verify(tokenRefreshWideEvent).onCrossProcessLockAcquired(acquireResult)
    }

    @Test
    fun whenTokenRefreshesRunConcurrentlyThenTheyDoNotOverlap() = runTest {
        givenUserIsSignedIn()
        givenValidateV2TokensSucceeds()
        whenever(authClient.getJwks()).thenReturn("fake jwks")

        var concurrentCalls = 0
        var maxConcurrentCalls = 0
        whenever(authClient.getTokens(any())).doSuspendableAnswer {
            concurrentCalls++
            maxConcurrentCalls = maxOf(maxConcurrentCalls, concurrentCalls)
            yield()
            concurrentCalls--
            TokenPair(FAKE_ACCESS_TOKEN_V2, FAKE_REFRESH_TOKEN_V2)
        }

        listOf(
            launch { subscriptionsManager.refreshAccessToken() },
            launch { subscriptionsManager.refreshAccessToken() },
        ).joinAll()

        assertEquals(1, maxConcurrentCalls)
    }

    @Test
    fun whenCallerIsCancelledMidRefreshThenRefreshCompletes() = runTest {
        givenUserIsSignedIn()
        givenValidateV2TokensSucceeds()
        whenever(authClient.getJwks()).thenReturn("fake jwks")

        val tokenRequestStarted = CompletableDeferred<Unit>()
        val tokenRequestBlocker = CompletableDeferred<Unit>()
        whenever(authClient.getTokens(any())).doSuspendableAnswer {
            tokenRequestStarted.complete(Unit)
            tokenRequestBlocker.await()
            TokenPair("refreshed access token", "refreshed refresh token")
        }

        val job = launch { subscriptionsManager.refreshAccessToken() }
        tokenRequestStarted.await()
        job.cancel()
        tokenRequestBlocker.complete(Unit)
        job.join()
        advanceUntilIdle() // the refresh continues in the app scope after the caller is cancelled

        assertEquals("refreshed access token", authRepository.getAccessTokenV2()?.jwt)
        verify(tokenRefreshWideEvent).onSuccess()
    }

    @Test
    fun whenGetAccessTokenIfSignedInWithV1ThenExchangesTokenForV2AndReturnsTrue() = runTest {
        givenUserIsSignedIn(useAuthV2 = false)
        givenV1AccessTokenExchangeSuccess()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Success)
        assertEquals(FAKE_ACCESS_TOKEN_V2, (result as AccessTokenResult.Success).accessToken)
        assertEquals(FAKE_ACCESS_TOKEN_V2, authRepository.getAccessTokenV2()?.jwt)
        assertEquals(FAKE_REFRESH_TOKEN_V2, authRepository.getRefreshTokenV2()?.jwt)
        assertNull(authRepository.getAccessToken())
        assertNull(authRepository.getAuthToken())
        verify(pixelSender).reportAuthV2MigrationSuccess()
    }

    @Test
    fun whenSubscriptionIsRefreshedAndUserSignedInWithV1ThenMigratesToAuthV2() = runTest {
        givenUserIsSignedIn(useAuthV2 = false)
        givenV1AccessTokenExchangeSuccess()

        whenever(subscriptionsService.subscription()).thenAnswer {
            runBlocking { subscriptionsManager.getAccessToken() } // the auth interceptor triggers the v1 -> v2 migration

            SubscriptionResponse(
                productId = MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = "Auto-Renewable",
                activeOffers = listOf(),
            )
        }

        subscriptionsManager.refreshSubscriptionData()

        assertEquals(FAKE_ACCESS_TOKEN_V2, authRepository.getAccessTokenV2()?.jwt)
        assertEquals(FAKE_REFRESH_TOKEN_V2, authRepository.getRefreshTokenV2()?.jwt)
        assertNull(authRepository.getAccessToken())
        assertNull(authRepository.getAuthToken())
        assertNotNull(subscriptionsManager.getSubscription())
    }

    @Test
    fun whenGetAccessTokenIfSignedInWithV1AndMigrationToV2FailsOnUnknownAccountErrorThenSignsOut() = runTest {
        givenUserIsSignedIn(useAuthV2 = false)
        givenV1AccessTokenExchangeFailsWithInvalidTokenError()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        assertFalse(subscriptionsManager.isSignedIn())
        assertNull(authRepository.getAccessTokenV2())
        assertNull(authRepository.getRefreshTokenV2())
        assertNull(authRepository.getAccount())
        assertNull(authRepository.getSubscription())
        verify(pixelSender).reportAuthV2MigrationFailureInvalidToken()
    }

    @Test
    fun whenGetAuthTokenIfUserSignedInAndValidTokenThenReturnSuccess() = runTest {
        givenUserIsSignedIn()

        val result = subscriptionsManager.getAuthToken()

        assertTrue(result is AuthTokenResult.Success)

        val actualAuthToken = (result as AuthTokenResult.Success).authToken
        assertEquals(FAKE_ACCESS_TOKEN_V2, actualAuthToken)
    }

    @Test
    fun whenGetAuthTokenIfUserNotSignedInThenReturnFailure() = runTest {
        givenUserIsNotSignedIn()

        val result = subscriptionsManager.getAuthToken()

        assertTrue(result is AuthTokenResult.Failure)
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
        whenever(playBillingManager.purchaseState).thenReturn(flowOf())
        val manager = RealSubscriptionsManager(
            subscriptionsService,
            mockRepo,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
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
        whenever(playBillingManager.purchaseState).thenReturn(flowOf())

        val manager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
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
        givenConfirmPurchaseSucceeds()
        givenV2AccessTokenRefreshSucceeds()

        whenever(playBillingManager.purchaseState).thenReturn(flowOf(Purchased("any", "any")))

        subscriptionsManager.currentPurchaseState.test {
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)

            verify(pixelSender).reportPurchaseSuccess(isFreeTrial = false)
            verify(pixelSender).reportSubscriptionActivated()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseIsSuccessfulWithFreeTrialThenPixelIsSentWithFreeTrialTrue() = runTest {
        givenUserIsSignedIn()
        givenConfirmPurchaseSucceedsWithFreeTrial()
        givenV2AccessTokenRefreshSucceeds()

        whenever(playBillingManager.purchaseState).thenReturn(flowOf(Purchased("any", "any")))

        subscriptionsManager.currentPurchaseState.test {
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)

            verify(pixelSender).reportPurchaseSuccess(isFreeTrial = true)
            verify(pixelSender).reportSubscriptionActivated()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSubscriptionIsRestoredOnPurchaseAttemptThenPixelIsSent() = runTest {
        givenUserIsNotSignedIn()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()

        subscriptionsManager.currentPurchaseState.test {
            purchase()
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
        givenConfirmPurchaseFails()

        whenever(playBillingManager.purchaseState).thenReturn(flowOf(Purchased("validateToken", "packageName")))

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
            purchase()
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)

            verify(pixelSender).reportPurchaseFailureAccountCreation()
            verify(pixelSender).reportPurchaseFailureOther("PURCHASE_EXCEPTION", "An error happened")
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetSubscriptionOfferThenReturnValue() = runTest {
        authRepository.setFeatures(MONTHLY_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PLAN_US, setOf(NETP))
        givenPlansAvailable(MONTHLY_PLAN_US, YEARLY_PLAN_US)

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertTrue(any { it.planId == MONTHLY_PLAN_US })
            assertEquals("1$", find { it.planId == MONTHLY_PLAN_US }?.pricingPhases?.first()?.formattedPrice)
            assertTrue(any { it.planId == YEARLY_PLAN_US })
            assertEquals("1$", find { it.planId == YEARLY_PLAN_US }?.pricingPhases?.first()?.formattedPrice)
            assertEquals(setOf(NETP), first().features)
        }
    }

    @Test
    fun whenGetSubscriptionOfferAndNoFeaturesThenReturnEmptyList() = runTest {
        authRepository.setFeatures(MONTHLY_PLAN_US, emptySet())
        authRepository.setFeatures(YEARLY_PLAN_US, emptySet())
        givenPlansAvailable(MONTHLY_PLAN_US, YEARLY_PLAN_US)

        assertEquals(emptyList<SubscriptionOfferDetails>(), subscriptionsManager.getSubscriptionOffer())
    }

    @Test
    fun whenGetSubscriptionOfferAndRowPlansAvailableThenReturnValue() = runTest {
        authRepository.setFeatures(MONTHLY_PLAN_ROW, setOf(NETP))
        authRepository.setFeatures(YEARLY_PLAN_ROW, setOf(NETP))
        givenPlansAvailable(MONTHLY_PLAN_ROW, YEARLY_PLAN_ROW)

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertTrue(any { it.planId == MONTHLY_PLAN_ROW })
            assertEquals("1$", find { it.planId == MONTHLY_PLAN_ROW }?.pricingPhases?.first()?.formattedPrice)
            assertTrue(any { it.planId == YEARLY_PLAN_ROW })
            assertEquals("1$", find { it.planId == YEARLY_PLAN_ROW }?.pricingPhases?.first()?.formattedPrice)
            assertEquals(setOf(NETP), first().features)
        }
    }

    @Test
    fun whenGetSubscriptionOfferWithTierMessagingEnabledThenReturnEntitlementsFromV2() = runTest {
        givenTierMessagingEnabled(true)
        authRepository.setFeaturesV2(
            MONTHLY_PLAN_US,
            setOf(Entitlement(name = "plus", product = NETP)),
        )
        authRepository.setFeaturesV2(
            YEARLY_PLAN_US,
            setOf(Entitlement(name = "plus", product = NETP)),
        )
        givenPlansAvailable(MONTHLY_PLAN_US, YEARLY_PLAN_US)

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertTrue(isNotEmpty())
            assertTrue(any { it.planId == MONTHLY_PLAN_US })
            assertTrue(any { it.planId == YEARLY_PLAN_US })
            assertEquals("plus", first().tier)
            assertEquals(setOf(Entitlement(name = "plus", product = NETP)), first().entitlements)
            assertEquals(setOf(NETP), first().features)
        }
    }

    @Test
    fun whenGetSubscriptionOfferWithTierMessagingEnabledAndV2EmptyThenFallbackToV1() = runTest {
        givenTierMessagingEnabled(true)
        // V2 is empty, but V1 has data
        authRepository.setFeatures(MONTHLY_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PLAN_US, setOf(NETP))
        givenPlansAvailable(MONTHLY_PLAN_US, YEARLY_PLAN_US)

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertTrue(isNotEmpty())
            assertTrue(any { it.planId == MONTHLY_PLAN_US })
            // When falling back to V1, tier defaults to "plus"
            assertEquals("plus", first().tier)
            // Entitlements are created from V1 features with name="plus"
            assertEquals(setOf(Entitlement(name = "plus", product = NETP)), first().entitlements)
            assertEquals(setOf(NETP), first().features)
        }
    }

    @Test
    fun whenGetSubscriptionOfferWithTierMessagingDisabledThenUseV1Features() = runTest {
        givenTierMessagingEnabled(false)
        authRepository.setFeatures(MONTHLY_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PLAN_US, setOf(NETP))
        givenPlansAvailable(MONTHLY_PLAN_US, YEARLY_PLAN_US)

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertTrue(isNotEmpty())
            assertTrue(any { it.planId == MONTHLY_PLAN_US })
            // When flag OFF, tier defaults to "plus"
            assertEquals("plus", first().tier)
            // Entitlements created from V1 features
            assertEquals(setOf(Entitlement(name = "plus", product = NETP)), first().entitlements)
            assertEquals(setOf(NETP), first().features)
        }
    }

    @Test
    fun whenGetSubscriptionOfferWithTierMessagingEnabledAndBothStoragesEmptyThenReturnEmptyList() = runTest {
        givenTierMessagingEnabled(true)
        // Both V1 and V2 are empty
        givenPlansAvailable(MONTHLY_PLAN_US, YEARLY_PLAN_US)

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        assertTrue(subscriptionOffers.isEmpty())
    }

    @Test
    fun whenGetSubscriptionOfferWithProTierFlagEnabledThenReturnProPlans() = runTest {
        givenAllowProTierPurchase(true)
        authRepository.setFeatures(MONTHLY_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PLAN_US, setOf(NETP))
        authRepository.setFeatures(MONTHLY_PRO_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PRO_PLAN_US, setOf(NETP))
        givenPlansAvailableForProducts(
            basicPlanIds = arrayOf(MONTHLY_PLAN_US, YEARLY_PLAN_US),
            proPlanIds = arrayOf(MONTHLY_PRO_PLAN_US, YEARLY_PRO_PLAN_US),
        )

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertTrue(any { it.planId == MONTHLY_PLAN_US })
            assertTrue(any { it.planId == YEARLY_PLAN_US })
            assertTrue(any { it.planId == MONTHLY_PRO_PLAN_US })
            assertTrue(any { it.planId == YEARLY_PRO_PLAN_US })
            assertEquals("plus", find { it.planId == MONTHLY_PLAN_US }?.tier)
            assertEquals("pro", find { it.planId == MONTHLY_PRO_PLAN_US }?.tier)
        }
    }

    @Test
    fun whenGetSubscriptionOfferWithProTierFlagDisabledThenDoNotReturnProPlans() = runTest {
        givenAllowProTierPurchase(false)
        authRepository.setFeatures(MONTHLY_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PLAN_US, setOf(NETP))
        authRepository.setFeatures(MONTHLY_PRO_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PRO_PLAN_US, setOf(NETP))
        givenPlansAvailableForProducts(
            basicPlanIds = arrayOf(MONTHLY_PLAN_US, YEARLY_PLAN_US),
            proPlanIds = arrayOf(MONTHLY_PRO_PLAN_US, YEARLY_PRO_PLAN_US),
        )

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertTrue(any { it.planId == MONTHLY_PLAN_US })
            assertTrue(any { it.planId == YEARLY_PLAN_US })
            assertFalse(any { it.planId == MONTHLY_PRO_PLAN_US })
            assertFalse(any { it.planId == YEARLY_PRO_PLAN_US })
        }
    }

    @Test
    fun whenGetSubscriptionOfferWithProTierFlagEnabledAndOnlyProPlansAvailableThenReturnOnlyProPlans() = runTest {
        givenAllowProTierPurchase(true)
        authRepository.setFeatures(MONTHLY_PRO_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PRO_PLAN_US, setOf(NETP))
        givenPlansAvailableForProducts(
            basicPlanIds = emptyArray(),
            proPlanIds = arrayOf(MONTHLY_PRO_PLAN_US, YEARLY_PRO_PLAN_US),
        )

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        with(subscriptionOffers) {
            assertEquals(2, size)
            assertTrue(any { it.planId == MONTHLY_PRO_PLAN_US })
            assertTrue(any { it.planId == YEARLY_PRO_PLAN_US })
            assertTrue(all { it.tier == "pro" })
        }
    }

    @Test
    fun whenGetSubscriptionOfferWithProTierFlagDisabledAndOnlyProPlansAvailableThenReturnEmptyList() = runTest {
        givenAllowProTierPurchase(false)
        authRepository.setFeatures(MONTHLY_PRO_PLAN_US, setOf(NETP))
        authRepository.setFeatures(YEARLY_PRO_PLAN_US, setOf(NETP))
        givenPlansAvailableForProducts(
            basicPlanIds = emptyArray(),
            proPlanIds = arrayOf(MONTHLY_PRO_PLAN_US, YEARLY_PRO_PLAN_US),
        )

        val subscriptionOffers = subscriptionsManager.getSubscriptionOffer()

        assertTrue(subscriptionOffers.isEmpty())
    }

    @Test
    fun whenCanSupportEncryptionThenReturnTrue() = runTest {
        assertTrue(subscriptionsManager.canSupportEncryption())
    }

    @Test
    fun whenCanSupportEncryptionIfCannotThenReturnFalse() = runTest {
        val authDataStore: SubscriptionsDataStore = FakeSubscriptionsDataStore(supportEncryption = false)
        val authRepository = RealAuthRepository(authDataStore, coroutineRule.testDispatcherProvider, serpPromo, { subscriptionsFeature })
        whenever(playBillingManager.purchaseState).thenReturn(flowOf())
        subscriptionsManager = RealSubscriptionsManager(
            subscriptionsService,
            authRepository,
            playBillingManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
            { subscriptionsFeature },
            authClient,
            authJwtValidator,
            pkceGenerator,
            timeProvider,
            backgroundTokenRefresh,
            crossProcessLock,
            subscriptionPurchaseWideEvent,
            tokenRefreshWideEvent,
            subscriptionSwitchWideEvent,
            freeTrialConversionWideEvent,
            subscriptionRestoreWideEvent,
            vpnReminderNotificationScheduler,
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
        givenSubscriptionExists(status = INACTIVE)

        subscriptionsManager.entitlements.test {
            val entitlements = expectMostRecentItem()
            assertTrue(entitlements.isEmpty())
        }
    }

    @Test
    fun whenSubscriptionIsActiveThenEntitlementSetEmitsRawEntitlements() = runTest {
        givenSubscriptionExists()

        subscriptionsManager.entitlementSet.test {
            assertEquals(
                setOf(Entitlement(name = "subscriber", product = NetP.value)),
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun whenSubscriptionIsInactiveThenEntitlementSetEmitsEmpty() = runTest {
        givenSubscriptionExists(status = INACTIVE)

        subscriptionsManager.entitlementSet.test {
            assertTrue(expectMostRecentItem().isEmpty())
        }
    }

    @Test
    fun whenSignOutThenEntitlementSetReEmitsEmpty() = runTest {
        givenSubscriptionExists()
        givenUserIsSignedIn()

        subscriptionsManager.entitlementSet.test {
            assertFalse(expectMostRecentItem().isEmpty())
            subscriptionsManager.signOut()
            assertTrue(expectMostRecentItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenMultipleEntitlementsExistThenAllAreEmittedInSet() = runTest {
        givenSubscriptionExists()
        authRepository.setEntitlements(
            listOf(
                Entitlement(name = "plus", product = NetP.value),
                Entitlement(name = "pro", product = "Duck.ai"),
            ),
        )

        subscriptionsManager.entitlementSet.test {
            assertEquals(
                setOf(
                    Entitlement(name = "plus", product = NetP.value),
                    Entitlement(name = "pro", product = "Duck.ai"),
                ),
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun whenValidateTokenFailsThenPixelIsSent() = runTest {
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()

        whenever(authClient.getTokens(any<String>()))
            .thenReturn(TokenPair(FAKE_ACCESS_TOKEN_V2, FAKE_REFRESH_TOKEN_V2))
        whenever(authClient.getJwks()).thenReturn("fake jwks")
        whenever(authJwtValidator.validateAccessToken(any<String>(), any<String>())).thenThrow(RuntimeException::class.java)
        whenever(authJwtValidator.validateRefreshToken(any<String>(), any<String>())).thenThrow(RuntimeException::class.java)

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        verify(pixelSender).reportAuthV2TokenValidationError()
    }

    @Test
    fun whenStoringTokenFailsThenPixelIsSent() = runTest {
        givenUserIsSignedIn()
        givenAccessTokenIsExpired()
        givenV2AccessTokenRefreshSucceeds()
        authDataStore.simluateAccessTokenV2StoreError = true

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessTokenResult.Failure)
        verify(pixelSender).reportAuthV2TokenStoreError()
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
        whenever(subscriptionsService.subscription()).thenReturn(
            SubscriptionResponse(
                productId = MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = status,
                activeOffers = listOf(),
            ),
        )
    }

    private suspend fun givenSubscriptionSucceedsWithEntitlements(status: String = "Auto-Renewable") {
        whenever(subscriptionsService.subscription()).thenReturn(
            SubscriptionResponse(
                productId = MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = status,
                activeOffers = listOf(),
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

    private fun givenUserIsSignedIn(useAuthV2: Boolean = true, accountExternalId: String = "1234") {
        if (useAuthV2) {
            authDataStore.accessTokenV2 = FAKE_ACCESS_TOKEN_V2
            authDataStore.accessTokenV2ExpiresAt = timeProvider.currentTime + Duration.ofHours(4)
            authDataStore.refreshTokenV2 = FAKE_REFRESH_TOKEN_V2
            authDataStore.refreshTokenV2ExpiresAt = timeProvider.currentTime + Duration.ofDays(30)
        } else {
            authDataStore.accessToken = "accessToken"
            authDataStore.authToken = "authToken"
        }
        authDataStore.externalId = accountExternalId
    }

    private suspend fun givenCreateAccountFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authClient.authorize(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
        whenever(authClient.createAccount(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenCreateAccountSucceeds() {
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
        authDataStore.billingPeriod = "Monthly"
        authDataStore.startedAt = 1000L
        authDataStore.expiresOrRenewsAt = 1000L
    }

    private suspend fun givenStoreLoginFails() {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authClient.authorize(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
        whenever(authClient.storeLogin(any(), any(), any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenPurchaseStored() {
        val purchase: Purchase = mock {
            whenever(it.signature).thenReturn("signature")
            whenever(it.originalJson).thenReturn("originalJson")
        }
        whenever(playBillingManager.products).thenReturn(emptyList())
        whenever(playBillingManager.getLatestPurchase()).thenReturn(LatestPurchaseResult.Present(purchase))
    }

    private suspend fun givenActivePurchase() {
        val purchase: Purchase = mock {
            whenever(it.signature).thenReturn("signature")
            whenever(it.originalJson).thenReturn("originalJson")
        }
        whenever(playBillingManager.getLatestPurchase()).thenReturn(LatestPurchaseResult.Present(purchase))
    }

    private suspend fun givenNoActivePurchase() {
        whenever(playBillingManager.getLatestPurchase()).thenReturn(LatestPurchaseResult.Absent)
    }

    private suspend fun givenPurchaseInfoUnknown(cause: String = "billing_client_not_ready") {
        whenever(playBillingManager.getLatestPurchase()).thenReturn(LatestPurchaseResult.Unknown(cause = cause))
    }

    private suspend fun givenStoreLoginSucceeds(newAccessToken: String = FAKE_ACCESS_TOKEN_V2) {
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

    private suspend fun givenV1AccessTokenExchangeFailsWithInvalidTokenError() {
        whenever(authClient.getJwks()).thenReturn("fake jwks")
        whenever(authClient.authorize(any())).thenReturn("fake session id")
        val errorResponseBody = """{"error":"invalid_token"}""".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authClient.exchangeV1AccessToken(any(), any())).thenThrow(HttpException(Response.error<String>(400, errorResponseBody)))
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
                    billingPeriod = "Monthly",
                    platform = "google",
                    status = "Auto-Renewable",
                    startedAt = 1000000L,
                    expiresOrRenewsAt = 1000000L,
                    activeOffers = listOf(),
                ),
            ),
        )
    }

    private suspend fun givenConfirmPurchaseSucceedsWithFreeTrial() {
        whenever(subscriptionsService.confirm(any())).thenReturn(
            ConfirmationResponse(
                email = "test@duck.com",
                entitlements = listOf(
                    ConfirmationEntitlement(NetP.value, NetP.value),
                ),
                subscription = SubscriptionResponse(
                    productId = "id",
                    billingPeriod = "Monthly",
                    platform = "google",
                    status = "Auto-Renewable",
                    startedAt = 1000000L,
                    expiresOrRenewsAt = 1000000L,
                    activeOffers = listOf(ActiveOfferResponse("Trial")),
                ),
            ),
        )
    }

    private suspend fun givenConfirmPurchaseSucceedsWithPendingPlan() {
        whenever(subscriptionsService.confirm(any())).thenReturn(
            ConfirmationResponse(
                email = "test@duck.com",
                entitlements = listOf(
                    ConfirmationEntitlement(NetP.value, NetP.value),
                ),
                subscription = SubscriptionResponse(
                    productId = MONTHLY_PLAN_US,
                    billingPeriod = "Monthly",
                    platform = "google",
                    status = "Auto-Renewable",
                    startedAt = 1000000L,
                    expiresOrRenewsAt = 1000000L,
                    activeOffers = listOf(),
                    pendingPlans = listOf(
                        PendingPlanResponse(
                            productId = YEARLY_PLAN_US,
                            billingPeriod = "Yearly",
                            effectiveAt = 2000000L,
                            status = "scheduled",
                            tier = "plus",
                        ),
                    ),
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

    private suspend fun givenV2AccessTokenRefreshFails(errorCode: String? = null) {
        val exception = if (errorCode != null) {
            val responseBody = """{"error":"$errorCode"}""".toResponseBody("text/json".toMediaTypeOrNull())
            HttpException(Response.error<Void>(400, responseBody))
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

    private fun givenPlansAvailable(vararg basePlanIds: String) {
        givenPlansAvailableForProducts(
            basicPlanIds = basePlanIds.filter { it in SubscriptionsConstants.LIST_OF_PLUS_PLANS }.toTypedArray(),
            proPlanIds = basePlanIds.filter { it in SubscriptionsConstants.LIST_OF_PRO_PLANS }.toTypedArray(),
        )
    }

    private fun givenPlansAvailableForProducts(
        basicPlanIds: Array<out String> = emptyArray(),
        proPlanIds: Array<out String> = emptyArray(),
    ) {
        val products = mutableListOf<ProductDetails>()

        if (basicPlanIds.isNotEmpty()) {
            val basicProductDetails: ProductDetails = mock { productDetails ->
                whenever(productDetails.productId).thenReturn(SubscriptionsConstants.BASIC_SUBSCRIPTION)

                val mockPricingPhase: PricingPhase = mock {
                    on { priceAmountMicros } doReturn 1000000
                    on { priceCurrencyCode } doReturn "USD"
                    on { formattedPrice } doReturn "1$"
                    on { billingPeriod } doReturn "P1M"
                }

                val pricingPhaseList: List<PricingPhase> = listOf(mockPricingPhase)

                val pricingPhases: PricingPhases = mock { pricingPhases ->
                    whenever(pricingPhases.pricingPhaseList).thenReturn(pricingPhaseList)
                }

                val offers = basicPlanIds.map { basePlanId ->
                    mock<SubscriptionOfferDetails> { offer ->
                        whenever(offer.basePlanId).thenReturn(basePlanId)
                        whenever(offer.pricingPhases).thenReturn(pricingPhases)
                    }
                }

                whenever(productDetails.subscriptionOfferDetails).thenReturn(offers)
            }
            products.add(basicProductDetails)
        }

        if (proPlanIds.isNotEmpty()) {
            val proProductDetails: ProductDetails = mock { productDetails ->
                whenever(productDetails.productId).thenReturn(ADVANCED_SUBSCRIPTION)

                val mockPricingPhase: PricingPhase = mock {
                    on { priceAmountMicros } doReturn 2000000
                    on { priceCurrencyCode } doReturn "USD"
                    on { formattedPrice } doReturn "2$"
                    on { billingPeriod } doReturn "P1M"
                }

                val pricingPhaseList: List<PricingPhase> = listOf(mockPricingPhase)

                val pricingPhases: PricingPhases = mock { pricingPhases ->
                    whenever(pricingPhases.pricingPhaseList).thenReturn(pricingPhaseList)
                }

                val offers = proPlanIds.map { basePlanId ->
                    mock<SubscriptionOfferDetails> { offer ->
                        whenever(offer.basePlanId).thenReturn(basePlanId)
                        whenever(offer.pricingPhases).thenReturn(pricingPhases)
                    }
                }

                whenever(productDetails.subscriptionOfferDetails).thenReturn(offers)
            }
            products.add(proProductDetails)
        }

        whenever(playBillingManager.products).thenReturn(products)
    }

    @Test
    fun whenSwitchSubscriptionPlanWithNoActiveSubscriptionThenEmitFailure() = runTest {
        givenUserIsSignedIn()
        givenNoActiveSubscription()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.switchSubscriptionPlan(
                activity = mock(),
                planId = YEARLY_PLAN_US,
                offerId = null,
                replacementMode = SubscriptionReplacementMode.DEFERRED,
            )

            val item = awaitItem()
            assertTrue(item is CurrentPurchase.Failure)
            assertEquals("No active subscription found for switch", (item as CurrentPurchase.Failure).message)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSwitchSubscriptionPlanWithNoPurchaseTokenThenEmitFailure() = runTest {
        givenUserIsSignedIn()
        givenActiveSubscription()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.switchSubscriptionPlan(
                activity = mock(),
                planId = YEARLY_PLAN_US,
                offerId = null,
                replacementMode = SubscriptionReplacementMode.DEFERRED,
            )

            val item = awaitItem()
            assertTrue(item is CurrentPurchase.Failure)
            assertEquals("No current purchase token found for switch", (item as CurrentPurchase.Failure).message)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSwitchSubscriptionPlanWithNoAccountThenEmitFailure() = runTest {
        givenUserIsSignedIn()
        givenActiveSubscription()
        givenPurchaseStored()
        whenever(playBillingManager.getLatestPurchaseToken()).thenReturn("validToken")
        authRepository.setAccount(null)

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.switchSubscriptionPlan(
                activity = mock(),
                planId = YEARLY_PLAN_US,
                offerId = null,
                replacementMode = SubscriptionReplacementMode.DEFERRED,
            )

            val item = awaitItem()
            assertTrue(item is CurrentPurchase.Failure)
            assertEquals("No account found for switch", (item as CurrentPurchase.Failure).message)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSwitchSubscriptionPlanWithUserNotSignedInThenEmitFailure() = runTest {
        givenActiveSubscription()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.switchSubscriptionPlan(
                activity = mock(),
                planId = YEARLY_PLAN_US,
                offerId = null,
                replacementMode = SubscriptionReplacementMode.DEFERRED,
            )

            val item = awaitItem()
            assertTrue(item is CurrentPurchase.Failure)
            assertEquals("User not signed in for switch", (item as CurrentPurchase.Failure).message)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSwitchSubscriptionPlanWithInvalidPlanIdThenEmitFailure() = runTest {
        givenUserIsSignedIn()
        givenActiveSubscription()
        givenPurchaseStored()
        whenever(playBillingManager.getLatestPurchaseToken()).thenReturn("validToken")
        authRepository.setAccount(Account("test@test.com", "1234"))
        authRepository.setFeatures(MONTHLY_PLAN_US, setOf(NETP))
        givenPlansAvailable(MONTHLY_PLAN_US)

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.switchSubscriptionPlan(
                activity = mock(),
                planId = "invalid_plan",
                offerId = null,
                replacementMode = SubscriptionReplacementMode.DEFERRED,
            )

            val item = awaitItem()
            assertTrue(item is CurrentPurchase.Failure)
            assertTrue((item as CurrentPurchase.Failure).message.contains("Target plan not found"))
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSwitchSubscriptionPlanWithValidDataThenLaunchesSubscriptionUpdate() = runTest {
        givenUserIsSignedIn()
        givenActiveSubscription()
        givenPurchaseStored()
        whenever(playBillingManager.getLatestPurchaseToken()).thenReturn("validToken")
        authRepository.setAccount(Account("test@test.com", "1234"))
        authRepository.setFeatures(YEARLY_PLAN_US, setOf(NETP))
        givenPlansAvailable(YEARLY_PLAN_US)

        subscriptionsManager.switchSubscriptionPlan(
            activity = mock(),
            planId = YEARLY_PLAN_US,
            offerId = null,
            replacementMode = SubscriptionReplacementMode.DEFERRED,
        )

        verify(playBillingManager).launchSubscriptionUpdate(
            activity = any(),
            newPlanId = eq(YEARLY_PLAN_US),
            externalId = eq("1234"),
            newOfferId = isNull(),
            oldPurchaseToken = eq("validToken"),
            replacementMode = eq(SubscriptionReplacementMode.DEFERRED),
        )
    }

    @Test
    fun whenBlackFridayOfferAvailableWithFeatureFlagEnabledThenReturnTrue() = runTest {
        givenBlackFridayFeatureFlagEnabled(true)

        val result = subscriptionsManager.blackFridayOfferAvailable()

        assertTrue(result)
    }

    @Test
    fun whenBlackFridayOfferAvailableWithFeatureFlagDisabledThenReturnFalse() = runTest {
        givenBlackFridayFeatureFlagEnabled(false)

        val result = subscriptionsManager.blackFridayOfferAvailable()

        assertFalse(result)
    }

    @SuppressLint("DenyListedApi")
    private fun givenBlackFridayFeatureFlagEnabled(value: Boolean) {
        subscriptionsFeature.blackFridayOffer2025().setRawStoredState(State(remoteEnableState = value))
    }

    @Test
    fun whenRefreshSubscriptionDataWithPendingPlansThenStoresPendingPlans() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithPendingPlans()

        subscriptionsManager.refreshSubscriptionData()

        val subscription = subscriptionsManager.getSubscription()
        assertNotNull(subscription)
        assertEquals(1, subscription!!.pendingPlans.size)
        assertEquals("ddg-privacy-pro-yearly-renews-us", subscription.pendingPlans[0].productId)
        assertEquals(SubscriptionTier.PLUS, subscription.pendingPlans[0].tier)
        assertTrue(subscription.hasPendingChange)
    }

    @Test
    fun whenRefreshSubscriptionDataWithNoPendingPlansThenStoresEmptyList() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithNoPendingPlans()

        subscriptionsManager.refreshSubscriptionData()

        val subscription = subscriptionsManager.getSubscription()
        assertNotNull(subscription)
        assertTrue(subscription!!.pendingPlans.isEmpty())
        assertFalse(subscription.hasPendingChange)
    }

    @Test
    fun whenSubscriptionHasPendingPlanThenEffectiveTierReflectsPendingTier() = runTest {
        givenUserIsSignedIn()
        givenSubscriptionSucceedsWithPendingPlans()

        subscriptionsManager.refreshSubscriptionData()

        val subscription = subscriptionsManager.getSubscription()
        assertNotNull(subscription)
        // Current tier is based on productId
        assertEquals(SubscriptionTier.PLUS, subscription!!.tier)
        // Effective tier reflects the pending plan's tier
        assertEquals(SubscriptionTier.PLUS, subscription.effectiveTier)
    }

    private suspend fun givenSubscriptionSucceedsWithPendingPlans() {
        whenever(subscriptionsService.subscription()).thenReturn(
            SubscriptionResponse(
                productId = MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = "Auto-Renewable",
                activeOffers = listOf(),
                pendingPlans = listOf(
                    PendingPlanResponse(
                        productId = "ddg-privacy-pro-yearly-renews-us",
                        billingPeriod = "yearly",
                        effectiveAt = 1700000000000L,
                        status = "scheduled",
                        tier = "plus",
                    ),
                ),
            ),
        )
    }

    private suspend fun givenSubscriptionSucceedsWithNoPendingPlans() {
        whenever(subscriptionsService.subscription()).thenReturn(
            SubscriptionResponse(
                productId = MONTHLY_PLAN_US,
                billingPeriod = "Monthly",
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = "Auto-Renewable",
                activeOffers = listOf(),
                pendingPlans = emptyList(),
            ),
        )
    }

    private suspend fun purchase(
        planId: String = "",
        offerId: String? = null,
        experimentName: String? = null,
        experimentCohort: String? = null,
    ) {
        subscriptionsManager.purchase(
            mock(),
            planId = planId,
            offerId = offerId,
            experimentCohort = experimentCohort,
            experimentName = experimentName,
            origin = null,
        )
    }

    @SuppressLint("DenyListedApi")
    private fun givenTierMessagingEnabled(value: Boolean) {
        subscriptionsFeature.tierMessagingEnabled().setRawStoredState(State(remoteEnableState = value))
    }

    @SuppressLint("DenyListedApi")
    private fun givenAllowProTierPurchase(value: Boolean) {
        subscriptionsFeature.allowProTierPurchase().setRawStoredState(State(remoteEnableState = value))
    }

    private suspend fun givenActiveSubscription() {
        authRepository.setSubscription(
            Subscription(
                productId = "ddg_privacy_pro",
                billingPeriod = "P1M",
                startedAt = 1234L,
                expiresOrRenewsAt = 1234L,
                status = AUTO_RENEWABLE,
                platform = "android",
                activeOffers = emptyList(),
            ),
        )
    }

    private suspend fun givenNoActiveSubscription() {
        authRepository.setSubscription(null)
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var currentTime: Instant = Instant.parse("2024-10-28T00:00:00Z")

        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
    }

    // TimeoutCancellationException has an internal constructor, so a real instance is obtained via withTimeout
    private suspend fun timeoutCancellationException(): TimeoutCancellationException = try {
        withTimeout(1) { awaitCancellation() }
    } catch (e: TimeoutCancellationException) {
        e
    }

    private companion object {
        const val FAKE_ACCESS_TOKEN_V2 = "fake access token"
        const val FAKE_REFRESH_TOKEN_V2 = "fake refresh token"
    }
}

private class FakeLockHandle : Closeable {
    var closed = false
    override fun close() {
        closed = true
    }
}
