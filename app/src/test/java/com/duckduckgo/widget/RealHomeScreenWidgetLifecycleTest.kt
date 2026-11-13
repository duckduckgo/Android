/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.widget

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.nextsteps.NextStepsAdoptionMetrics
import com.duckduckgo.app.pixels.AppPixelName.WIDGETS_ADDED
import com.duckduckgo.app.pixels.AppPixelName.WIDGETS_DELETED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealHomeScreenWidgetLifecycleTest {

    private val appInstallStore: AppInstallStore = mock()
    private val widgetCapabilities: WidgetCapabilities = mock()
    private val pixel: Pixel = mock()
    private val nextStepsAdoptionMetrics: NextStepsAdoptionMetrics = mock()
    private val testee = RealHomeScreenWidgetLifecycle(appInstallStore, widgetCapabilities, pixel, nextStepsAdoptionMetrics)

    @Test
    fun whenFirstWidgetEnabledThenMarksInstalledAndFiresAddedPixel() {
        whenever(appInstallStore.widgetInstalled).thenReturn(false)

        testee.onWidgetEnabled()

        verify(appInstallStore).widgetInstalled = true
        verify(pixel).fire(WIDGETS_ADDED)
    }

    @Test
    fun whenWidgetAlreadyInstalledThenAddedPixelNotFiredAgain() {
        whenever(appInstallStore.widgetInstalled).thenReturn(true)

        testee.onWidgetEnabled()

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenWidgetEnabledThenReportsNextStepsAdoption() {
        whenever(appInstallStore.widgetInstalled).thenReturn(true)

        testee.onWidgetEnabled()

        verify(nextStepsAdoptionMetrics).onWidgetAdded()
    }

    @Test
    fun whenLastWidgetDisabledThenMarksNotInstalledAndFiresDeletedPixel() {
        whenever(appInstallStore.widgetInstalled).thenReturn(true)
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.onWidgetDisabled()

        verify(appInstallStore).widgetInstalled = false
        verify(pixel).fire(WIDGETS_DELETED)
    }

    @Test
    fun whenOtherWidgetsRemainThenDeletedPixelNotFired() {
        whenever(appInstallStore.widgetInstalled).thenReturn(true)
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(true)

        testee.onWidgetDisabled()

        verify(appInstallStore, never()).widgetInstalled = false
        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenNoWidgetWasInstalledThenDeletedPixelNotFired() {
        whenever(appInstallStore.widgetInstalled).thenReturn(false)
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.onWidgetDisabled()

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }
}
