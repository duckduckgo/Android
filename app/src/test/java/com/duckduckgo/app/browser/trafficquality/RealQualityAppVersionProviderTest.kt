package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealQualityAppVersionProviderTest {

    private val appBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.flavor).thenReturn(BuildFlavor.PLAY)
    }

    private lateinit var testee: RealQualityAppVersionProvider

    @Before
    fun setup() {
        testee = RealQualityAppVersionProvider(appBuildConfig)
    }

    @Test
    fun returnsDefaultValue() {
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }
}
