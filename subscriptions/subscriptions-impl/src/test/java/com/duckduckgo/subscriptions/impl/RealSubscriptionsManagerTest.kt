package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsDataResult.Failure
import com.duckduckgo.subscriptions.impl.SubscriptionsDataResult.Success
import com.duckduckgo.subscriptions.impl.auth.AccessTokenResponse
import com.duckduckgo.subscriptions.impl.auth.AccountResponse
import com.duckduckgo.subscriptions.impl.auth.AuthService
import com.duckduckgo.subscriptions.impl.auth.CreateAccountResponse
import com.duckduckgo.subscriptions.impl.auth.EntitlementsResponse
import com.duckduckgo.subscriptions.impl.auth.StoreLoginResponse
import com.duckduckgo.subscriptions.impl.auth.ValidateTokenResponse
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.repository.RealSubscriptionsRepository
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import com.duckduckgo.subscriptions.store.AuthDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
    private val mockRepository: SubscriptionsRepository = mock()
    private val context: Context = mock()

    @Before
    fun before() {
        whenever(context.packageName).thenReturn("packageName")
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotAuthenticatedAndNotPurchaseStoredThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserNotAuthenticatedAndPurchaseStoredThenGetIdFromPurchase() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenAuthenticateSucceeds()
        givenValidateTokenSucceeds()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        verify(authService).storeLogin(any())
        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("accessToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenStoreLoginFails()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfUserAuthenticatedWithNotPurchasesThenReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenAuthenticateSucceeds()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfValidateTokenSucceedsThenReturnExternalId() = runTest {
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceeds()
        givenAuthenticateSucceeds()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("accessToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.recoverSubscriptionFromStore()

        assertTrue(value is Failure)
    }

    @Test
    fun whenRecoverSubscriptionFromStoreIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenAuthenticateSucceeds()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        subscriptionsManager.recoverSubscriptionFromStore()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.token)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetSubscriptionDataIfUserNotAuthenticatedThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Failure)
    }

    @Test
    fun whenGetSubscriptionDataIfTokenIsValidThenReturnSuccess() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceeds()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.getSubscriptionData()
        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("accessToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenGetSubscriptionDataIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPrePurchaseFlowIfUserNotAuthenticatedAndNotPurchaseStoredThenCreateAccount() = runTest {
        givenUserIsNotAuthenticated()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        subscriptionsManager.prePurchaseFlow()

        verify(authService).createAccount()
    }

    @Test
    fun whenPrePurchaseFlowIfCreateAccountFailsThenReturnFailure() = runTest {
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)
        givenUserIsNotAuthenticated()
        givenCreateAccountFails()

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPrePurchaseFlowIfCreateAccountSucceedsThenReturnExternalId() = runTest {
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()
        givenValidateTokenSucceeds()
        givenAuthenticateSucceeds()

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("accessToken", value.pat)
        assertTrue(value.entitlements.isNotEmpty())
    }

    @Test
    fun whenPrePurchaseFlowIfUserNotAuthenticatedAndPurchaseStoredThenGetIdFromPurchase() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceeds()
        givenAuthenticateSucceeds()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.prePurchaseFlow()

        verify(authService).storeLogin(any())
        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("accessToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenPrePurchaseFlowIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenStoreLoginFails()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPrePurchaseFlowIfUserAuthenticatedThenValidateToken() = runTest {
        givenUserIsAuthenticated()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        subscriptionsManager.prePurchaseFlow()

        verify(authService).validateToken(any())
    }

    @Test
    fun whenPrePurchaseFlowIfValidateTokenSucceedsThenReturnExternalId() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceeds()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("accessToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenPrePurchaseFlowIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPrePurchaseFlowIfAccountCreatedThenSignInUserAndSetToken() = runTest {
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()
        givenAuthenticateSucceeds()

        subscriptionsManager.prePurchaseFlow()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.token)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPrePurchaseFlowIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenAuthenticateSucceeds()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        subscriptionsManager.prePurchaseFlow()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.token)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticateIfNoAccessTokenThenReturnFailure() = runTest {
        givenAuthenticateFails()

        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.authenticate("validToken")

        assertTrue(value is Failure)
    }

    @Test
    fun whenAuthenticateIfAccessTokenThenSignInUserAndExchangeToken() = runTest {
        givenAuthenticateSucceeds()
        givenValidateTokenSucceeds()

        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        subscriptionsManager.authenticate("validToken")
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.token)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAuthenticateIfAccessTokenThenReturnSuccess() = runTest {
        givenAuthenticateSucceeds()
        givenValidateTokenSucceeds()

        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.authenticate("validToken")

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("accessToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    private fun givenUserIsNotAuthenticated() {
        authDataStore.token = null
    }

    private fun givenUserIsAuthenticated() {
        authDataStore.token = "accessToken"
    }

    private suspend fun givenCreateAccountFails() {
        val exception = "account_failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.createAccount()).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenCreateAccountSucceeds() {
        whenever(authService.createAccount()).thenReturn(
            CreateAccountResponse(
                authToken = "validToken",
                externalId = "1234",
                status = "ok",
            ),
        )
    }

    private suspend fun givenStoreLoginFails() {
        val exception = "failure".toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.storeLogin(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenValidateTokenSucceeds() {
        whenever(authService.validateToken(any())).thenReturn(
            ValidateTokenResponse(
                account = AccountResponse(
                    email = "accessToken",
                    externalId = "1234",
                    entitlements = listOf(
                        EntitlementsResponse("id", "name", "testProduct"),
                    ),
                ),
            ),
        )
    }

    private suspend fun givenValidateTokenFails(failure: String) {
        val exception = failure.toResponseBody("text/json".toMediaTypeOrNull())
        whenever(authService.validateToken(any())).thenThrow(HttpException(Response.error<String>(400, exception)))
    }

    private suspend fun givenPurchaseStored() {
        val purchaseRecord = PurchaseHistoryRecord(
            """
        {"purchaseToken": "validToken", "productId": "test", "purchaseTime":1, "quantity":1}
        """,
            "signature",
        )
        val testFlow: MutableStateFlow<List<PurchaseHistoryRecord>> = MutableStateFlow(listOf())
        whenever(billingClient.products).thenReturn(flowOf())
        whenever(billingClient.purchaseHistory).thenReturn(testFlow)
        testFlow.emit(listOf(purchaseRecord))
    }

    private suspend fun givenPurchaseStoredIsValid() {
        whenever(authService.storeLogin(any())).thenReturn(
            StoreLoginResponse(
                authToken = "validToken",
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

        override var token: String? = null
        override fun canUseEncryption(): Boolean = true
    }
}
