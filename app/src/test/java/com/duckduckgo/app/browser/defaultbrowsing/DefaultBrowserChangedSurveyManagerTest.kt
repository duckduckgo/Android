package com.duckduckgo.app.browser.defaultbrowsing

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DefaultBrowserChangedSurveyManagerTest {

    private val appInstallStore: AppInstallStore = mock()
    private val feature: DefaultBrowserChangedSurveyFeature = mock()
    private val toggle: Toggle = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val notificationManager: NotificationManagerCompat = mock()
    private lateinit var manager: RealDefaultBrowserChangedSurveyManager

    @Before
    fun setup() {
        whenever(feature.self()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)
        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(appBuildConfig.versionName).thenReturn("5.0.0")
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(appInstallStore.wasEverDefaultBrowser).thenReturn(true)
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        manager = RealDefaultBrowserChangedSurveyManager(appInstallStore, feature, appBuildConfig, defaultBrowserDetector, notificationManager)
    }

    @Test
    fun whenAllConditionsMetThenShouldTriggerSurvey() {
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertTrue(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenSurveyDoneThenShouldNotTriggerSurvey() {
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(true)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenFeatureDisabledThenShouldNotTriggerSurvey() {
        whenever(toggle.isEnabled()).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenNonEnglishLocaleThenShouldNotTriggerSurvey() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.FRANCE)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenEnglishUkLocaleThenShouldTriggerSurvey() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.UK)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertTrue(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenNeverDefaultBrowserThenShouldNotTriggerSurvey() {
        whenever(appInstallStore.wasEverDefaultBrowser).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenStillDefaultBrowserThenShouldNotTriggerSurvey() {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenMarkSurveyDoneThenShownSetToTrue() {
        manager.markSurveyShown()
        verify(appInstallStore).defaultBrowserChangedSurveyDone = true
    }

    @Test
    fun whenNotificationsEnabledThenReturnsTrue() {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)
        assertTrue(manager.areNotificationsEnabled())
    }

    @Test
    fun whenNotificationsDisabledThenReturnsFalse() {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(false)
        assertFalse(manager.areNotificationsEnabled())
    }

    @Test
    fun whenInstalledDay1ThenBucketIsD1() {
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12))
        val url = manager.buildSurveyUrl("in-app")
        assertTrue(url.contains("installAgeBucket=d1"))
    }

    @Test
    fun whenInstalledDay3ThenBucketIsD2_6() {
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))
        val url = manager.buildSurveyUrl("in-app")
        assertTrue(url.contains("installAgeBucket=d2_6"))
    }

    @Test
    fun whenInstalledWeek3ThenBucketIsW2_4() {
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(21))
        val url = manager.buildSurveyUrl("in-app")
        assertTrue(url.contains("installAgeBucket=w2_4"))
    }

    @Test
    fun whenInstalledMonth3ThenBucketIsM1_6() {
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90))
        val url = manager.buildSurveyUrl("in-app")
        assertTrue(url.contains("installAgeBucket=m1_6"))
    }

    @Test
    fun whenInstalledOverMonth6ThenBucketIsM6p() {
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(200))
        val url = manager.buildSurveyUrl("in-app")
        assertTrue(url.contains("installAgeBucket=m6p"))
    }

    @Test
    fun whenBuildSurveyUrlThenContainsChannel() {
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        val url = manager.buildSurveyUrl("push")
        assertTrue(url.contains("channel=push"))
    }

    @Test
    fun whenBuildSurveyUrlThenContainsOsvAndAppVer() {
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(appBuildConfig.versionName).thenReturn("5.100.0")
        val url = manager.buildSurveyUrl("in-app")
        assertTrue(url.contains("osv=33"))
        assertTrue(url.contains("appVer=5.100.0"))
    }
}
