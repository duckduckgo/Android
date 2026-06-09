package com.duckduckgo.subscriptions.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.FREE_TRIAL_DUCK_AI_PAID_USED
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.FREE_TRIAL_START
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.FREE_TRIAL_VPN_ACTIVATION
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PAYWALL_NOT_SEEN_D0
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PAYWALL_NOT_SEEN_D14
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PAYWALL_NOT_SEEN_D3
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PAYWALL_NOT_SEEN_D30
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PAYWALL_NOT_SEEN_D7
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PAYWALL_SHOWN_FIRST_TIME
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_SUCCESS_ORIGIN
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_WEBVIEW_RENDER_PROCESS_CRASH
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class SubscriptionPixelTest(
    private val pixel: SubscriptionPixel,
) {
    @Test
    fun `pixel name has subscription namespace prefix`() {
        assumeFalse(
            pixel in listOf(
                PURCHASE_SUCCESS_ORIGIN,
                SUBSCRIPTION_WEBVIEW_RENDER_PROCESS_CRASH,
                FREE_TRIAL_START,
                FREE_TRIAL_VPN_ACTIVATION,
                FREE_TRIAL_DUCK_AI_PAID_USED,
                PAYWALL_SHOWN_FIRST_TIME,
                PAYWALL_NOT_SEEN_D0,
                PAYWALL_NOT_SEEN_D3,
                PAYWALL_NOT_SEEN_D7,
                PAYWALL_NOT_SEEN_D14,
                PAYWALL_NOT_SEEN_D30,
            ),
        )

        pixel.getPixelNames().values.forEach { pixelName ->
            assertTrue(pixelName.startsWith("m_privacy-pro_"))
        }
    }

    @Test
    fun `pixel name has pixel type suffix`() {
        if (pixel == PURCHASE_SUCCESS_ORIGIN) return
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            val expectedSuffix = when (pixelType) {
                is Count -> "_c"
                is Daily -> "_d"
                is Unique -> "_u"
            }

            assertTrue(pixelName.endsWith(expectedSuffix))
        }
    }

    @Test
    fun `pixel names map is not empty`() {
        assertTrue(pixel.getPixelNames().isNotEmpty())
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): Collection<Array<SubscriptionPixel>> =
            SubscriptionPixel.entries.map { arrayOf(it) }
    }
}
