/*
 * Copyright (c) 2019 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.cta.ui

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventEntity
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.useourapp.UseOurAppDetector
import com.duckduckgo.app.global.useourapp.UseOurAppDetector.Companion.USE_OUR_APP_SHORTCUT_URL
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.*
import java.util.concurrent.TimeUnit

@FlowPreview
@ExperimentalCoroutinesApi
class CtaViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase

    @Mock
    private lateinit var mockSurveyDao: SurveyDao

    @Mock
    private lateinit var mockWidgetCapabilities: WidgetCapabilities

    @Mock
    private lateinit var mockDismissedCtaDao: DismissedCtaDao

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockAppInstallStore: AppInstallStore

    @Mock
    private lateinit var mockVariantManager: VariantManager

    @Mock
    private lateinit var mockSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockOnboardingStore: OnboardingStore

    @Mock
    private lateinit var mockUserWhitelistDao: UserWhitelistDao

    @Mock
    private lateinit var mockUserStageStore: UserStageStore

    @Mock
    private lateinit var mockUserEventsStore: UserEventsStore

    @Mock
    private lateinit var mockTabRepository: TabRepository

    private val requiredDaxOnboardingCtas: List<CtaId> = listOf(
        CtaId.DAX_INTRO,
        CtaId.DAX_DIALOG_SERP,
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        CtaId.DAX_DIALOG_NETWORK,
        CtaId.DAX_FIRE_BUTTON,
        CtaId.DAX_END
    )

    private lateinit var testee: CtaViewModel

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockUserWhitelistDao.contains(any())).thenReturn(false)
        whenever(mockDismissedCtaDao.dismissedCtas()).thenReturn(db.dismissedCtaDao().dismissedCtas())
        whenever(mockTabRepository.flowTabs).thenReturn(db.tabsDao().flowTabs())

        testee = CtaViewModel(
            mockAppInstallStore,
            mockPixel,
            mockSurveyDao,
            mockWidgetCapabilities,
            mockDismissedCtaDao,
            mockUserWhitelistDao,
            mockVariantManager,
            mockSettingsDataStore,
            mockOnboardingStore,
            mockUserStageStore,
            mockUserEventsStore,
            UseOurAppDetector(mockUserEventsStore),
            mockTabRepository,
            coroutineRule.testDispatcherProvider
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysMatchAndLocaleIsUsThenCtaIsSurvey() = coroutineRule.runBlocking {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, site = null, locale = Locale.US)
        assertTrue(value is HomePanelCta.Survey)
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysMatchButLocaleIsNotUsThenCtaIsNotSurvey() = coroutineRule.runBlocking {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, site = null, locale = Locale.CANADA)
        assertFalse(value is HomePanelCta.Survey)
    }

    @Test
    fun whenScheduledSurveyIsNullThenCtaIsNotSurvey() = coroutineRule.runBlocking {
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, site = null, locale = Locale.US)
        assertFalse(value is HomePanelCta.Survey)
    }

    @Test
    fun whenScheduledSurveyChangesFromNullToNullThenClearedIsFalse() {
        val value = testee.onSurveyChanged(null)
        assertFalse(value)
    }

    @Test
    fun whenScheduledSurveyChangesFromNullToASurveyThenClearedIsFalse() {
        testee.onSurveyChanged(null)
        val value = testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        assertFalse(value)
    }

    @Test
    fun whenScheduledSurveyChangesFromASurveyToNullThenClearedIsTrue() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        val value = testee.onSurveyChanged(null)
        assertTrue(value)
    }

    @Test
    fun whenCtaShownAndCtaIsDaxAndCanNotSendPixelThenPixelIsNotFired() {
        testee.onCtaShown(DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockPixel, never()).fire(eq(SURVEY_CTA_SHOWN), any(), any())
    }

    @Test
    fun whenCtaShownAndCtaIsDaxAndCanSendPixelThenPixelIsFired() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn("s:0")
        testee.onCtaShown(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockPixel, never()).fire(eq(SURVEY_CTA_SHOWN), any(), any())
    }

    @Test
    fun whenCtaShownAndCtaIsNotDaxThenPixelIsFired() {
        testee.onCtaShown(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockPixel).fire(eq(SURVEY_CTA_SHOWN), any(), any())
    }

    @Test
    fun whenCtaLaunchedPixelIsFired() {
        testee.onUserClickCtaOkButton(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockPixel).fire(eq(SURVEY_CTA_LAUNCHED), any(), any())
    }

    @Test
    fun whenCtaDismissedPixelIsFired() = coroutineRule.runBlocking {
        testee.onUserDismissedCta(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockPixel).fire(eq(SURVEY_CTA_DISMISSED), any(), any())
    }

    @Test
    fun whenSurveyCtaDismissedThenScheduledSurveysAreCancelled() = coroutineRule.runBlocking {
        testee.onUserDismissedCta(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockSurveyDao).cancelScheduledSurveys()
        verify(mockDismissedCtaDao, never()).insert(any())
    }

    @Test
    fun whenNonSurveyCtaDismissedCtaThenDatabaseNotified() = coroutineRule.runBlocking {
        testee.onUserDismissedCta(HomePanelCta.AddWidgetAuto)
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.ADD_WIDGET))
    }

    @Test
    fun whenCtaDismissedAndUserHasPendingOnboardingCtasThenStageNotCompleted() = coroutineRule.runBlocking {
        givenOnboardingActive()
        givenShownDaxOnboardingCtas(emptyList())
        testee.onUserDismissedCta(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockUserStageStore, times(0)).stageCompleted(any())
    }

    @Test
    fun whenCtaDismissedAndAllDaxOnboardingCtasShownThenStageCompleted() = coroutineRule.runBlocking {
        givenOnboardingActive()
        givenShownDaxOnboardingCtas(requiredDaxOnboardingCtas)
        testee.onUserDismissedCta(DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockUserStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenHideTipsForeverThenPixelIsFired() = coroutineRule.runBlocking {
        testee.hideTipsForever(HomePanelCta.AddWidgetAuto)
        verify(mockPixel).fire(eq(ONBOARDING_DAX_ALL_CTA_HIDDEN), any(), any())
    }

    @Test
    fun whenHideTipsForeverThenHideTipsSetToTrueOnSettings() = coroutineRule.runBlocking {
        testee.hideTipsForever(HomePanelCta.AddWidgetAuto)
        verify(mockSettingsDataStore).hideTips = true
    }

    @Test
    fun whenHideTipsForeverThenDaxOnboardingStageCompleted() = coroutineRule.runBlocking {
        testee.hideTipsForever(HomePanelCta.AddWidgetAuto)
        verify(mockUserStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenRegisterDaxBubbleIntroCtaThenDatabaseNotified() = coroutineRule.runBlocking {
        testee.registerDaxBubbleCtaDismissed(DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
    }

    @Test
    fun whenRegisterDaxBubbleEndCtaThenDatabaseNotified() = coroutineRule.runBlocking {
        testee.registerDaxBubbleCtaDismissed(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_END))
    }

    @Test
    fun whenRegisterCtaAndUserHasPendingOnboardingCtasThenStageNotCompleted() = coroutineRule.runBlocking {
        givenOnboardingActive()
        givenShownDaxOnboardingCtas(emptyList())
        testee.registerDaxBubbleCtaDismissed(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockUserStageStore, times(0)).stageCompleted(any())
    }

    @Test
    fun whenRegisterCtaAndAllDaxOnboardingCtasShownThenStageCompleted() = coroutineRule.runBlocking {
        givenOnboardingActive()
        givenShownDaxOnboardingCtas(requiredDaxOnboardingCtas)
        testee.registerDaxBubbleCtaDismissed(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockUserStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndSiteIsNullThenReturnNull() = coroutineRule.runBlocking {
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = null)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndHideTipsIsTrueThenReturnNull() = coroutineRule.runBlocking {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndHideTipsIsTrueAndWidgetCompatibleThenReturnWidgetCta() = coroutineRule.runBlocking {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertTrue(value is HomePanelCta.AddWidgetAuto)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndPrivacyOffForSiteThenReturnNull() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        whenever(mockUserWhitelistDao.contains(any())).thenReturn(true)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndHideTipsIsTrueThenReturnWidgetAutoCta() = coroutineRule.runBlocking {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertTrue(value is HomePanelCta.AddWidgetAuto)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndHideTipsIsTrueThenReturnWidgetInstructionsCta() = coroutineRule.runBlocking {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertTrue(value is HomePanelCta.AddWidgetInstructions)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingMajorTrackerSiteThenReturnNetworkCta() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))
        val expectedCtaText = context.resources.getString(
            R.string.daxMainNetworkCtaText,
            "Facebook",
            "facebook.com",
            "Facebook"
        )

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site) as DaxDialogCta

        assertTrue(value is DaxDialogCta.DaxMainNetworkCta)
        assertEquals(expectedCtaText, value.getDaxText(context))
    }

    @Test
    fun whenRefreshCtaWhileBrowsingOnSiteOwnedByMajorTrackerThenReturnNetworkCta() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val site = site(url = "http://m.instagram.com", entity = TestEntity("Facebook", "Facebook", 9.0))
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site) as DaxDialogCta
        val expectedCtaText = context.resources.getString(
            R.string.daxMainNetworkOwnedCtaText,
            "Facebook",
            "instagram.com",
            "Facebook"
        )

        assertTrue(value is DaxDialogCta.DaxMainNetworkCta)
        assertEquals(expectedCtaText, value.getDaxText(context))
    }

    @Test
    fun whenRefreshCtaWhileBrowsingThenReturnTrackersBlockedCta() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val trackingEvent = TrackingEvent("test.com", "test.com", null, TestEntity("test", "test", 9.0), true)
        val site = site(url = "http://www.cnn.com", trackerCount = 1, events = listOf(trackingEvent))
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site)

        assertTrue(value is DaxDialogCta.DaxTrackersBlockedCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndTrackersAreNotMajorThenReturnTrackersBlockedCta() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val trackingEvent = TrackingEvent("test.com", "test.com", null, TestEntity("test", "test", 0.123), true)
        val site = site(url = "http://www.cnn.com", trackerCount = 1, events = listOf(trackingEvent))
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site)

        assertTrue(value is DaxDialogCta.DaxTrackersBlockedCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndNoTrackersInformationThenReturnNoSerpCta() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.cnn.com", trackerCount = 1)
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site)

        assertTrue(value is DaxDialogCta.DaxNoSerpCta)
    }

    @Test
    fun whenRefreshCtaOnSerpWhileBrowsingThenReturnSerpCta() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.duckduckgo.com")
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site)

        assertTrue(value is DaxDialogCta.DaxSerpCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingThenReturnNoSerpCta() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.wikipedia.com")
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site)

        assertTrue(value is DaxDialogCta.DaxNoSerpCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabThenValueReturnedIsNotDaxDialogCtaType() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.wikipedia.com")
        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, site = site)

        assertTrue(value !is DaxDialogCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroCtaWasNotPreviouslyShownThenIntroCtaShown() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(false)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertTrue(value is DaxBubbleCta.DaxIntroCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroCtaWasShownThenEndCtaShown() = coroutineRule.runBlocking {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        givenAtLeastOneDaxDialogCtaShown()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertTrue(value is DaxBubbleCta.DaxEndCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingWithDaxOnboardingCompletedButNotAllCtasWereShownThenReturnNull() = runBlockingTest {
        givenShownDaxOnboardingCtas(listOf(CtaId.DAX_INTRO))
        givenUserIsEstablished()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndUseOurAppOnboardingActiveThenUseOurAppCtaShown() = runBlockingTest {
        givenUseOurAppActive()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertTrue(value is UseOurAppCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndUseOurAppOnboardingActiveAndHideTipsIsTrueThenReturnNull() = runBlockingTest {
        givenUseOurAppActive()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndUseOurAppOnboardingActiveAndCtaShownThenReturnNull() = runBlockingTest {
        givenUseOurAppActive()
        whenever(mockDismissedCtaDao.exists(CtaId.USE_OUR_APP)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndSiteIsUseOurAppAndTwoDaysSinceShortcutAddedThenShowUseOurAppDeletionCta() = runBlockingTest {
        givenUserIsEstablished()
        val timestampEntity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(timestampEntity)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site(url = USE_OUR_APP_SHORTCUT_URL))
        assertTrue(value is UseOurAppDeletionCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndSiteIsUseOurAppAndTwoDaysSinceShortcutAddedAndHideTipsIsTrueThenReturnNull() = runBlockingTest {
        givenUserIsEstablished()
        val timestampEntity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(timestampEntity)
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site(url = USE_OUR_APP_SHORTCUT_URL))
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndSiteIsUseOurAppAndTwoDaysSinceShortcutAddedAndCtaShownThenReturnNull() = runBlockingTest {
        givenUserIsEstablished()
        val timestampEntity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(timestampEntity)
        whenever(mockDismissedCtaDao.exists(CtaId.USE_OUR_APP_DELETION)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site(url = USE_OUR_APP_SHORTCUT_URL))
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndSiteIsUseOurAppAndLessThanTwoDaysSinceShortcutAddedThenReturnNull() = runBlockingTest {
        givenUserIsEstablished()
        val timestampEntity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(timestampEntity)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site(url = USE_OUR_APP_SHORTCUT_URL))
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndSiteIsNotUseOurAppAndTwoDaysSinceShortcutAddedThenReturnNull() = runBlockingTest {
        givenUserIsEstablished()
        val timestampEntity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(timestampEntity)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, site = site(url = "test"))
        assertNull(value)
    }

    @Test
    fun whenUseOurAppCtaDismissedThenStageCompleted() = coroutineRule.runBlocking {
        givenUseOurAppActive()
        testee.onUserDismissedCta(UseOurAppCta())
        verify(mockUserStageStore).stageCompleted(AppStage.USE_OUR_APP_ONBOARDING)
    }

    @Test
    fun whenUserHidesAllTipsThenFireButtonAnimationShouldNotShow() = coroutineRule.runBlocking {
        givenOnboardingActive()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenUserHasTwoOrMoreTabsThenFireButtonAnimationShouldNotShow() = coroutineRule.runBlocking {
        givenOnboardingActive()
        db.tabsDao().insertTab(TabEntity(tabId = "0", position = 0))
        db.tabsDao().insertTab(TabEntity(tabId = "1", position = 1))

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenFireAnimationStopsThenDaxFireButtonDisabled() = coroutineRule.runBlocking {
        givenOnboardingActive()
        db.tabsDao().insertTab(TabEntity(tabId = "0", position = 0))
        db.tabsDao().insertTab(TabEntity(tabId = "1", position = 1))

        testee.showFireButtonPulseAnimation.first()

        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
    }

    @Test
    fun whenFireButtonAnimationActiveAndUserOpensANewTabThenFireButtonAnimationStops() = coroutineRule.runBlocking {
        val values = mutableListOf<Boolean>()
        givenOnboardingActive()
        db.tabsDao().insertTab(TabEntity(tabId = "0", position = 0))
        db.dismissedCtaDao().insert(DismissedCta(CtaId.DAX_DIALOG_TRACKERS_FOUND))
        db.tabsDao().insertTab(TabEntity(tabId = "1", position = 1))

        val collector = launch {
            testee.showFireButtonPulseAnimation.collect {
                assertFalse(it)
            }
        }
        collector.cancel()
    }

    @Test
    fun whenUserHasAlreadySeenFireButtonCtaThenFireButtonAnimationShouldNotShow() = coroutineRule.runBlocking {
        givenOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)).thenReturn(true)

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenUserHasAlreadySeenFireButtonPulseAnimationThenFireButtonAnimationShouldNotShow() = coroutineRule.runBlocking {
        givenOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)).thenReturn(true)

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenTipsActiveAndUserSeesAnyTriggerFirePulseAnimationCtaThenFireButtonAnimationShouldShow() = coroutineRule.runBlocking {
        givenOnboardingActive()
        val willTriggerFirePulseAnimationCtas = listOf(CtaId.DAX_DIALOG_TRACKERS_FOUND, CtaId.DAX_DIALOG_NETWORK, CtaId.DAX_DIALOG_OTHER)
        val launch = launch {
            testee.showFireButtonPulseAnimation.drop(1).collect {
                assertTrue(it)
            }
        }
        willTriggerFirePulseAnimationCtas.forEach {
            db.dismissedCtaDao().insert(DismissedCta(it))
        }

        launch.cancel()
    }

    @Test
    fun whenFirePulseAnimationDismissedThenCtaInsertedInDatabase() = coroutineRule.runBlocking {
        testee.dismissPulseAnimation()

        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON_PULSE))
    }

    @Test
    fun whenOnboardingCompletedThenNewDismissedCtasDoNotEmitValues() = coroutineRule.runBlocking {
        givenDaxOnboardingCompleted()
        val launch = launch {
            testee.showFireButtonPulseAnimation.collect { /* noop */ }
        }
        clearInvocations(mockDismissedCtaDao)

        requiredDaxOnboardingCtas.forEach {
            db.dismissedCtaDao().insert(DismissedCta(it))
        }

        verifyNoMoreInteractions(mockDismissedCtaDao)
        launch.cancel()
    }

    @Test
    fun whenTipsActiveAndUserSeesAnyNonTriggerFirePulseAnimationCtaThenFireButtonAnimationShouldNotShow() = coroutineRule.runBlocking {
        givenOnboardingActive()
        val willTriggerFirePulseAnimationCtas = listOf(CtaId.DAX_DIALOG_TRACKERS_FOUND, CtaId.DAX_DIALOG_NETWORK, CtaId.DAX_DIALOG_OTHER)
        val willNotTriggerFirePulseAnimationCtas = CtaId.values().toList() - willTriggerFirePulseAnimationCtas

        val launch = launch {
            testee.showFireButtonPulseAnimation.collect {
                assertFalse(it)
            }
        }
        willNotTriggerFirePulseAnimationCtas.forEach {
            db.dismissedCtaDao().insert(DismissedCta(it))
        }

        launch.cancel()
    }

    @Test
    fun whenFirstTimeUserClicksOnFireButtonThenFireDialogCtaReturned() = coroutineRule.runBlocking {
        givenOnboardingActive()

        val fireDialogCta = testee.getFireDialogCta()

        assertTrue(fireDialogCta is DaxFireDialogCta.TryClearDataCta)
    }

    @Test
    fun whenFirstTimeUserClicksOnFireButtonButUserHidAllTipsThenFireDialogCtaIsNull() = coroutineRule.runBlocking {
        givenOnboardingActive()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)

        val fireDialogCta = testee.getFireDialogCta()

        assertNull(fireDialogCta)
    }

    @Test
    fun whenFireCtaDismissedThenFireDialogCtaIsNull() = coroutineRule.runBlocking {
        givenOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)).thenReturn(true)

        val fireDialogCta = testee.getFireDialogCta()

        assertNull(fireDialogCta)
    }

    private suspend fun givenDaxOnboardingActive() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
    }

    private suspend fun givenDaxOnboardingCompleted() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
    }

    private suspend fun givenUserIsEstablished() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
    }

    private fun givenAtLeastOneDaxDialogCtaShown() {
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)
    }

    private fun givenShownDaxOnboardingCtas(shownCtas: List<CtaId>) {
        shownCtas.forEach {
            whenever(mockDismissedCtaDao.exists(it)).thenReturn(true)
        }
    }

    private suspend fun givenOnboardingActive() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
    }

    private suspend fun givenUseOurAppActive() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.USE_OUR_APP_ONBOARDING)
    }

    private fun site(
        url: String = "http://www.test.com",
        uri: Uri? = Uri.parse(url),
        https: HttpsStatus = HttpsStatus.SECURE,
        trackerCount: Int = 0,
        events: List<TrackingEvent> = emptyList(),
        majorNetworkCount: Int = 0,
        allTrackersBlocked: Boolean = true,
        privacyPractices: PrivacyPractices.Practices = PrivacyPractices.UNKNOWN,
        entity: Entity? = null,
        grade: PrivacyGrade = PrivacyGrade.UNKNOWN,
        improvedGrade: PrivacyGrade = PrivacyGrade.UNKNOWN
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(uri)
        whenever(site.https).thenReturn(https)
        whenever(site.entity).thenReturn(entity)
        whenever(site.trackingEvents).thenReturn(events)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.majorNetworkCount).thenReturn(majorNetworkCount)
        whenever(site.allTrackersBlocked).thenReturn(allTrackersBlocked)
        whenever(site.privacyPractices).thenReturn(privacyPractices)
        whenever(site.calculateGrades()).thenReturn(Site.SiteGrades(grade, improvedGrade))
        return site
    }
}
