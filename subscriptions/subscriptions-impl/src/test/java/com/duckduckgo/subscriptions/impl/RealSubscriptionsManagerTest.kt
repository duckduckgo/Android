package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsData.Failure
import com.duckduckgo.subscriptions.impl.SubscriptionsData.Success
import com.duckduckgo.subscriptions.impl.auth.AccessTokenResponse
import com.duckduckgo.subscriptions.impl.auth.AccountResponse
import com.duckduckgo.subscriptions.impl.auth.AuthService
import com.duckduckgo.subscriptions.impl.auth.CreateAccountResponse
import com.duckduckgo.subscriptions.impl.auth.Entitlement
import com.duckduckgo.subscriptions.impl.auth.StoreLoginResponse
import com.duckduckgo.subscriptions.impl.auth.ValidateTokenResponse
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.PurchaseState
import com.duckduckgo.subscriptions.store.AuthDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealSubscriptionsManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authService: AuthService = mock()
    private val authDataStore: AuthDataStore = FakeDataStore()
    private val billingClient: BillingClientWrapper = mock()
    private val billingBuilder: BillingFlowParams.Builder = mock()
    private val context: Context = mock()
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Before
    fun before() {
        whenever(context.packageName).thenReturn("packageName")
        whenever(billingClient.billingFlowParamsBuilder(any(), any(), any(), any())).thenReturn(billingBuilder)
        whenever(billingBuilder.build()).thenReturn(mock())
        subscriptionsManager = RealSubscriptionsManager(
            authService,
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
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
        givenAuthenticateSucceeds()

        subscriptionsManager.recoverSubscriptionFromStore()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetSubscriptionDataIfUserNotAuthenticatedThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Failure)
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

        verify(authService).createAccount()
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
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
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
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
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
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
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
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
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
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
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

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(billingClient.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            authService,
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Purchased)
            assertTrue(awaitItem() is CurrentPurchase.InProgress)
            assertTrue(awaitItem() is CurrentPurchase.Success)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseFailedThenPurchaseCheckedAndFailureEmit() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")

        val flowTest: MutableSharedFlow<PurchaseState> = MutableSharedFlow()
        whenever(billingClient.purchaseState).thenReturn(flowTest)

        val manager = RealSubscriptionsManager(
            authService,
            authDataStore,
            billingClient,
            context,
            TestScope(),
            coroutineRule.testDispatcherProvider,
        )

        manager.currentPurchaseState.test {
            flowTest.emit(PurchaseState.Purchased)
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
    fun whenGetAuthTokenIfUserAuthenticatedWithSubscriptionAndTokenExpiredAndPurchaseInStoreExistsThenReturnSuccess() = runTest {
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
        whenever(authService.createAccount()).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenCreateAccountSucceeds() {
        whenever(authService.createAccount()).thenReturn(
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

    internal class FakeDataStore : AuthDataStore {

        override var accessToken: String? = null
        override var authToken: String? = null
        override fun canUseEncryption(): Boolean = true
    }
}
