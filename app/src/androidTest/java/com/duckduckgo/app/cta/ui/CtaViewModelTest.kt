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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

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
    private lateinit var mockPrivacySettingsStore: PrivacySettingsStore

    private lateinit var testee: CtaViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockPrivacySettingsStore.privacyOn).thenReturn(true)

        testee = CtaViewModel(
            mockAppInstallStore,
            mockPixel,
            mockSurveyDao,
            mockWidgetCapabilities,
            mockDismissedCtaDao,
            mockVariantManager,
            mockSettingsDataStore,
            mockOnboardingStore,
            mockPrivacySettingsStore
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysMatchThenCtaIsSurvey() {
        val survey = Survey("abc", "http://example.com", 1, SCHEDULED)
        val value = testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        assertEquals(survey, value)
    }

    @Test
    fun whenScheduledSurveyIsNullThenCtaIsNotSurvey() {
        val value = testee.onSurveyChanged(null)
        assertNull(value)
    }

    @Test
    fun whenCtaShownPixelIsFired() {
        testee.onCtaShown(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockPixel).fire(eq(SURVEY_CTA_SHOWN), any())
    }

    @Test
    fun whenCtaLaunchedPixelIsFired() {
        testee.onUserClickCtaOkButton(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockPixel).fire(eq(SURVEY_CTA_LAUNCHED), any())
    }

    @Test
    fun whenCtaDismissedPixelIsFired() {
        testee.onUserDismissedCta(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockPixel).fire(eq(SURVEY_CTA_DISMISSED), any())
    }

    @Test
    fun whenSurveyCtaDismissedThenScheduledSurveysAreCancelled() {
        testee.onUserDismissedCta(HomePanelCta.Survey(Survey("abc", "http://example.com", 1, SCHEDULED)))
        verify(mockSurveyDao).cancelScheduledSurveys()
        verify(mockDismissedCtaDao, never()).insert(any())
    }

    @Test
    fun whenNonSurveyCtaDismissedCtaThenDatabaseNotified() {
        testee.onUserDismissedCta(HomePanelCta.AddWidgetAuto)
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.ADD_WIDGET))
    }

    @Test
    fun whenHideTipsForeverThenPixelIsFired() {
        testee.hideTipsForever(HomePanelCta.AddWidgetAuto)
        verify(mockPixel).fire(eq(ONBOARDING_DAX_ALL_CTA_HIDDEN), any())
    }

    @Test
    fun whenHideTipsForeverThenHideTipsSetToTrueOnSettings() {
        testee.hideTipsForever(HomePanelCta.AddWidgetAuto)
        verify(mockSettingsDataStore).hideTips = true
    }

    @Test
    fun whenRegisterDaxBubbleIntroCtaThenDatabaseNotified() {
        testee.registerDaxBubbleCtaShown(DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
    }

    @Test
    fun whenRegisterDaxBubbleIntroCtaThenPixelIsFired() {
        testee.registerDaxBubbleCtaShown(DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockPixel).fire(eq(ONBOARDING_DAX_CTA_SHOWN), any())
    }

    @Test
    fun whenRegisterDaxBubbleEndCtaThenDatabaseNotified() {
        testee.registerDaxBubbleCtaShown(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_END))
    }

    @Test
    fun whenRegisterDaxBubbleEndCtaThenPixelIsFired() {
        testee.registerDaxBubbleCtaShown(DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockPixel).fire(eq(ONBOARDING_DAX_CTA_SHOWN), any())
    }

    @Test
    fun whenRefreshCtaOnNewTabThenReturnDaxIntroCta() = runBlockingTest {
        setConceptTestVariant()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertTrue(value is DaxBubbleCta.DaxIntroCta)
    }

    @Test
    fun whenRefreshCtaOnExistingTabAndSiteIsNullThenReturnNull() = runBlockingTest {
        setConceptTestVariant()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = null)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnExistingTabAndHideTipsIsTrueThenReturnNull() = runBlockingTest {
        setConceptTestVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnExistingTabAndPrivacyOffThenReturnNull() = runBlockingTest {
        setConceptTestVariant()
        whenever(mockPrivacySettingsStore.privacyOn).thenReturn(false)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnExistingTabAndVariantIsNotConceptTestThenReturnNull() = runBlockingTest {
        setDefaultVariant()
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnNewTabThenReturnDaxEndCta() = runBlockingTest {
        setConceptTestVariant()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertTrue(value is DaxBubbleCta.DaxEndCta)
    }

    @Test
    fun whenRefreshCtaOnNewTabAndHideTipsIsTrueThenReturnNull() = runBlockingTest {
        setConceptTestVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnNewTabAndVarianIstNotConceptTestThenReturnNull() = runBlockingTest {
        setDefaultVariant()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnNewTabAndVariantIsConceptTestThenDoNotShowWidgetAutoCta() = runBlockingTest {
        setConceptTestVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertTrue(value !is HomePanelCta.AddWidgetAuto)
    }

    @Test
    fun whenRefreshCtaOnNewTabAndVariantIsConceptTestThenDoNotShowWidgetInstructionsCta() = runBlockingTest {
        setConceptTestVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertTrue(value !is HomePanelCta.AddWidgetInstructions)
    }

    @Test
    fun whenRefreshCtaOnNewTabAndHideTipsIsTrueThenReturnWidgetAutoCta() = runBlockingTest {
        setDefaultVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertTrue(value is HomePanelCta.AddWidgetAuto)
    }

    @Test
    fun whenRefreshCtaOnNewTabAndHideTipsIsTrueThenReturnWidgetInstructionsCta() = runBlockingTest {
        setDefaultVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertTrue(value is HomePanelCta.AddWidgetInstructions)
    }

    @Test
    fun whenRefreshCtaOnNewTabVariantIsNoCtaReturnNullWhenTryngToShowWidgetCta() = runBlockingTest {
        setNoCtaVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnNewTabVariantIsNoCtaReturnNullWhenTryngToShowWidgetInstructionsCta() = runBlockingTest {
        setNoCtaVariant()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnExistingTabThenReturnNetworkCta() = runBlockingTest {
        setConceptTestVariant()
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))
        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)

        assertTrue(value is DaxDialogCta.DaxMainNetworkCta)
    }

    @Test
    fun whenRefreshCtaOnExistingTabThenReturnTrackersBlockedCta() = runBlockingTest {
        setConceptTestVariant()
        val trackingEvent = TrackingEvent("test.com", "test.com", null, TestEntity("test", "test", 9.0), true)
        val site = site(url = "http://www.cnn.com", trackerCount = 1, events = listOf(trackingEvent))
        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)

        assertTrue(value is DaxDialogCta.DaxTrackersBlockedCta)
    }

    @Test
    fun whenRefreshCtaOnExistingTabButNoTrackersInformationThenReturnNoSerpCta() = runBlockingTest {
        setConceptTestVariant()
        val site = site(url = "http://www.cnn.com", trackerCount = 1)
        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)

        assertTrue(value is DaxDialogCta.DaxNoSerpCta)
    }

    @Test
    fun whenRefreshCtaOnExistingTabThenReturnSerpCta() = runBlockingTest {
        setConceptTestVariant()
        val site = site(url = "http://www.duckduckgo.com")
        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)

        assertTrue(value is DaxDialogCta.DaxSerpCta)
    }

    @Test
    fun whenRefreshCtaOnExistingTabThenReturnNoSerpCta() = runBlockingTest {
        setConceptTestVariant()
        val site = site(url = "http://www.wikipedia.com")
        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = false, site = site)

        assertTrue(value is DaxDialogCta.DaxNoSerpCta)
    }

    @Test
    fun whenRefreshCtaOnNewTabThenValueReturnedIsNotDaxDialogCtaType() = runBlockingTest {
        setConceptTestVariant()
        val site = site(url = "http://www.wikipedia.com")
        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true, site = site)

        assertTrue(value !is DaxDialogCta)
    }

    @Test
    fun whenRefreshCtaOnNewTabAndJourneyCompletedAndSiteIsNotNullReturnNull() = runBlockingTest {
        setConceptTestVariant()
        val site = site(url = "http://www.wikipedia.com")
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true, site = site)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnNewTabEndCtaNotShownIfIntroCtaWasNotPreviouslyShown() = runBlockingTest {
        setConceptTestVariant()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(false)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isNewTab = true)
        assertTrue(value !is DaxBubbleCta.DaxEndCta)
    }


    private fun setConceptTestVariant() {
        whenever(mockVariantManager.getVariant()).thenReturn(
            Variant(
                "test",
                features = listOf(VariantManager.VariantFeature.ConceptTest),
                filterBy = { true })
        )
    }

    private fun setDefaultVariant() {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("test", features = emptyList(), filterBy = { true }))
    }

    private fun setNoCtaVariant() {
        whenever(mockVariantManager.getVariant()).thenReturn(
            Variant(
                "test",
                features = listOf(VariantManager.VariantFeature.ExistingNoCta),
                filterBy = { true })
        )
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
