/*
 * Copyright (c) 2020 DuckDuckGo
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

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.experimental.theories.DataPoint
import org.junit.experimental.theories.Theories
import org.junit.experimental.theories.Theory
import org.junit.runner.RunWith

@RunWith(Theories::class)
class CtaViewModelNextCtaTest {

    private val mockWidgetCapabilities = mock<WidgetCapabilities>()
    private val mockDismissedCtaDao = mock<DismissedCtaDao>()
    private val mockVariantManager = mock<VariantManager>()
    private val mockDefaultBrowserDetector = mock<DefaultBrowserDetector>()

    private val testee = CtaViewModel(
        mock(),
        mock(),
        mock(),
        mockWidgetCapabilities,
        mockDismissedCtaDao,
        mockVariantManager,
        mock(),
        mock(),
        mock(),
        mockDefaultBrowserDetector
    )

    @Theory
    fun whenPreviousCtaIsDaxSerpCtaButConditionsAreValidThenExpectedNextCtaIsSearchWidgetCta(
        supportsStandardWidgetAdd: Boolean,
        hasInstalledWidget: Boolean,
        hasSearchWidgetDaxCtaFeature: Boolean,
        daxSearchWidgetShown: Boolean,
        daxNetworkDialogShown: Boolean,
        daxTrackersDialogShown: Boolean,
        daxOtherDialogShown: Boolean
    ) {
        val previousCta = DaxDialogCta.DaxSerpCta(mock(), mock())
        assumeTrue(supportsStandardWidgetAdd)
        assumeFalse(hasInstalledWidget)
        assumeTrue(hasSearchWidgetDaxCtaFeature)
        assumeFalse(daxSearchWidgetShown)
        assumeTrue(daxNetworkDialogShown || daxTrackersDialogShown || daxOtherDialogShown)
        givenSearchWidgetScenario(
            supportsStandardWidgetAdd,
            hasInstalledWidget,
            hasSearchWidgetDaxCtaFeature,
            daxSearchWidgetShown,
            daxNetworkDialogShown,
            daxTrackersDialogShown,
            daxOtherDialogShown
        )

        val nextCta = testee.obtainNextCta(previousCta)

        assertTrue(nextCta is DaxDialogCta.SearchWidgetCta)
    }

    @Theory
    fun whenPreviousCtaIsDaxSerpCtaButConditionsAreInvalidThenExpectedNextCtaIsNull(
        supportsStandardWidgetAdd: Boolean,
        hasInstalledWidget: Boolean,
        hasSearchWidgetDaxCtaFeature: Boolean,
        daxSearchWidgetShown: Boolean,
        daxNetworkDialogShown: Boolean,
        daxTrackersDialogShown: Boolean,
        daxOtherDialogShown: Boolean
    ) {
        val previousCta = DaxDialogCta.DaxSerpCta(mock(), mock())
        assumeFalse(
            supportsStandardWidgetAdd &&
                    !hasInstalledWidget &&
                    hasSearchWidgetDaxCtaFeature &&
                    !daxSearchWidgetShown &&
                    (daxNetworkDialogShown || daxTrackersDialogShown || daxOtherDialogShown)
        )
        givenSearchWidgetScenario(
            supportsStandardWidgetAdd,
            hasInstalledWidget,
            hasSearchWidgetDaxCtaFeature,
            daxSearchWidgetShown,
            daxNetworkDialogShown,
            daxTrackersDialogShown,
            daxOtherDialogShown
        )

        val nextCta = testee.obtainNextCta(previousCta)

        assertNull(nextCta)
    }

    @Theory
    fun whenPreviousCtaIsDaxTrackersBlockedCtaAndConditionsAreValidThenExpectedNextCtaIsDefaultBrowserCta(
        deviceSupportsDefaultBrowserConfiguration: Boolean,
        isDefaultBrowser: Boolean,
        hasDefaultBrowserDaxCtaFeature: Boolean,
        daxDefaultBrowserShown: Boolean
    ) {
        val previousCta = DaxDialogCta.DaxTrackersBlockedCta(mock(), mock(), emptyList(), "")
        assumeTrue(deviceSupportsDefaultBrowserConfiguration)
        assumeFalse(isDefaultBrowser)
        assumeTrue(hasDefaultBrowserDaxCtaFeature)
        assumeFalse(daxDefaultBrowserShown)
        givenDefaultBrowserScenario(
            deviceSupportsDefaultBrowserConfiguration,
            isDefaultBrowser,
            hasDefaultBrowserDaxCtaFeature,
            daxDefaultBrowserShown
        )

        val nextCta = testee.obtainNextCta(previousCta)

        assertTrue(nextCta is DaxDialogCta.DefaultBrowserCta)
    }

    @Theory
    fun whenPreviousCtaIsDaxTrackersBlockedCtaCtaButConditionsAreInvalidThenExpectedNextCtaIsNull(
        deviceSupportsDefaultBrowserConfiguration: Boolean,
        isDefaultBrowser: Boolean,
        hasDefaultBrowserDaxCtaFeature: Boolean,
        daxDefaultBrowserShown: Boolean
    ) {
        val previousCta = DaxDialogCta.DaxTrackersBlockedCta(mock(), mock(), emptyList(), "")
        assumeFalse(deviceSupportsDefaultBrowserConfiguration && !isDefaultBrowser && hasDefaultBrowserDaxCtaFeature && !daxDefaultBrowserShown)
        givenDefaultBrowserScenario(
            deviceSupportsDefaultBrowserConfiguration,
            isDefaultBrowser,
            hasDefaultBrowserDaxCtaFeature,
            daxDefaultBrowserShown
        )

        val nextCta = testee.obtainNextCta(previousCta)

        assertNull(nextCta)
    }

    private fun givenDefaultBrowserScenario(
        deviceSupportsDefaultBrowserConfiguration: Boolean,
        isDefaultBrowser: Boolean,
        hasDefaultBrowserDaxCtaFeature: Boolean,
        daxDefaultBrowserShown: Boolean
    ) {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(deviceSupportsDefaultBrowserConfiguration)
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(isDefaultBrowser)
        whenever(mockVariantManager.getVariant()).thenReturn(
            Variant("test", features = getFeatures(false, hasDefaultBrowserDaxCtaFeature), filterBy = { true })
        )
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_DEFAULT_BROWSER)).thenReturn(daxDefaultBrowserShown)
    }

    private fun givenSearchWidgetScenario(
        supportsStandardWidgetAdd: Boolean,
        hasInstalledWidget: Boolean,
        hasSearchWidgetDaxCtaFeature: Boolean,
        daxSearchWidgetShown: Boolean,
        daxNetworkDialogShown: Boolean,
        daxTrackersDialogShown: Boolean,
        daxOtherDialogShown: Boolean
    ) {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(supportsStandardWidgetAdd)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(hasInstalledWidget)
        whenever(mockVariantManager.getVariant()).thenReturn(
            Variant("test", features = getFeatures(hasSearchWidgetDaxCtaFeature, false), filterBy = { true })
        )
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_SEARCH_WIDGET)).thenReturn(daxSearchWidgetShown)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)).thenReturn(daxNetworkDialogShown)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(daxTrackersDialogShown)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)).thenReturn(daxOtherDialogShown)
    }

    private fun getFeatures(searchWidgetFeature: Boolean, defaultBrowserFeature: Boolean): List<VariantManager.VariantFeature> {
        val featureList = mutableListOf<VariantManager.VariantFeature>()
        if (searchWidgetFeature) {
            featureList.add(VariantManager.VariantFeature.SearchWidgetDaxCta)
        }
        if (defaultBrowserFeature) {
            featureList.add(VariantManager.VariantFeature.DefaultBrowserDaxCta)
        }
        return featureList
    }

    companion object {
        @JvmStatic
        @DataPoint
        fun supportsStandardWidgetAdd() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun hasInstalledWidget() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun hasSearchWidgetDaxCtaFeature() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun daxSearchWidgetShown() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun deviceSupportsDefaultBrowserConfiguration() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun isDefaultBrowser() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun hasDefaultBrowserDaxCtaFeature() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun daxDefaultBrowserShown() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun daxNetworkDialogShown() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun daxTrackersDialogShown() = listOf(true, false)

        @JvmStatic
        @DataPoint
        fun daxOtherDialogShown() = listOf(true, false)
    }
}