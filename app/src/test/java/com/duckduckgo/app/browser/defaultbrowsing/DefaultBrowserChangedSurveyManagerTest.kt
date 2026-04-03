package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    fun whenSurveyPendingAndNotDoneThenShouldTriggerSurvey() {
        whenever(appInstallStore.defaultBrowserChangedSurveyPending).thenReturn(true)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertTrue(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenSurveyNotPendingThenShouldNotTriggerSurvey() {
        whenever(appInstallStore.defaultBrowserChangedSurveyPending).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenSurveyDoneThenShouldNotTriggerSurvey() {
        whenever(appInstallStore.defaultBrowserChangedSurveyPending).thenReturn(true)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(true)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenFeatureDisabledThenShouldNotTriggerSurvey() {
        whenever(toggle.isEnabled()).thenReturn(false)
        whenever(appInstallStore.defaultBrowserChangedSurveyPending).thenReturn(true)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenNonEnglishLocaleThenShouldNotTriggerSurvey() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.FRANCE)
        whenever(appInstallStore.defaultBrowserChangedSurveyPending).thenReturn(true)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertFalse(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenEnglishUkLocaleThenShouldTriggerSurvey() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.UK)
        whenever(appInstallStore.defaultBrowserChangedSurveyPending).thenReturn(true)
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        assertTrue(manager.shouldTriggerSurvey())
    }

    @Test
    fun whenMarkSurveyPendingAndNotDoneThenFlagIsTrue() {
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(false)
        manager.markSurveyPending()
        verify(appInstallStore).defaultBrowserChangedSurveyPending = true
    }

    @Test
    fun whenMarkSurveyPendingButAlreadyDoneThenFlagNotSet() {
        whenever(appInstallStore.defaultBrowserChangedSurveyDone).thenReturn(true)
        manager.markSurveyPending()
        verify(appInstallStore, never()).defaultBrowserChangedSurveyPending = true
    }

    @Test
    fun whenMarkSurveyDoneThenPendingClearedAndDoneSet() {
        manager.markSurveyDone()
        verify(appInstallStore).defaultBrowserChangedSurveyPending = false
        verify(appInstallStore).defaultBrowserChangedSurveyDone = true
    }

    @Test
    fun whenNotificationNotSentThenReturnsFalse() {
        assertFalse(manager.wasNotificationSentThisSession())
    }

    @Test
    fun whenNotificationSentThenReturnsTrue() {
        manager.recordNotificationSentThisSession()
        assertTrue(manager.wasNotificationSentThisSession())
    }
}
