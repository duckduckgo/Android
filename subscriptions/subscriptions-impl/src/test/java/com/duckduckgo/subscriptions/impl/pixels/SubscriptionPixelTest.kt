package com.duckduckgo.subscriptions.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE
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
        pixel.getPixelNames().values.forEach { pixelName ->
            assertTrue(pixelName.startsWith("m_privacy-pro_"))
        }
    }

    @Test
    fun `pixel name has pixel type suffix`() {
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            val expectedSuffix = when (pixelType) {
                COUNT -> "_c"
                DAILY -> "_d"
                UNIQUE -> "_u"
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
