package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.*
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.billing.PlayBillingManager
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.FakeSubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.repository.RealAuthRepository
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
    private val authRepository = RealAuthRepository(authDataStore, coroutineRule.testDispatcherProvider)
    private val emailManager: EmailManager = mock()
    private val playBillingManager: PlayBillingManager = mock()
    private val context: Context = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Before
    fun before() {
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
        )
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotAuthenticatedAndNotPurchaseStoredThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotAuthenticatedAndPurchaseStoredThenReturnSubscriptionAndStoreData() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenAccessTokenSucceeds()

        val result = subscriptionsManager.recoverSubscriptionFromStore() as RecoverSubscriptionResult.Success
        val subscription = result.subscription

        verify(authService).storeLogin(any())
        assertEquals("authToken", authDataStore.authToken)
        assertTrue(subscription.entitlements.firstOrNull { it.product == NetP.value } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenStoreLoginFails()

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserAuthenticatedWithNotPurchasesThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
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

        val result = subscriptionsManager.recoverSubscriptionFromStore() as RecoverSubscriptionResult.Success
        val subscription = result.subscription

        assertEquals("1234", authDataStore.externalId)
        assertTrue(subscription.entitlements.firstOrNull { it.product == NetP.value } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")

        val result = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(result is RecoverSubscriptionResult.Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.recoverSubscriptionFromStore()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFetchAndStoreAllDataIfUserNotAuthenticatedThenReturnNullSubscription() = runTest {
        givenUserIsNotAuthenticated()

        val value = subscriptionsManager.fetchAndStoreAllData()

        assertNull(value)
    }

    @Test
    fun whenFetchAndStoreAllDataIfTokenIsValidThenReturnSubscription() = runTest {
        givenUserIsAuthenticated()
        givenSubscriptionSucceedsWithEntitlements()

        val value = subscriptionsManager.fetchAndStoreAllData()
        assertEquals("1234", authDataStore.externalId)
        assertTrue(value?.entitlements?.firstOrNull { it.product == NetP.value } != null)
    }

    @Test
    fun whenFetchAndStoreAllDataIfTokenIsValidThenReturnEmitEntitlements() = runTest {
        givenUserIsAuthenticated()
        givenSubscriptionSucceedsWithEntitlements()

        subscriptionsManager.fetchAndStoreAllData()
        subscriptionsManager.entitlements.test {
            assertTrue(awaitItem().size == 1)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFetchAndStoreAllDataIfSubscriptionFailsThenReturnNull() = runTest {
        givenUserIsAuthenticated()
        givenSubscriptionFails()

        assertNull(subscriptionsManager.fetchAndStoreAllData())
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
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)

        verify(playBillingManager).launchBillingFlow(any(), any(), any(), externalId = eq("1234"))
    }

    @Test
    fun whenPurchaseFlowIfUserNotAuthenticatedAndPurchaseNotActiveInStoreThenGetIdFromPurchase() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements(status = "Expired")
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)

        verify(playBillingManager).launchBillingFlow(any(), any(), any(), externalId = eq("1234"))
    }

    @Test
    fun whenPurchaseFlowIfUserNotAuthenticatedAndPurchaseActiveInStoreThenRecoverSubscription() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(playBillingManager, never()).launchBillingFlow(any(), any(), any(), any())
            assertTrue(awaitItem() is CurrentPurchase.Recovered)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFlowIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
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
        givenSubscriptionSucceedsWithoutEntitlements(status = "Expired")

        subscriptionsManager.currentPurchaseState.test {
            subscriptionsManager.purchase(mock(), mock(), "", false)
            assertTrue(awaitItem() is CurrentPurchase.PreFlowInProgress)
            verify(playBillingManager).launchBillingFlow(any(), any(), any(), externalId = eq("1234"))
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
    fun whenPurchaseFlowIfAccountCreatedThenSetTokens() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)
        assertEquals("accessToken", authDataStore.accessToken)
        assertEquals("authToken", authDataStore.authToken)
    }

    @Test
    fun whenPurchaseFlowIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithoutEntitlements()
        givenAccessTokenSucceeds()

        subscriptionsManager.purchase(mock(), mock(), "", false)
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            assertEquals("authToken", authDataStore.authToken)
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
    fun whenNoEntitlementsThenHasSubscriptionEmitFalse() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsNoEntitlements()

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
            playBillingManager,
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
            playBillingManager,
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
        givenConfirmPurchaseSucceeds()

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
    }

    @Test
    fun whenPurchaseFailedThenPurchaseCheckedAndFailureEmit() = runTest {
        givenUserIsAuthenticated()
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
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Purchased("validateToken", "packageName"))
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)
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
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Canceled)
            assertTrue(awaitItem() is CurrentPurchase.Canceled)
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
        givenSubscriptionSucceedsWithEntitlements()
        givenValidateTokenFailsAndThenSucceeds("""{ "error": "expired_token" }""")
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenAccessTokenSucceeds()

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
        givenStoreLoginSucceeds()
        givenAccessTokenSucceeds()

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
        givenStoreLoginFails()
        givenPurchaseStored()

        val result = subscriptionsManager.getAuthToken()

        verify(authService).storeLogin(any())
        assertTrue(result is AuthToken.Failure)
    }

    @Test
    fun whenGetSubscriptionThenReturnCorrectStatus() = runTest {
        givenUserIsAuthenticated()
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
            playBillingManager,
            emailManager,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            pixelSender,
        )
        manager.signOut()
        verify(mockRepo).clearSubscription()
        verify(mockRepo).clearAccount()
    }

    @Test
    fun whenSignOutEmitFalseForIsSignedIn() = runTest {
        givenSubscriptionExists()
        givenUserIsAuthenticated()

        subscriptionsManager.isSignedIn.test {
            subscriptionsManager.signOut()
            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSignOutEmitFalseForHasSubscription() = runTest {
        givenUserIsAuthenticated()
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
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenStoreLoginSucceeds()
        givenSubscriptionSucceedsWithEntitlements()
        givenAccessTokenSucceeds()

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

        whenever(playBillingManager.purchaseState).thenReturn(flowOf(PurchaseState.Purchased("validateToken", "packageName")))

        subscriptionsManager.currentPurchaseState.test {
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Failure)

            verify(pixelSender).reportPurchaseFailureBackend()
            verifyNoMoreInteractions(pixelSender)

            cancelAndConsumeRemainingEvents()
        }
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

    private suspend fun givenSubscriptionSucceedsWithoutEntitlements(status: String = "Auto-Renewable") {
        givenValidateTokenSucceedsNoEntitlements()
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

    private suspend fun givenSubscriptionSucceedsWithEntitlements(status: String = "Auto-Renewable") {
        givenValidateTokenSucceedsWithEntitlements()
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

    private fun givenSubscriptionExists() {
        authDataStore.platform = "google"
        authDataStore.productId = "productId"
        authDataStore.entitlements = """[{"product":"product", "name":"name"}]"""
        authDataStore.status = "Auto-Renewable"
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

    private suspend fun givenStoreLoginSucceeds() {
        whenever(authService.storeLogin(any())).thenReturn(
            StoreLoginResponse(
                authToken = "authToken",
                externalId = "1234",
                email = "test@duck.com",
                status = "ok",
            ),
        )
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
        whenever(subscriptionsService.confirm(any(), any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenConfirmPurchaseSucceeds() {
        whenever(subscriptionsService.confirm(any(), any())).thenReturn(
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
}
