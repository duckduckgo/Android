package com.duckduckgo.subscriptions.impl.serp_promo

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class SerpPromoTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val cookieManager: CookieManagerWrapper = mock()
    private val subscriptions: Subscriptions = mock()
    private lateinit var lifecycleOwner: LifecycleOwner
    private var subscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)

    private val serpPromo = RealSerpPromo(cookieManager, coroutineRule.testDispatcherProvider, { subscriptionsFeature }, { subscriptions })

    @Before
    fun setup() {
        lifecycleOwner = TestLifecycleOwner()
    }

    @Test
    fun whenInjectCookieThenSetCookie() = runTest {
        subscriptionsFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))

        serpPromo.injectCookie("value")

        verify(cookieManager).setCookieOnAllProfiles(".subscriptions.duckduckgo.com", "privacy_pro_access_token=value;HttpOnly;Path=/;")
    }

    @Test
    fun whenKillSwitchedInjectCookieThenNoop() = runTest {
        subscriptionsFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = false))

        serpPromo.injectCookie("value")

        verify(cookieManager, never()).setCookieOnAllProfiles(any(), any())
    }

    @Test
    fun whenOnStartThenSetEmptyCookie() = runTest {
        subscriptionsFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))

        serpPromo.onStart(lifecycleOwner)

        verify(cookieManager).setCookieOnAllProfiles(".subscriptions.duckduckgo.com", "privacy_pro_access_token=;HttpOnly;Path=/;")
    }

    @Test
    fun whenKillSwitchedOnStartThenNoop() = runTest {
        subscriptionsFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = false))

        serpPromo.onStart(lifecycleOwner)

        verify(cookieManager, never()).setCookieOnAllProfiles(any(), any())
    }

    @Test
    fun whenOnStartAndAccessTokenThenSetAccessTokenCookie() = runTest {
        subscriptionsFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getAccessToken()).thenReturn("value")

        serpPromo.onStart(lifecycleOwner)

        verify(cookieManager).setCookieOnAllProfiles(".subscriptions.duckduckgo.com", "privacy_pro_access_token=value;HttpOnly;Path=/;")
    }
}
