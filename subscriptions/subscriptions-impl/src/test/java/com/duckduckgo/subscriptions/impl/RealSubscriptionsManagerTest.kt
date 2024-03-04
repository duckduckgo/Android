package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AutoRenewable
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.Expired
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.GracePeriod
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.Inactive
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.NotAutoRenewable
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.Unknown
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsData.Failure
import com.duckduckgo.subscriptions.impl.SubscriptionsData.Success
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.FakeSubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.repository.RealAuthRepository
import com.duckduckgo.subscriptions.impl.services.AccessTokenResponse
import com.duckduckgo.subscriptions.impl.services.AccountResponse
import com.duckduckgo.subscriptions.impl.services.AuthService
import com.duckduckgo.subscriptions.impl.services.ConfirmationResponse
import com.duckduckgo.subscriptions.impl.services.CreateAccountResponse
import com.duckduckgo.subscriptions.impl.services.Entitlement
import com.duckduckgo.subscriptions.impl.services.PortalResponse
import com.duckduckgo.subscriptions.impl.services.StoreLoginResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import com.duckduckgo.subscriptions.impl.services.ValidateTokenResponse
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class RealSubscriptionsManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authService: AuthService = mock()
    private val subscriptionsService: SubscriptionsService = mock()
    private val authDataStore: SubscriptionsDataStore = FakeSubscriptionsDataStore()
    private val authRepository = RealAuthRepository(authDataStore)
    private val emailManager: EmailManager = mock()
    private val billingClient: BillingClientWrapper = mock()
    private val billingBuilder: BillingFlowParams.Builder = mock()
    private val context: Context = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Before
    fun before() {
        whenever(emailManager.getToken()).thenReturn(null)
        whenever(context.packageName).thenReturn("packageName")
        whenever(billingClient.billingFlowParamsBuilder(any(), any(), any(), any())).thenReturn(billingBuilder)
        whenever(billingBuilder.build()).thenReturn(mock())
        subscriptionsManager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotAuthenticatedAndNotPurchaseStoredThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotAuthenticatedAndPurchaseStoredThenGetIdFromPurchase() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenAuthenticateSucceeds()
        givenValidateTokenSucceedsWithEntitlements()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        verify(authService).storeLogin(any())
        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenStoreLoginFails()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserAuthenticatedWithNotPurchasesThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenAuthenticateSucceeds()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfValidateTokenSucceedsThenReturnExternalId() = runTest {
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceedsWithEntitlements()
        givenAuthenticateSucceeds()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceedsNoEntitlements()
        givenAuthenticateSucceeds()

        subscriptionsManager.recoverSubscriptionFromStore()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetSubscriptionDataIfUserNotAuthenticatedThenReturnSuccessWithEmtpyFields() = runTest {
        givenUserIsNotAuthenticated()

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Success)
        assertNull((value as Success).email)
        assertTrue(value.entitlements.isEmpty())
        assertTrue(value.externalId.isBlank())
    }

    @Test
    fun whenGetSubscriptionDataIfTokenIsValidThenReturnSuccess() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()

        val value = subscriptionsManager.getSubscriptionData()
        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenGetSubscriptionDataIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPurchaseFlowIfUserNotAuthenticatedAndNotPurchaseStoredThenCreateAccount() = runTest {
        givenUserIsNotAuthenticated()

        subscriptionsManager.purchase(mock(), mock(), "", false)

        verify(authService).createAccount(any())
    }

    @Test
    fun whenPurchaseFlowIfUserNotAuthenticatedAndNotPurchaseStoredAndSignedInEmailThenCreateAccountWithEmailToken() = runTest {
        whenever(emailManager.getToken()).thenReturn("emailToken")
        givenUserIsNotAuthenticated()

        subscriptionsManager.purchase(mock(), mock(), "", false)

        verify(authService).createAccount("Bearer emailToken")
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountFails()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountSucceedsThenBillingFlowUsesCorrectExternalId() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()
        givenValidateTokenSucceedsNoEntitlements()
        givenAuthenticateSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)

        verify(billingClient).billingFlowParamsBuilder(any(), any(), eq("1234"), any())
        verify(billingClient).launchBillingFlow(any(), any())
    }

    @Test
    fun whenPurchaseFlowIfUserNotAuthenticatedAndPurchaseNotActiveInStoreThenGetIdFromPurchase() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceedsNoEntitlements()
        givenAuthenticateSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)

        verify(billingClient).billingFlowParamsBuilder(any(), any(), eq("1234"), any())
        verify(billingClient).launchBillingFlow(any(), any())
    }

    @Test
    fun whenPurchaseFlowIfUserNotAuthenticatedAndPurchaseActiveInStoreThenRecoverSubscription() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceedsWithEntitlements()
        givenAuthenticateSucceeds()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(billingClient, never()).billingFlowParamsBuilder(any(), any(), eq("1234"), any())
            verify(billingClient, never()).launchBillingFlow(any(), any())
            assertTrue(awaitItem() is CurrentPurchase.Recovered)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenStoreLoginFails()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfUserAuthenticatedThenValidateToken() = runTest {
        givenUserIsAuthenticated()

        subscriptionsManager.purchase(mock(), mock(), "", false)

        verify(authService).validateToken(any())
    }

    @Test
    fun whenPurchaseFlowIfValidateTokenSucceedsThenBillingFlowUsesCorrectExternalIdAndEmitStates() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsNoEntitlements()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(billingClient).billingFlowParamsBuilder(any(), any(), eq("1234"), any())
            verify(billingClient).launchBillingFlow(any(), any())
            assertTrue(awaitItem() is CurrentPurchase.PreFlowFinished)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfAccountCreatedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()
        givenValidateTokenSucceedsNoEntitlements()
        givenAuthenticateSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            assertEquals("authToken", authDataStore.authToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceedsNoEntitlements()
        givenAuthenticateSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            assertEquals("authToken", authDataStore.authToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticateIfNoAccessTokenThenReturnFailure() = runTest {
        givenAuthenticateFails()

        val value = subscriptionsManager.authenticate("authToken")

        assertTrue(value is Failure)
    }

    @Test
    fun whenAuthenticateIfAccessTokenThenSignInUserAndExchangeToken() = runTest {
        givenAuthenticateSucceeds()
        givenValidateTokenSucceedsWithEntitlements()

        subscriptionsManager.authenticate("authToken")
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            assertEquals("authToken", authDataStore.authToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticateIfAccessTokenThenReturnSuccess() = runTest {
        givenAuthenticateSucceeds()
        givenValidateTokenSucceedsWithEntitlements()

        val value = subscriptionsManager.authenticate("authToken")

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenHasSubscriptionThenHasSubscriptionEmitTrue() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.hasSubscription.test {
            assertTrue(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNoEntitlementsThenHasSubscriptionEmitFalse() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsNoEntitlements()

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.hasSubscription.test {
            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenErrorThenHasSubscriptionEmitFalse() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("error")

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.hasSubscription.test {
            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInitializeIfSubscriptionExistsThenEmitTrue() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()
        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.hasSubscription.test {
            assertTrue(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInitializeIfSubscriptionExistsThenEmitFalse() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")
        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.hasSubscription.test {
            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseSuccessfulThenPurchaseCheckedAndSuccessEmit() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()
        givenConfirmPurchaseSucceeds()

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(billingClient.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Purchased("validToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFailedThenPurchaseCheckedAndFailureEmit() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")
        givenConfirmPurchaseFails()

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(billingClient.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Purchased("validateToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetAccessTokenIfUserIsAuthenticatedThenReturnSuccess() = runTest {
        givenUserIsAuthenticated()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessToken.Success)
        assertEquals("accessToken", (result as AccessToken.Success).accessToken)
    }

    @Test
    fun whenGetAccessTokenIfUserIsAuthenticatedThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()

        val result = subscriptionsManager.getAccessToken()

        assertTrue(result is AccessToken.Failure)
    }

    @Test
    fun whenGetAuthTokenIfUserAuthenticatedAndValidTokenThenReturnSuccess() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()

        val result = subscriptionsManager.getAuthToken()

        assertTrue(result is AuthToken.Success)
        assertEquals("authToken", (result as AuthToken.Success).authToken)
    }

    @Test
    fun whenGetAuthTokenIfUserNotAuthenticatedThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()

        val result = subscriptionsManager.getAuthToken()

        assertTrue(result is AuthToken.Failure)
    }

    @Test
    fun whenGetAuthTokenIfUserAuthenticatedWithSubscriptionAndTokenExpiredAndEntitlementsExistsThenReturnSuccess() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenAuthenticateSucceeds()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthToken.Success)
        assertEquals("authToken", (result as AuthToken.Success).authToken)
    }

    @Test
    fun whenGetAuthTokenIfUserAuthenticatedWithSubscriptionAndTokenExpiredAndEntitlementsDoNotExistThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsNoEntitlements()
        givenValidateTokenFailsAndThenSucceedsWithNoEntitlements("""{ "error": "expired_token" }""")
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenAuthenticateSucceeds()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthToken.Failure)
    }

    @Test
    fun whenGetAuthTokenIfUserAuthenticatedAndTokenExpiredAndNoPurchaseInTheStoreThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")

        val result = subscriptionsManager.getAuthToken()

        verify(authService, never()).storeLogin(any())
        assertTrue(result is AuthToken.Failure)
    }

    @Test
    fun whenGetAuthTokenIfUserAuthenticatedAndTokenExpiredAndPurchaseNotValidThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")
        givenPurchaseStored()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthToken.Failure)
    }

    @Test
    fun whenGetAuthTokenIfUserAuthenticatedAndNoEntitlementsThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsNoEntitlements()
        givenPurchaseStored()

        val result = subscriptionsManager.getAuthToken()

        assertTrue(result is AuthToken.Failure)
    }

    @Test
    fun whenGetSubscriptionIfUserNotAuthenticatedThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()

        val result = subscriptionsManager.getSubscription()

        assertTrue(result is Subscription.Failure)
    }

    @Test
    fun whenGetSubscriptionIfServiceFailsThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenSubscriptionFails()
        val result = subscriptionsManager.getSubscription()

        assertTrue(result is Subscription.Failure)
    }

    @Test
    fun whenGetSubscriptionThenReturnCorrectStatus() = runTest {
        givenUserIsAuthenticated()
        givenSubscriptionSucceeds("Auto-Renewable")
        assertTrue((subscriptionsManager.getSubscription() as Subscription.Success).status is AutoRenewable)

        givenSubscriptionSucceeds("Not Auto-Renewable")
        assertTrue((subscriptionsManager.getSubscription() as Subscription.Success).status is NotAutoRenewable)

        givenSubscriptionSucceeds("Grace Period")
        assertTrue((subscriptionsManager.getSubscription() as Subscription.Success).status is GracePeriod)

        givenSubscriptionSucceeds("Inactive")
        assertTrue((subscriptionsManager.getSubscription() as Subscription.Success).status is Inactive)

        givenSubscriptionSucceeds("Expired")
        assertTrue((subscriptionsManager.getSubscription() as Subscription.Success).status is Expired)

        givenSubscriptionSucceeds("test")
        assertTrue((subscriptionsManager.getSubscription() as Subscription.Success).status is Unknown)
    }

    @Test
    fun whenGetSubscriptionThenStorePlatformValue() = runTest {
        givenUserIsAuthenticated()
        givenSubscriptionSucceeds("Auto-Renewable")

        assertNull(authDataStore.platform)
        subscriptionsManager.getSubscription()
        assertEquals("android", authDataStore.platform)
    }

    @Test
    fun whenGetPortalAndUserAuthenticatedReturnUrl() = runTest {
        givenUserIsAuthenticated()
        givenUrlPortalSucceeds()

        assertEquals("example.com", subscriptionsManager.getPortalUrl())
    }

    @Test
    fun whenGetPortalAndUserIsNotAuthenticatedReturnNull() = runTest {
        givenUserIsNotAuthenticated()

        assertNull(subscriptionsManager.getPortalUrl())
    }

    @Test
    fun whenGetPortalFailsReturnNull() = runTest {
        givenUserIsAuthenticated()
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
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )
        manager.signOut()
        verify(mockRepo).signOut()
    }

    @Test
    fun whenSignOutEmitFalseForIsSignedIn() = runTest {
        givenAuthenticateSucceeds()
        givenValidateTokenSucceedsWithEntitlements()

        subscriptionsManager.authenticate("authToken")
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            subscriptionsManager.signOut()
            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSignOutEmitFalseForHasSubscription() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()
        val manager = RealSubscriptionsManager(
            authService,
            subscriptionsService,
            authRepository,
            billingClient,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )

        manager.hasSubscription.test {
            assertTrue(awaitItem())
            manager.signOut()
            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseIsSuccessfulThenPixelIsSent() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsWithEntitlements()
        givenConfirmPurchaseSucceeds()

        whenever(billingClient.purchaseState).thenReturn(flowOf(PurchaseState.Purchased("any", "any")))

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
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceedsWithEntitlements()
        givenAuthenticateSucceeds()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
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
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")
        givenConfirmPurchaseFails()

        whenever(billingClient.purchaseState).thenReturn(flowOf(PurchaseState.Purchased("validateToken", "packageName")))

        subscriptionsManager.currentPurchaseState.test {
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)

            verify(pixelSender).reportPurchaseFailureBackend()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSubscriptionIsRecoveredFromStoreThenPixelIsSent() = runTest {
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceedsWithEntitlements()
        givenAuthenticateSucceeds()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Success)
        verify(pixelSender).reportSubscriptionActivated()
        verifyNoMoreInteractions(pixelSender)
    }

    @Test
    fun whenPurchaseFlowIfCreateAccountFailsThenPixelIsSent() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountFails()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)

            verify(pixelSender).reportPurchaseFailureAccountCreation()
            verify(pixelSender).reportPurchaseFailureOther()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
    }

    private suspend fun givenUrlPortalSucceeds() {
        whenever(subscriptionsService.portal(any())).thenReturn(PortalResponse("example.com"))
    }

    private suspend fun givenUrlPortalFails() {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(subscriptionsService.portal(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenSubscriptionFails() {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(subscriptionsService.subscription(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenSubscriptionSucceeds(status: String = "Auto-Renewable") {
        whenever(subscriptionsService.subscription(any())).thenReturn(
            SubscriptionResponse(
                productId = MONTHLY_PLAN,
                startedAt = 1234,
                expiresOrRenewsAt = 1234,
                platform = "android",
                status = status,
            ),
        )
    }

    private fun givenUserIsNotAuthenticated() {
        authDataStore.accessToken = null
        authDataStore.authToken = null
    }

    private fun givenUserIsAuthenticated() {
        authDataStore.accessToken = "accessToken"
        authDataStore.authToken = "authToken"
    }

    private suspend fun givenCreateAccountFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.createAccount(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenCreateAccountSucceeds() {
        whenever(authService.createAccount(any())).thenReturn(
            CreateAccountResponse(
                authToken = "authToken",
                externalId = "1234",
                status = "ok",
            ),
        )
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
                            Entitlement("id", "name", "testProduct"),
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
    }

    private suspend fun givenValidateTokenSucceedsWithEntitlements() {
        whenever(authService.validateToken(any())).thenReturn(
            ValidateTokenResponse(
                account = AccountResponse(
                    email = "accessToken",
                    externalId = "1234",
                    entitlements = listOf(
                        Entitlement("id", "name", "testProduct"),
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
        whenever(billingClient.products).thenReturn(mapOf())
        whenever(billingClient.purchaseHistory).thenReturn(listOf(purchaseRecord))
    }

    private suspend fun givenPurchaseStoredIsValid() {
        whenever(authService.storeLogin(any())).thenReturn(
            StoreLoginResponse(
                authToken = "authToken",
                externalId = "1234",
                email = "test@duck.com",
                status = "ok",
            ),
        )
    }

    private suspend fun givenAuthenticateSucceeds() {
        whenever(authService.accessToken(any())).thenReturn(AccessTokenResponse("accessToken"))
    }

    private suspend fun givenAuthenticateFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.accessToken(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenConfirmPurchaseFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(subscriptionsService.confirm(any(), any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenConfirmPurchaseSucceeds() {
        whenever(subscriptionsService.confirm(any(), any())).thenReturn(
            ConfirmationResponse(
                email = "test@duck.com",
                entitlements = listOf(),
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
}
