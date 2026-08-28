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

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WidgetPrivacyConfigUpdateListenerTest {

    private val context: Context = mock()
    private val widgetUpdater: WidgetUpdater = mock()
    private val providerInfoUpdater: SearchWidgetProviderInfoUpdater = mock()
    private val toggles: AppBrandDesignUpdateToggles = mock()
    private val addressBarToggle: Toggle = mock()
    private val testee = WidgetPrivacyConfigUpdateListener(context, widgetUpdater, providerInfoUpdater, toggles)

    @Before
    fun setUp() {
        whenever(toggles.addressBar()).thenReturn(addressBarToggle)
    }

    @Test
    fun whenProcessEntersForegroundThenProviderInfoIsSynchronizedBeforeWidgets() {
        whenever(addressBarToggle.isEnabled()).thenReturn(false)

        testee.onStart(mock<LifecycleOwner>())

        inOrder(providerInfoUpdater, widgetUpdater) {
            verify(providerInfoUpdater).sync()
            verify(widgetUpdater).updateWidgets(context)
        }
    }

    @Test
    fun whenProcessEntersForegroundRepeatedlyWithSameFlagThenWidgetsAreRefreshedOnce() {
        whenever(addressBarToggle.isEnabled()).thenReturn(false)

        testee.onStart(mock<LifecycleOwner>())
        testee.onStart(mock<LifecycleOwner>())

        verify(providerInfoUpdater).sync()
        verify(widgetUpdater).updateWidgets(context)
    }

    @Test
    fun whenAddressBarFlagChangesBetweenForegroundsThenWidgetsAreRefreshedAgain() {
        whenever(addressBarToggle.isEnabled()).thenReturn(false, true)

        testee.onStart(mock<LifecycleOwner>())
        testee.onStart(mock<LifecycleOwner>())

        verify(providerInfoUpdater, times(2)).sync()
        verify(widgetUpdater, times(2)).updateWidgets(context)
    }

    @Test
    fun whenPrivacyConfigChangesWithSameFlagThenWidgetsAreRefreshedAgain() {
        whenever(addressBarToggle.isEnabled()).thenReturn(false)

        testee.onStart(mock<LifecycleOwner>())
        testee.onPrivacyConfigDownloaded()

        inOrder(providerInfoUpdater, widgetUpdater) {
            verify(providerInfoUpdater).sync()
            verify(widgetUpdater).updateWidgets(context)
            verify(providerInfoUpdater).sync()
            verify(widgetUpdater).updateWidgets(context)
        }
    }
}
