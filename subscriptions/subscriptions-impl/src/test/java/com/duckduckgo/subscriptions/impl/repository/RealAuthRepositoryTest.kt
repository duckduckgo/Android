package com.duckduckgo.subscriptions.impl.repository

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.model.Entitlement
import com.duckduckgo.subscriptions.impl.serp_promo.FakeSerpPromo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@SuppressLint("DenyListedApi")
class RealAuthRepositoryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authStore = FakeSubscriptionsDataStore()
    private val serpPromo = FakeSerpPromo()
    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)
    private val authRepository: AuthRepository = RealAuthRepository(
        authStore,
        coroutineRule.testDispatcherProvider,
        serpPromo,
        dagger.Lazy { privacyProFeature },
    )

    @Test
    fun whenClearAccountThenClearData() = runTest {
        authStore.email = "email@duck.com"
        authStore.externalId = "externalId"
        authStore.authToken = "authToken"
        authStore.accessToken = "accessToken"

        authRepository.setAuthToken(null)
        authRepository.setAccessToken(null)
        authRepository.setAccount(null)

        assertNull(authStore.accessToken)
        assertNull(authStore.authToken)
        assertNull(authStore.externalId)
        assertNull(authStore.email)
    }

    @Test
    fun whenSetAccessTokenThenInjectSerpPromoCookie() = runTest {
        authRepository.setAccessToken("token")

        assertEquals("token", serpPromo.cookie)
    }

    @Test
    fun whenClearSubscriptionThenClearData() = runTest {
        authStore.status = "expired"
        authStore.billingPeriod = "Monthly"
        authStore.startedAt = 1000L
        authStore.expiresOrRenewsAt = 1000L
        authStore.platform = "google"
        authStore.productId = "productId"
        authStore.entitlements = "[]"

        authRepository.setSubscription(null)

        assertNull(authStore.status)
        assertNull(authStore.billingPeriod)
        assertNull(authStore.startedAt)
        assertNull(authStore.expiresOrRenewsAt)
        assertNull(authStore.platform)
        assertNull(authStore.productId)
        assertEquals("[]", authStore.entitlements)
    }

    @Test
    fun whenSetAccountThenSetData() = runTest {
        assertNull(authStore.email)
        assertNull(authStore.externalId)

        authRepository.setAccount(Account(externalId = "externalId", email = "john@example.com"))

        assertEquals("externalId", authStore.externalId)
        assertEquals("john@example.com", authStore.email)
    }

    @Test
    fun whenTokensThenReturnTokens() = runTest {
        assertNull(authStore.authToken)
        assertNull(authStore.accessToken)
        assertNull(authStore.accessTokenV2)
        assertNull(authStore.accessTokenV2ExpiresAt)
        assertNull(authStore.refreshTokenV2)
        assertNull(authStore.refreshTokenV2ExpiresAt)

        authStore.accessToken = "accessToken"
        authStore.authToken = "authToken"

        val accessTokenV2 = AccessToken(jwt = "jwt-access", expiresAt = Instant.parse("2024-10-21T10:15:30.00Z"))
        val refreshTokenV2 = RefreshToken(jwt = "jwt-refresh", expiresAt = Instant.parse("2024-10-21T10:15:30.00Z"))
        authRepository.setAccessTokenV2(accessTokenV2)
        authRepository.setRefreshTokenV2(refreshTokenV2)

        assertEquals("authToken", authRepository.getAuthToken())
        assertEquals("accessToken", authRepository.getAccessToken())
        assertEquals(accessTokenV2, authRepository.getAccessTokenV2())
        assertEquals(refreshTokenV2, authRepository.getRefreshTokenV2())
    }

    @Test
    fun whenPurchaseToWaitingStatusThenStoreWaiting() = runTest {
        authRepository.purchaseToWaitingStatus()
        assertEquals(WAITING.statusName, authStore.status)
    }

    @Test
    fun whenGetStatusReturnCorrectStatus() = runTest {
        authStore.status = AUTO_RENEWABLE.statusName
        assertEquals(AUTO_RENEWABLE, authRepository.getStatus())
        authStore.status = NOT_AUTO_RENEWABLE.statusName
        assertEquals(NOT_AUTO_RENEWABLE, authRepository.getStatus())
        authStore.status = GRACE_PERIOD.statusName
        assertEquals(GRACE_PERIOD, authRepository.getStatus())
        authStore.status = INACTIVE.statusName
        assertEquals(INACTIVE, authRepository.getStatus())
        authStore.status = EXPIRED.statusName
        assertEquals(EXPIRED, authRepository.getStatus())
        authStore.status = WAITING.statusName
        assertEquals(WAITING, authRepository.getStatus())
        authStore.status = "test"
        assertEquals(UNKNOWN, authRepository.getStatus())
    }

    @Test
    fun whenCanSupportEncryptionThenReturnValue() = runTest {
        assertTrue(authRepository.canSupportEncryption())
    }

    @Test
    fun whenCanSupportEncryptionItCannotThenReturnFalse() = runTest {
        val repository: AuthRepository = RealAuthRepository(
            FakeSubscriptionsDataStore(supportEncryption = false),
            coroutineRule.testDispatcherProvider,
            serpPromo,
            dagger.Lazy { privacyProFeature },
        )
        assertFalse(repository.canSupportEncryption())
    }

    @Test
    fun whenSetFeaturesAndStoredValueIsNullThenSaveJson() = runTest {
        authStore.subscriptionFeatures = null

        authRepository.setFeatures(basePlanId = "plan1", features = setOf("feature1", "feature2"))

        assertEquals("""{"plan1":["feature1","feature2"]}""", authStore.subscriptionFeatures)
    }

    @Test
    fun whenSetFeaturesAndStoredValueIsNotNullThenUpdateJson() = runTest {
        authStore.subscriptionFeatures = """{"plan1":["feature1","feature2"]}"""

        authRepository.setFeatures(basePlanId = "plan2", features = setOf("feature1", "feature2"))

        assertEquals(
            """{"plan1":["feature1","feature2"],"plan2":["feature1","feature2"]}""",
            authStore.subscriptionFeatures,
        )
    }

    @Test
    fun whenGetFeaturesThenReturnsCorrectValue() = runTest {
        authStore.subscriptionFeatures = """{"plan1":["feature1","feature2"],"plan2":["feature3"]}"""

        val result = authRepository.getFeatures(basePlanId = "plan1")

        assertEquals(setOf("feature1", "feature2"), result)
    }

    @Test
    fun whenGetFeaturesAndBasePlanNotFoundThenReturnEmptySet() = runTest {
        authStore.subscriptionFeatures = """{"plan1":["feature1","feature2"]}"""

        val result = authRepository.getFeatures(basePlanId = "plan2")

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun whenRegisterLocalPurchasedAtThenStoreTimestamp() = runTest {
        authRepository.registerLocalPurchasedAt()

        assertNotNull(authStore.localPurchasedAt)
        assertTrue(authStore.localPurchasedAt!! > 0)
    }

    @Test
    fun whenGetLocalPurchasedAtThenReturnStoredValue() = runTest {
        val expectedTimestamp = 1699000000000L
        authStore.localPurchasedAt = expectedTimestamp

        val result = authRepository.getLocalPurchasedAt()

        assertEquals(expectedTimestamp, result)
    }

    @Test
    fun whenGetLocalPurchasedAtAndNotSetThenReturnNull() = runTest {
        authStore.localPurchasedAt = null

        val result = authRepository.getLocalPurchasedAt()

        assertNull(result)
    }

    @Test
    fun whenRemoveLocalPurchasedAtThenClearValue() = runTest {
        authStore.localPurchasedAt = 1699000000000L

        authRepository.removeLocalPurchasedAt()

        assertNull(authStore.localPurchasedAt)
    }

    @Test
    fun whenSetFeaturesV2AndStoredValueIsNullThenSaveJson() = runTest {
        authStore.subscriptionEntitlements = null

        authRepository.setFeaturesV2(
            basePlanId = "plan1",
            features = setOf(
                Entitlement(name = "plus", product = "Network Protection"),
                Entitlement(name = "plus", product = "Data Broker Protection"),
            ),
        )

        assertNotNull(authStore.subscriptionEntitlements)
        assertTrue(authStore.subscriptionEntitlements!!.contains("plan1"))
        assertTrue(authStore.subscriptionEntitlements!!.contains("Network Protection"))
        assertTrue(authStore.subscriptionEntitlements!!.contains("Data Broker Protection"))
    }

    @Test
    fun whenSetFeaturesV2AndStoredValueIsNotNullThenUpdateJson() = runTest {
        authStore.subscriptionEntitlements = """{"plan1":[{"name":"plus","product":"Network Protection"}]}"""

        authRepository.setFeaturesV2(
            basePlanId = "plan2",
            features = setOf(Entitlement(name = "plus", product = "Data Broker Protection")),
        )

        assertNotNull(authStore.subscriptionEntitlements)
        assertTrue(authStore.subscriptionEntitlements!!.contains("plan1"))
        assertTrue(authStore.subscriptionEntitlements!!.contains("plan2"))
    }

    @Test
    fun whenGetFeaturesV2ThenReturnsCorrectValue() = runTest {
        authStore.subscriptionEntitlements = """{"plan1":[
            |{"name":"plus","product":"Network Protection"}
            |,{"name":"plus","product":"Data Broker Protection"}
            |]}
        """.trimMargin()

        val result = authRepository.getFeaturesV2(basePlanId = "plan1")

        assertEquals(2, result.size)
        assertTrue(result.contains(Entitlement(name = "plus", product = "Network Protection")))
        assertTrue(result.contains(Entitlement(name = "plus", product = "Data Broker Protection")))
    }

    @Test
    fun whenGetFeaturesV2AndBasePlanNotFoundThenReturnEmptySet() = runTest {
        authStore.subscriptionEntitlements = """{"plan1":[
            |{"name":"plus","product":"Network Protection"}
            |]}
        """.trimMargin()

        val result = authRepository.getFeaturesV2(basePlanId = "plan2")

        assertEquals(emptySet<Entitlement>(), result)
    }

    @Test
    fun whenGetFeaturesAndTierFlagOnThenReturnV2ProductNames() = runTest {
        privacyProFeature.tierMessagingEnabled().setRawStoredState(Toggle.State(enable = true))
        authStore.subscriptionEntitlements = """{"plan1":[
            |{"name":"plus","product":"Network Protection"},
            |{"name":"plus","product":"Data Broker Protection"}
            |]}
        """.trimMargin()
        authStore.subscriptionFeatures = """{"plan1":["Old Feature"]}"""

        val result = authRepository.getFeatures(basePlanId = "plan1")

        assertEquals(setOf("Network Protection", "Data Broker Protection"), result)
    }

    @Test
    fun whenGetFeaturesAndTierFlagOnAndV2EmptyThenFallbackToV1() = runTest {
        privacyProFeature.tierMessagingEnabled().setRawStoredState(Toggle.State(enable = true))
        authStore.subscriptionEntitlements = null
        authStore.subscriptionFeatures = """{"plan1":["feature1","feature2"]}"""

        val result = authRepository.getFeatures(basePlanId = "plan1")

        // When flag is ON and v2 is empty, fallback to v1 for smooth runtime flag transitions
        assertEquals(setOf("feature1", "feature2"), result)
    }

    @Test
    fun whenGetFeaturesAndTierFlagOnAndV2EmptyForPlanThenFallbackToV1() = runTest {
        privacyProFeature.tierMessagingEnabled().setRawStoredState(Toggle.State(enable = true))
        authStore.subscriptionEntitlements = """{"plan2":[{"name":"plus","product":"Network Protection"}]}"""
        authStore.subscriptionFeatures = """{"plan1":["feature1","feature2"]}"""

        val result = authRepository.getFeatures(basePlanId = "plan1")

        // When flag is ON and v2 is empty for this plan, fallback to v1 for smooth runtime flag transitions
        assertEquals(setOf("feature1", "feature2"), result)
    }

    @Test
    fun whenGetFeaturesAndTierFlagOffThenReturnV1Features() = runTest {
        privacyProFeature.tierMessagingEnabled().setRawStoredState(Toggle.State(enable = false))
        authStore.subscriptionEntitlements = """{"plan1":[{"name":"plus","product":"Network Protection"}]}"""
        authStore.subscriptionFeatures = """{"plan1":["feature1","feature2"]}"""

        val result = authRepository.getFeatures(basePlanId = "plan1")

        // When flag is OFF, should use v1 storage only
        assertEquals(setOf("feature1", "feature2"), result)
    }

    @Test
    fun whenSetPendingPlansWithEmptyListThenStoreNull() = runTest {
        authRepository.setPendingPlans(emptyList())

        assertNull(authStore.pendingPlans)
    }

    @Test
    fun whenSetPendingPlansWithSinglePlanThenStoreJson() = runTest {
        val pendingPlan = PendingPlan(
            productId = "ddg-privacy-pro-yearly-renews-us",
            billingPeriod = "yearly",
            effectiveAt = 1700000000000L,
            status = "scheduled",
            tier = SubscriptionTier.PLUS,
        )

        authRepository.setPendingPlans(listOf(pendingPlan))

        assertNotNull(authStore.pendingPlans)
        assertTrue(authStore.pendingPlans!!.contains("ddg-privacy-pro-yearly-renews-us"))
        assertTrue(authStore.pendingPlans!!.contains("yearly"))
        assertTrue(authStore.pendingPlans!!.contains("scheduled"))
        assertTrue(authStore.pendingPlans!!.contains("plus"))
    }

    @Test
    fun whenGetPendingPlansThenReturnCorrectList() = runTest {
        authStore.pendingPlans = """[{
            "productId": "ddg-privacy-pro-yearly-renews-us",
            "billingPeriod": "yearly",
            "effectiveAt": 1700000000000,
            "status": "scheduled",
            "tier": "plus"
        }]"""

        val result = authRepository.getPendingPlans()

        assertEquals(1, result.size)
        assertEquals("ddg-privacy-pro-yearly-renews-us", result[0].productId)
        assertEquals("yearly", result[0].billingPeriod)
        assertEquals(1700000000000L, result[0].effectiveAt)
        assertEquals("scheduled", result[0].status)
        assertEquals(SubscriptionTier.PLUS, result[0].tier)
    }

    @Test
    fun whenGetPendingPlansWithMultiplePlansThenReturnCorrectList() = runTest {
        authStore.pendingPlans = """[
            {"productId": "plan1", "billingPeriod": "monthly", "effectiveAt": 1700000000000, "status": "scheduled", "tier": "plus"},
            {"productId": "plan2", "billingPeriod": "yearly", "effectiveAt": 1800000000000, "status": "pending", "tier": "pro"}
        ]"""

        val result = authRepository.getPendingPlans()

        assertEquals(2, result.size)
        assertEquals("plan1", result[0].productId)
        assertEquals(SubscriptionTier.PLUS, result[0].tier)
        assertEquals("plan2", result[1].productId)
        assertEquals(SubscriptionTier.PRO, result[1].tier)
    }

    @Test
    fun whenGetPendingPlansAndNullThenReturnEmptyList() = runTest {
        authStore.pendingPlans = null

        val result = authRepository.getPendingPlans()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenSetSubscriptionWithPendingPlansThenStorePendingPlans() = runTest {
        val pendingPlan = PendingPlan(
            productId = "ddg-privacy-pro-yearly-renews-us",
            billingPeriod = "yearly",
            effectiveAt = 1700000000000L,
            status = "scheduled",
            tier = SubscriptionTier.PLUS,
        )

        authRepository.setSubscription(
            Subscription(
                productId = "ddg-subscription-pro-monthly-renews-us",
                billingPeriod = "monthly",
                startedAt = 1234L,
                expiresOrRenewsAt = 5678L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = emptyList(),
                pendingPlans = listOf(pendingPlan),
            ),
        )

        assertNotNull(authStore.pendingPlans)
        assertTrue(authStore.pendingPlans!!.contains("ddg-privacy-pro-yearly-renews-us"))
    }

    @Test
    fun whenGetSubscriptionWithPendingPlansThenReturnPendingPlans() = runTest {
        authStore.productId = "ddg-subscription-pro-monthly-renews-us"
        authStore.billingPeriod = "monthly"
        authStore.startedAt = 1234L
        authStore.expiresOrRenewsAt = 5678L
        authStore.status = AUTO_RENEWABLE.statusName
        authStore.platform = "google"
        authStore.pendingPlans = """[{
            "productId": "ddg-privacy-pro-yearly-renews-us",
            "billingPeriod": "yearly",
            "effectiveAt": 1700000000000,
            "status": "scheduled",
            "tier": "plus"
        }]"""

        val subscription = authRepository.getSubscription()

        assertNotNull(subscription)
        assertEquals(1, subscription!!.pendingPlans.size)
        assertEquals("ddg-privacy-pro-yearly-renews-us", subscription.pendingPlans[0].productId)
        assertTrue(subscription.hasPendingChange)
    }

    @Test
    fun whenGetSubscriptionWithNoPendingPlansThenReturnEmptyList() = runTest {
        authStore.productId = "ddg-subscription-pro-monthly-renews-us"
        authStore.billingPeriod = "monthly"
        authStore.startedAt = 1234L
        authStore.expiresOrRenewsAt = 5678L
        authStore.status = AUTO_RENEWABLE.statusName
        authStore.platform = "google"
        authStore.pendingPlans = null

        val subscription = authRepository.getSubscription()

        assertNotNull(subscription)
        assertTrue(subscription!!.pendingPlans.isEmpty())
        assertFalse(subscription.hasPendingChange)
    }

    @Test
    fun whenGetSubscriptionWithPendingPlansThenPendingPlansReturned() = runTest {
        authStore.productId = "ddg-subscription-pro-monthly-renews-us"
        authStore.billingPeriod = "monthly"
        authStore.startedAt = 1234L
        authStore.expiresOrRenewsAt = 5678L
        authStore.status = AUTO_RENEWABLE.statusName
        authStore.platform = "google"
        authStore.pendingPlans = """[{
            "productId": "ddg-privacy-pro-yearly-renews-us",
            "billingPeriod": "yearly",
            "effectiveAt": 1700000000000,
            "status": "scheduled",
            "tier": "plus"
        }]"""

        val subscription = authRepository.getSubscription()

        assertNotNull(subscription)
        assertEquals(1, subscription!!.pendingPlans.size)
        assertEquals("ddg-privacy-pro-yearly-renews-us", subscription.pendingPlans[0].productId)
        assertTrue(subscription.hasPendingChange)
    }
}
