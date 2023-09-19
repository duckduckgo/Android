package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsDataResult.Failure
import com.duckduckgo.subscriptions.impl.SubscriptionsDataResult.Success
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
    fun whenGetExternalIdIfUserNotAuthenticatedAndNotPurchaseStoredThenCreateAccount() = runTest {
        givenUserIsNotAuthenticated()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        subscriptionsManager.getSubscriptionData()

        verify(authService).createAccount()
    }

    @Test
    fun whenCreateAccountFailsThenReturnFailure() = runTest {
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)
        givenUserIsNotAuthenticated()
        givenCreateAccountFails()

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Failure)
    }

    @Test
    fun whenCreateAccountSucceedsThenReturnExternalId() = runTest {
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("validToken", value.pat)
        assertTrue(value.entitlements.isEmpty())
    }

    @Test
    fun whenGetExternalIdIfUserNotAuthenticatedAndPurchaseStoredThenGetIdFromPurchase() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceeds()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.getSubscriptionData()

        verify(authService).storeLogin(any())
        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("validToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenStoreLoginFails()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Failure)
    }

    @Test
    fun whenGetExternalIdIfUserAuthenticatedThenValidateToken() = runTest {
        givenUserIsAuthenticated()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        subscriptionsManager.getSubscriptionData()

        verify(authService).validateToken(any())
    }

    @Test
    fun whenValidateTokenSucceedsThenReturnExternalId() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceeds()
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertEquals("validToken", value.pat)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        val value = subscriptionsManager.getSubscriptionData()

        assertTrue(value is Failure)
    }

    @Test
    fun whenValidateTokenFailsWithExpiredTokenThenRetryWithPurchaseHistory() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("""{"error":"expired_token"}""")
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)

        subscriptionsManager.getSubscriptionData()

        verify(mockRepository).lastPurchaseHistoryRecord
    }

    @Test
    fun whenAccountCreatedThenSignInUserAndSetToken() = runTest {
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, mockRepository, context)
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()

        subscriptionsManager.getSubscriptionData()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("validToken", authDataStore.token)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        val repository: SubscriptionsRepository =
            RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
        val subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, repository, context)

        subscriptionsManager.getSubscriptionData()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("validToken", authDataStore.token)
            cancelAndConsumeRemainingEvents()
        }
    }

    private fun givenUserIsNotAuthenticated() {
        authDataStore.token = null
    }

    private fun givenUserIsAuthenticated() {
        authDataStore.token = "validToken"
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
                    email = "validToken",
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

    internal class FakeDataStore : AuthDataStore {

        override var token: String? = null
        override fun canUseEncryption(): Boolean = true
    }
}
