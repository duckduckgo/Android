package com.duckduckgo.subscriptions.impl.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealSubscriptionsRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var repository: RealSubscriptionsRepository
    private val billingClient: BillingClientWrapper = mock()

    @Before
    fun before() {
        repository = RealSubscriptionsRepository(billingClient)
    }

    @Test
    fun whenProductDetailsExistAndMatchBasicSubscriptionIdThenReturnIt() = runTest {
        givenProductExist(BASIC_SUBSCRIPTION)

        assertTrue(repository.subscriptionDetails()?.productId == BASIC_SUBSCRIPTION)
    }

    @Test
    fun whenProductDetailsDoNotExistThenExpectNoEvents() = runTest {
        assertNull(repository.subscriptionDetails())
    }

    @Test
    fun whenProductDetailsExistAndDoNotMatchBasicSubscriptionIdThenExpectNoEvents() = runTest {
        givenProductExist("test")

        assertNull(repository.subscriptionDetails())
    }

    @Test
    fun whenSubscriptionDetailsExistThenReturnMapById() = runTest {
        givenProductExist(BASIC_SUBSCRIPTION)

        assertTrue(repository.offerDetail().size == 1)
    }

    @Test
    fun whenSubscriptionDetailsDoNotExistThenMapEmpty() = runTest {
        assertTrue(repository.offerDetail().isEmpty())
    }

    private fun getProductDetails(productId: String = BASIC_SUBSCRIPTION): ProductDetails {
        val productDetails: ProductDetails = mock()
        val subscriptionOfferDetails: SubscriptionOfferDetails = mock()
        whenever(subscriptionOfferDetails.basePlanId).thenReturn("basePlanId")
        whenever(productDetails.productId).thenReturn(productId)
        whenever(productDetails.name).thenReturn("name")
        whenever(productDetails.description).thenReturn("description")
        whenever(productDetails.subscriptionOfferDetails).thenReturn(listOf(subscriptionOfferDetails))
        return productDetails
    }
    private fun givenProductExist(productId: String = BASIC_SUBSCRIPTION) {
        val mockProducts = listOf(getProductDetails(productId))
        whenever(billingClient.products).thenReturn(mockProducts)
    }
}
