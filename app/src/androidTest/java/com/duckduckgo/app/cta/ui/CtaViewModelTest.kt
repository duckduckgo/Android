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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

class CtaViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

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

    private lateinit var testee: CtaViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))

        testee = CtaViewModel(
            mockAppInstallStore,
            mockPixel,
            mockSurveyDao,
            mockWidgetCapabilities,
            mockDismissedCtaDao
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysMatchThenCtaIsSurvey() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        assertTrue(testee.ctaViewState.value!!.cta is CtaConfiguration.Survey)
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysDontMatchThenCtaIsNotSurvey() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 2, SCHEDULED))
        assertFalse(testee.ctaViewState.value!!.cta is CtaConfiguration.Survey)
    }

    @Test
    fun whenScheduledSurveyIsNullThenCtaIsNotSurvey() {
        testee.onSurveyChanged(null)
        assertFalse(testee.ctaViewState.value!!.cta is CtaConfiguration.Survey)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetNotInstalledThenCtaIsAutoWidget() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertEquals(CtaConfiguration.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetNotInstalledThenCtaIsInstructionsWidget() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertEquals(CtaConfiguration.AddWidgetInstructions, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndStandardAddNotSupportedAndWidgetNotInstalledThenCtaIsNull() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndAddStandardAddNotSupportedAndWidgetAlreadyInstalledThenCtaIsNull() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaShownPixelIsFired() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        testee.onCtaShown()
        verify(mockPixel).fire(eq(SURVEY_CTA_SHOWN), any())
    }

    @Test
    fun whenCtaLaunchedPixelIsFired() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        testee.onCtaLaunched()
        verify(mockPixel).fire(eq(SURVEY_CTA_LAUNCHED), any())
    }

    @Test
    fun whenCtaDismissedPixelIsFired() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        testee.onCtaDismissed()
        verify(mockPixel).fire(eq(SURVEY_CTA_DISMISSED), any())
    }

    @Test
    fun whenSurveyCtaDismissedAndNotOtherCtaPossibleCtaIsNull() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        testee.onCtaDismissed()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenSurveyCtaDismissedAndWidgetCtaIsPossibleThenNextCtaIsWidget() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.onSurveyChanged(Survey("abc", "http://example.com", 1, SCHEDULED))
        testee.onCtaDismissed()
        assertEquals(CtaConfiguration.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenSurveyCtaDismissedThenScheduledSurveysAreCancelled() {
        val survey = Survey("abc", "http://example.com", 1, SCHEDULED)
        testee.ctaViewState.value = testee.ctaViewState.value!!.copy(cta = CtaConfiguration.Survey(survey))
        testee.onCtaDismissed()
        verify(mockSurveyDao).cancelScheduledSurveys()
    }

    @Test
    fun whenNonSurveyCtaDismissedCtaThenDatabaseNotified() {
        testee.ctaViewState.value = testee.ctaViewState.value!!.copy(cta = CtaConfiguration.AddWidgetAuto)
        testee.onCtaDismissed()
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.ADD_WIDGET))
    }
}
