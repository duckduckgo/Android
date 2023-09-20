package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.app.CoroutineTestRule
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
import com.duckduckgo.subscriptions.store.AuthDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val context: Context = mock()
    private lateinit var subscriptionsManager: SubscriptionsManager

    @Before
    fun before() {
        whenever(context.packageName).thenReturn("packageName")
        subscriptionsManager = RealSubscriptionsManager(authService, authDataStore, billingClient, context)
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
        givenValidateTokenSucceeds()

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
        givenValidateTokenSucceeds()
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
        givenValidateTokenSucceeds()

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
    fun whenPrePurchaseFlowIfUserNotAuthenticatedAndNotPurchaseStoredThenCreateAccount() = runTest {
        givenUserIsNotAuthenticated()

        subscriptionsManager.prePurchaseFlow()

        verify(authService).createAccount()
    }

    @Test
    fun whenPrePurchaseFlowIfCreateAccountFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountFails()

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPrePurchaseFlowIfCreateAccountSucceedsThenReturnExternalId() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()
        givenValidateTokenSucceeds()
        givenAuthenticateSucceeds()

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.isNotEmpty())
    }

    @Test
    fun whenPrePurchaseFlowIfUserNotAuthenticatedAndPurchaseStoredThenGetIdFromPurchase() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenValidateTokenSucceeds()
        givenAuthenticateSucceeds()

        val value = subscriptionsManager.prePurchaseFlow()

        verify(authService).storeLogin(any())
        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenPrePurchaseFlowIfStoreLoginFailsThenReturnFailure() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenStoreLoginFails()

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPrePurchaseFlowIfUserAuthenticatedThenValidateToken() = runTest {
        givenUserIsAuthenticated()

        subscriptionsManager.prePurchaseFlow()

        verify(authService).validateToken(any())
    }

    @Test
    fun whenPrePurchaseFlowIfValidateTokenSucceedsThenReturnExternalId() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceeds()

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenPrePurchaseFlowIfValidateTokenFailsReturnFailure() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("failure")

        val value = subscriptionsManager.prePurchaseFlow()

        assertTrue(value is Failure)
    }

    @Test
    fun whenPrePurchaseFlowIfAccountCreatedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenCreateAccountSucceeds()
        givenAuthenticateSucceeds()

        subscriptionsManager.prePurchaseFlow()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPrePurchaseFlowIfPurchaseHistoryRetrievedThenSignInUserAndSetToken() = runTest {
        givenUserIsNotAuthenticated()
        givenPurchaseStored()
        givenPurchaseStoredIsValid()
        givenAuthenticateSucceeds()

        subscriptionsManager.prePurchaseFlow()
        subscriptionsManager.isSignedIn.test {
            assertTrue(awaitItem())
            assertEquals("accessToken", authDataStore.accessToken)
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
        givenValidateTokenSucceeds()

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
        givenValidateTokenSucceeds()

        val value = subscriptionsManager.authenticate("authToken")

        assertTrue(value is Success)
        assertEquals("1234", (value as Success).externalId)
        assertTrue(value.entitlements.firstOrNull { it.product == "testProduct" } != null)
    }

    @Test
    fun whenHasSubscriptionThenReturnTrue() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceeds()

        assertTrue(subscriptionsManager.hasSubscription())
    }

    @Test
    fun whenHasSubscriptionAndNoEntitlementsThenReturnFalse() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenSucceedsNoEntitlements()

        assertFalse(subscriptionsManager.hasSubscription())
    }

    @Test
    fun whenHasSubscriptionAndErrorThenReturnFalse() = runTest {
        givenUserIsAuthenticated()
        givenValidateTokenFails("error")

        assertFalse(subscriptionsManager.hasSubscription())
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
