package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.RealQualityAppVersionProvider.Companion.APP_VERSION_QUALITY_DEFAULT_VALUE
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import java.time.LocalDateTime
import java.time.ZoneId
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
    fun whenBuildDateEmptyThenReturnDefault() {
        whenever(appBuildConfig.buildDateTimeMillis).thenReturn(0L)
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }

    @Test
    fun whenBuildDateTodayThenReturnDefault() {
        givenBuildDateDaysAgo(0)
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }

    @Test
    fun whenNotYetTimeToLogThenReturnDefault() {
        givenBuildDateDaysAgo(2)
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }

    @Test
    fun whenTimeToLogThenReturnAppVersion() {
        givenBuildDateDaysAgo(6)
        val versionName = "5.212.0"
        givenVersionName(versionName)
        val appVersion = testee.provide()
        assertTrue(appVersion == versionName)
    }

    @Test
    fun whenTimeToLogAndNotOverLoggingPeriodThenReturnAppVersion() {
        val versionName = "5.212.0"
        givenVersionName(versionName)
        givenBuildDateDaysAgo(8)
        val appVersion = testee.provide()
        assertTrue(appVersion == versionName)
    }

    @Test
    fun whenTimeToLogAndOverLoggingPeriodThenReturnDefault() {
        val versionName = "5.212.0"
        givenVersionName(versionName)
        givenBuildDateDaysAgo(20)
        val appVersion = testee.provide()
        assertTrue(appVersion == APP_VERSION_QUALITY_DEFAULT_VALUE)
    }

    @Test
    fun whenTimeToLogAndJustOnLoggingPeriodThenReturnVersionName() {
        val versionName = "5.212.0"
        givenVersionName(versionName)
        givenBuildDateDaysAgo(16)
        val appVersion = testee.provide()
        assertTrue(appVersion == versionName)
    }

    private fun givenBuildDateDaysAgo(days: Long) {
        val daysAgo = LocalDateTime.now().minusDays(days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(appBuildConfig.buildDateTimeMillis).thenReturn(daysAgo)
    }

    private fun givenVersionName(versionName: String) {
        whenever(appBuildConfig.versionName).thenReturn(versionName)
    }
}
