package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import java.time.LocalDateTime
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
    fun whenBuildDateTodayThenReturnDefault() {
        whenever(appBuildConfig.buildDateTime).thenReturn(LocalDateTime.now())
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }

    @Test
    fun whenNotYetTimeToLogThenReturnDefault() {
        whenever(appBuildConfig.buildDateTime).thenReturn(LocalDateTime.now().minusDays(2))
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }

    @Test
    fun whenTimeToLogAndNotOverLoggingPeriodThenReturnAppVersion() {
        val versionName = "5.212.0"
        whenever(appBuildConfig.versionName).thenReturn(versionName)
        whenever(appBuildConfig.buildDateTime).thenReturn(LocalDateTime.now().minusDays(8))
        val appVersion = testee.provide()
        assertTrue(appVersion == versionName)
    }

    @Test
    fun whenTimeToLogAndOverLoggingPeriodThenReturnDefault() {
        val versionName = "5.212.0"
        whenever(appBuildConfig.versionName).thenReturn(versionName)
        whenever(appBuildConfig.buildDateTime).thenReturn(LocalDateTime.now().minusDays(20))
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }
}
