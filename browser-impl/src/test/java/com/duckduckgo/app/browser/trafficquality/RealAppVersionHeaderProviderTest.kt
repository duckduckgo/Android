package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.RealAppVersionHeaderProvider.Companion.APP_VERSION_QUALITY_DEFAULT_VALUE
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealAppVersionHeaderProviderTest {

    private val appBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.flavor).thenReturn(BuildFlavor.PLAY)
    }

    private lateinit var testee: RealAppVersionHeaderProvider

    @Before
    fun setup() {
        testee = RealAppVersionHeaderProvider(appBuildConfig)
    }

    @Test
    fun whenStubRequiredThenStubProvided() {
        val appVersion = testee.provide(isStub = true)

        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }

    @Test
    fun whenStubNotRequiredThenVersionProvided() {
        val versionName = "5.212.0"
        whenever(appBuildConfig.versionName).thenReturn(versionName)

        val appVersion = testee.provide(isStub = false)

        assertTrue(appVersion == versionName)
    }
}
