package com.duckduckgo.subscriptions.impl.serp_promo

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import kotlinx.coroutines.test.runTest
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
    private val lifecycleOwner: LifecycleOwner = TestLifecycleOwner()
    private var privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)

    private val serpPromo = RealSerpPromo(cookieManager, coroutineRule.testDispatcherProvider, { privacyProFeature }, { subscriptions })

    @Test
    fun whenInjectCookieThenSetCookie() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))

        serpPromo.injectCookie("value")

        verify(cookieManager).setCookie(".subscriptions.duckduckgo.com", "privacy_pro_access_token=value;HttpOnly;Path=/;")
    }

    @Test
    fun whenKillSwitchedInjectCookieThenNoop() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = false))

        serpPromo.injectCookie("value")

        verify(cookieManager, never()).setCookie(any(), any())
    }

    @Test
    fun whenOnStartThenSetEmptyCookie() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))

        serpPromo.onStart(lifecycleOwner)

        verify(cookieManager).setCookie(".subscriptions.duckduckgo.com", "privacy_pro_access_token=;HttpOnly;Path=/;")
    }

    @Test
    fun whenKillSwitchedOnStartThenNoop() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = false))

        serpPromo.onStart(lifecycleOwner)

        verify(cookieManager, never()).setCookie(any(), any())
    }

    @Test
    fun whenOnStartAndPromoCookieSetThenSetEmptyCookie() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))
        whenever(cookieManager.getCookie(any())).thenReturn("privacy_pro_access_token=value;")

        serpPromo.onStart(lifecycleOwner)
        verify(cookieManager).setCookie(".subscriptions.duckduckgo.com", "privacy_pro_access_token=;HttpOnly;Path=/;")
    }

    @Test
    fun whenOnStartAndPromoCookieSetAndAccessTokenThenSetEmptyCookie() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))
        whenever(cookieManager.getCookie(any())).thenReturn("privacy_pro_access_token=value;")
        whenever(subscriptions.getAccessToken()).thenReturn("value")

        serpPromo.onStart(lifecycleOwner)
        verify(cookieManager).setCookie(".subscriptions.duckduckgo.com", "privacy_pro_access_token=value;HttpOnly;Path=/;")
    }

    @Test
    fun whenKillSwitchedOnStartAndPromoCookieSetThenNoop() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = false))
        whenever(cookieManager.getCookie(any())).thenReturn("privacy_pro_access_token=value;")

        serpPromo.onStart(lifecycleOwner)
        verify(cookieManager, never()).setCookie(any(), any())
    }

    @Test
    fun whenOnStartAndOtherCookiesSetThenSetEmptyCookie() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))
        whenever(cookieManager.getCookie(any())).thenReturn("another_cookie=value;")

        serpPromo.onStart(lifecycleOwner)
        verify(cookieManager).setCookie(".subscriptions.duckduckgo.com", "privacy_pro_access_token=;HttpOnly;Path=/;")
    }

    @Test
    fun whenOnStartAndOtherCookiesSetAndAccessTokenThenSetAccessTokenCookie() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = true))
        whenever(cookieManager.getCookie(any())).thenReturn("another_cookie=value;")
        whenever(subscriptions.getAccessToken()).thenReturn("value")

        serpPromo.onStart(lifecycleOwner)
        verify(cookieManager).setCookie(".subscriptions.duckduckgo.com", "privacy_pro_access_token=value;HttpOnly;Path=/;")
    }

    @Test
    fun whenKillSwitchedOnStartAndOtherCookiesSetThenNoop() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = false))
        whenever(cookieManager.getCookie(any())).thenReturn("another_cookie=value;")

        serpPromo.onStart(lifecycleOwner)
        verify(cookieManager, never()).setCookie(any(), any())
    }

    @Test
    fun whenKillSwitchedOnStartAndOtherCookiesSetAndAccessTokenThenNoop() = runTest {
        privacyProFeature.serpPromoCookie().setRawStoredState(Toggle.State(enable = false))
        whenever(cookieManager.getCookie(any())).thenReturn("another_cookie=value;")
        whenever(subscriptions.getAccessToken()).thenReturn("value")

        serpPromo.onStart(lifecycleOwner)
        verify(cookieManager, never()).setCookie(any(), any())
    }
}
