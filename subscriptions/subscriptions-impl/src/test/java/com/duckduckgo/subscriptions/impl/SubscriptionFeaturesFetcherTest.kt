package com.duckduckgo.subscriptions.impl

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.PricingPhase
import com.android.billingclient.api.ProductDetails.PricingPhases
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.DUCK_AI
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ITR
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.NETP
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_US
import com.duckduckgo.subscriptions.impl.billing.PlayBillingManager
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.services.FeaturesResponse
import com.duckduckgo.subscriptions.impl.services.SubscriptionsCachedService
import com.duckduckgo.subscriptions.impl.services.SubscriptionsService
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SubscriptionFeaturesFetcherTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val processLifecycleOwner = TestLifecycleOwner(initialState = INITIALIZED)
    private val playBillingManager: PlayBillingManager = mock()
    private val subscriptionsService: SubscriptionsService = mock()
    private val subscriptionsCachedService: SubscriptionsCachedService = mock()
    private val authRepository: AuthRepository = mock()
    private val privacyProFeature: PrivacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)

    private val subscriptionFeaturesFetcher = SubscriptionFeaturesFetcher(
        appCoroutineScope = coroutineRule.testScope,
        playBillingManager = playBillingManager,
        subscriptionsService = subscriptionsService,
        subscriptionsCachedService = subscriptionsCachedService,
        authRepository = authRepository,
        privacyProFeature = privacyProFeature,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Before
    fun setUp() {
        processLifecycleOwner.lifecycle.addObserver(subscriptionFeaturesFetcher)
    }

    @Test
    fun `when FF disabled then does not do anything`() = runTest {
        givenIsFeaturesApiEnabled(false)

        processLifecycleOwner.currentState = CREATED

        verifyNoInteractions(playBillingManager)
        verifyNoInteractions(authRepository)
        verifyNoInteractions(subscriptionsService)
    }

    @Test
    fun `when products loaded And Use Client with Cache Enabled then fetches and stores features from Cached Service`() = runTest {
        givenIsFeaturesApiEnabled(true)
        givenUseClientWithCacheForFeaturesEnabled(true)
        val productDetails = mockProductDetails()
        whenever(playBillingManager.productsFlow).thenReturn(flowOf(productDetails))
        whenever(authRepository.getFeatures(any())).thenReturn(emptySet())
        whenever(subscriptionsCachedService.features(any())).thenReturn(FeaturesResponse(listOf(NETP, ITR, DUCK_AI)))

        processLifecycleOwner.currentState = CREATED

        verify(playBillingManager).productsFlow
        verifyNoInteractions(subscriptionsService)
        verify(subscriptionsCachedService).features(MONTHLY_PLAN_US)
        verify(subscriptionsCachedService).features(YEARLY_PLAN_US)
        verify(authRepository).setFeatures(MONTHLY_PLAN_US, setOf(NETP, ITR, DUCK_AI))
        verify(authRepository).setFeatures(YEARLY_PLAN_US, setOf(NETP, ITR, DUCK_AI))
    }

    @Test
    fun `when products loaded then fetches and stores features`() = runTest {
        givenIsFeaturesApiEnabled(true)
        givenUseClientWithCacheForFeaturesEnabled(false)
        val productDetails = mockProductDetails()
        whenever(playBillingManager.productsFlow).thenReturn(flowOf(productDetails))
        whenever(authRepository.getFeatures(any())).thenReturn(emptySet())
        whenever(subscriptionsService.features(any())).thenReturn(FeaturesResponse(listOf(NETP, ITR, DUCK_AI)))

        processLifecycleOwner.currentState = CREATED

        verify(playBillingManager).productsFlow
        verify(subscriptionsService).features(MONTHLY_PLAN_US)
        verify(subscriptionsService).features(YEARLY_PLAN_US)
        verify(authRepository).setFeatures(MONTHLY_PLAN_US, setOf(NETP, ITR, DUCK_AI))
        verify(authRepository).setFeatures(YEARLY_PLAN_US, setOf(NETP, ITR, DUCK_AI))
    }

    @Test
    fun `when there are no products then does not store anything`() = runTest {
        givenIsFeaturesApiEnabled(true)
        whenever(playBillingManager.productsFlow).thenReturn(flowOf())

        processLifecycleOwner.currentState = CREATED

        verify(playBillingManager).productsFlow
        verifyNoInteractions(authRepository)
        verifyNoInteractions(subscriptionsService)
    }

    @Test
    fun `when features already stored and refresh features FF Disabled then does not fetch again`() = runTest {
        givenRefreshSubscriptionPlanFeaturesEnabled(false)
        givenIsFeaturesApiEnabled(true)
        val productDetails = mockProductDetails()
        whenever(playBillingManager.productsFlow).thenReturn(flowOf(productDetails))
        whenever(authRepository.getFeatures(any())).thenReturn(setOf(NETP, ITR))
        whenever(subscriptionsService.features(any())).thenReturn(FeaturesResponse(listOf(NETP, ITR)))

        processLifecycleOwner.currentState = CREATED

        verify(playBillingManager).productsFlow
        verify(authRepository).getFeatures(MONTHLY_PLAN_US)
        verify(authRepository).getFeatures(YEARLY_PLAN_US)
        verify(authRepository, never()).setFeatures(any(), any())
        verifyNoInteractions(subscriptionsService)
    }

    @Test
    fun `when features already stored and refresh features FF enabled then does fetch again`() = runTest {
        givenRefreshSubscriptionPlanFeaturesEnabled(true)
        givenUseClientWithCacheForFeaturesEnabled(false)
        givenIsFeaturesApiEnabled(true)
        val productDetails = mockProductDetails()
        whenever(playBillingManager.productsFlow).thenReturn(flowOf(productDetails))
        whenever(authRepository.getFeatures(any())).thenReturn(setOf(NETP, ITR))
        whenever(subscriptionsService.features(any())).thenReturn(FeaturesResponse(listOf(NETP, ITR, DUCK_AI)))

        processLifecycleOwner.currentState = CREATED

        verify(playBillingManager).productsFlow
        verify(subscriptionsService).features(MONTHLY_PLAN_US)
        verify(subscriptionsService).features(YEARLY_PLAN_US)
        verify(authRepository).setFeatures(MONTHLY_PLAN_US, setOf(NETP, ITR, DUCK_AI))
        verify(authRepository).setFeatures(YEARLY_PLAN_US, setOf(NETP, ITR, DUCK_AI))
    }

    @SuppressLint("DenyListedApi")
    private fun givenIsFeaturesApiEnabled(value: Boolean) {
        privacyProFeature.featuresApi().setRawStoredState(State(value))
    }

    @SuppressLint("DenyListedApi")
    private fun givenRefreshSubscriptionPlanFeaturesEnabled(value: Boolean) {
        privacyProFeature.refreshSubscriptionPlanFeatures().setRawStoredState(State(value))
    }

    @SuppressLint("DenyListedApi")
    private fun givenUseClientWithCacheForFeaturesEnabled(value: Boolean) {
        privacyProFeature.useClientWithCacheForFeatures().setRawStoredState(State(value))
    }

    private fun mockProductDetails(): List<ProductDetails> {
        val productDetails: ProductDetails = mock { productDetails ->
            whenever(productDetails.productId).thenReturn(SubscriptionsConstants.BASIC_SUBSCRIPTION)

            val pricingPhaseList: List<PricingPhase> = listOf(
                mock { pricingPhase -> whenever(pricingPhase.formattedPrice).thenReturn("1$") },
            )

            val pricingPhases: PricingPhases = mock { pricingPhases ->
                whenever(pricingPhases.pricingPhaseList).thenReturn(pricingPhaseList)
            }

            val offers = listOf(MONTHLY_PLAN_US, YEARLY_PLAN_US)
                .map { basePlanId ->
                    mock<SubscriptionOfferDetails> { offer ->
                        whenever(offer.basePlanId).thenReturn(basePlanId)
                        whenever(offer.pricingPhases).thenReturn(pricingPhases)
                    }
                }

            whenever(productDetails.subscriptionOfferDetails).thenReturn(offers)
        }

        return listOf(productDetails)
    }
}
