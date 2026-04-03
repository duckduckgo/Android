package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale

class DefaultBrowserChangedSurveyManagerTest {

    private val appInstallStore: AppInstallStore = mock()
    private val feature: DefaultBrowserChangedSurveyFeature = mock()
    private val toggle: Toggle = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private lateinit var manager: RealDefaultBrowserChangedSurveyManager

    @Before
    fun setup() {
        whenever(feature.self()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)
        manager = RealDefaultBrowserChangedSurveyManager(appInstallStore, feature, appBuildConfig)
    }

    @Test
    fun whenWasEverDefaultAndNotCurrentlyDefaultAndSurveyNotShownThenShouldTriggerSurvey() {
        whenever(appInstallStore.wasEverDefault).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyShown).thenReturn(false)
        assertTrue(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenNeverWasDefaultThenShouldNotTriggerSurvey() {
        whenever(appInstallStore.wasEverDefault).thenReturn(false)
        whenever(appInstallStore.defaultBrowser).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyShown).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenCurrentlyDefaultThenShouldNotTriggerSurvey() {
        whenever(appInstallStore.wasEverDefault).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(true)
        whenever(appInstallStore.defaultBrowserChangedSurveyShown).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenSurveyAlreadyShownThenShouldNotTriggerSurvey() {
        whenever(appInstallStore.wasEverDefault).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyShown).thenReturn(true)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenFeatureDisabledThenShouldNotTriggerSurvey() {
        whenever(toggle.isEnabled()).thenReturn(false)
        whenever(appInstallStore.wasEverDefault).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyShown).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenNonEnglishLocaleThenShouldNotTriggerSurvey() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.FRANCE)
        whenever(appInstallStore.wasEverDefault).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyShown).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenEnglishUkLocaleThenShouldTriggerSurvey() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.UK)
        whenever(appInstallStore.wasEverDefault).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyShown).thenReturn(false)
        assertTrue(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenMarkSurveyAsShownThenStoreIsUpdated() {
        manager.markSurveyAsShown()
        verify(appInstallStore).defaultBrowserChangedSurveyShown = true
    }

    @Test
    fun whenRecordDefaultSetThenWasEverDefaultIsSet() {
        manager.recordDefaultSet()
        verify(appInstallStore).wasEverDefault = true
    }

    @Test
    fun whenAlreadyWasEverDefaultThenRecordDefaultSetStillSetsFlag() {
        whenever(appInstallStore.wasEverDefault).thenReturn(true)
        manager.recordDefaultSet()
        verify(appInstallStore).wasEverDefault = true
    }

    @Test
    fun whenNotificationNotSentThenWasNotificationSentThisSessionReturnsFalse() {
        assertFalse(manager.wasNotificationSentThisSession())
    }

    @Test
    fun whenNotificationSentThenWasNotificationSentThisSessionReturnsTrue() {
        manager.recordNotificationSentThisSession()
        assertTrue(manager.wasNotificationSentThisSession())
    }
}
