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
import com.duckduckgo.app.cta.ui.DaxDialogCta.DefaultBrowserCta.DefaultBrowserCtaBehavior
import com.duckduckgo.app.cta.ui.DaxDialogCta.SearchWidgetCta.SearchWidgetCtaBehavior
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Test

class DaxDialogCtaTest {

    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val mockWidgetCapabilities: WidgetCapabilities = mock()

    @Test
    fun whenUserHasDefaultBrowserThenDefaultBrowserCtaBehaviorIsSettings() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)

        val testee = DaxDialogCta.DefaultBrowserCta(
            mockDefaultBrowserDetector, mock(), mock()
        )

        assertEquals(testee.primaryAction, DefaultBrowserCtaBehavior.Settings.action)
        assertEquals(testee.ctaPixelParam, DefaultBrowserCtaBehavior.Settings.ctaPixelParam)
        assertEquals(testee.okButton, DefaultBrowserCtaBehavior.Settings.primaryButtonStringRes)
    }

    @Test
    fun whenUserDoesNotHaveDefaultBrowserThenDefaultBrowserCtaBehaviorIsDialog() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)

        val testee = DaxDialogCta.DefaultBrowserCta(
            mockDefaultBrowserDetector, mock(), mock()
        )

        assertEquals(testee.primaryAction, DefaultBrowserCtaBehavior.Dialog.action)
        assertEquals(testee.ctaPixelParam, DefaultBrowserCtaBehavior.Dialog.ctaPixelParam)
        assertEquals(testee.okButton, DefaultBrowserCtaBehavior.Dialog.primaryButtonStringRes)
    }

    @Test
    fun whenUserDeviceSupportsAutomaticWidgetAddThenSearchWidgetCtaBehaviorIsAuto() {
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        val testee = DaxDialogCta.SearchWidgetCta(
            mockWidgetCapabilities, mock(), mock()
        )

        assertEquals(testee.primaryAction, SearchWidgetCtaBehavior.Automatic.action)
        assertEquals(testee.ctaPixelParam, SearchWidgetCtaBehavior.Automatic.ctaPixelParam)
    }

    @Test
    fun whenUserDeviceDoesNotSupportAutomaticWidgetAddThenSearchWidgetCtaBehaviorIsManual() {
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)

        val testee = DaxDialogCta.SearchWidgetCta(
            mockWidgetCapabilities, mock(), mock()
        )

        assertEquals(testee.primaryAction, SearchWidgetCtaBehavior.Manual.action)
        assertEquals(testee.ctaPixelParam, SearchWidgetCtaBehavior.Manual.ctaPixelParam)
    }
}