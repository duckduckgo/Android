package com.duckduckgo.subscriptions.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_SUCCESS_ORIGIN
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class SubscriptionPixelTest(
    private val pixel: SubscriptionPixel,
) {
    @Test
    fun `pixel name has privacy pro namespace prefix`() {
        if (pixel == PURCHASE_SUCCESS_ORIGIN) return
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
