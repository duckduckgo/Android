package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.Toggle.TargetMatcherPlugin
import okio.ByteString.Companion.encode
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

class RealFeatureTogglesCallbackTest {
    private val pixel: Pixel = mock()
    private val plugins: PluginPoint<TargetMatcherPlugin> = object : PluginPoint<TargetMatcherPlugin> {
        override fun getPlugins(): Collection<TargetMatcherPlugin> {
            TODO("Not yet implemented")
        }
    }
    private val callback = RealFeatureTogglesCallback(pixel, plugins)

    @Test
    fun `test pixel is sent with correct parameters`() {
        val pixelName = "experiment_enroll_experimentName_cohortName"
        val params = mapOf("enrollmentDate" to "2024-10-15")
        val tag = "${pixelName}_$params".encode().md5().hex()
        callback.onCohortAssigned(
            experimentName = "experimentName",
            cohortName = "cohortName",
            enrollmentDate = "2024-10-15T08:50:17.467-05:00[America/New_York]",
        )
        verify(pixel).fire(pixelName = pixelName, parameters = params, type = Unique(tag))
    }
}
