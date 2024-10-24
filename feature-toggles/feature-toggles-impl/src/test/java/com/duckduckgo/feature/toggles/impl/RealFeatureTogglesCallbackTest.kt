package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import okio.ByteString.Companion.encode
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

class RealFeatureTogglesCallbackTest {
    private val pixel: Pixel = mock()
    private val callback = RealFeatureTogglesCallback(pixel)

    @Test
    fun `test pixel is sent with correct parameters`() {
        val pixelName = "experiment_enroll_experimentName_cohortName"
        val params = mapOf("enrollmentDate" to "2024-10-15")
        val tag = "${pixelName}_$params".encode().md5().hex()
        callback.onCohortAssigned(
            experimentName = "experimentName",
            cohortName = "cohortName",
            enrollmentDate = "2024-10-15T00:00-04:00[America/New_York]",
        )
        verify(pixel).fire(pixelName = pixelName, parameters = params, type = Unique(tag))
    }
}
