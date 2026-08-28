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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SearchWidgetProviderInfoUpdaterTest {

    private val context: Context = mock()
    private val appWidgetManager: AppWidgetManager = mock()
    private val toggles: AppBrandDesignUpdateToggles = mock()
    private val addressBarToggle: Toggle = mock()
    private val pendingIntent: PendingIntent = mock()

    private lateinit var testee: SearchWidgetProviderInfoUpdater

    @Before
    fun setUp() {
        whenever(context.packageName).thenReturn(PACKAGE_NAME)
        whenever(toggles.addressBar()).thenReturn(addressBarToggle)
        testee = SearchWidgetProviderInfoUpdater(context, appWidgetManager, toggles)
    }

    @Test
    fun whenAddressBarEnabledThenAllSearchWidgetProvidersUseDefaultInfo() {
        whenever(addressBarToggle.isEnabled()).thenReturn(true)

        testee.sync()

        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchWidget::class.java.name)),
            isNull(),
        )
        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchWidgetLight::class.java.name)),
            isNull(),
        )
        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchOnlyWidget::class.java.name)),
            isNull(),
        )
        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchAndFavoritesWidget::class.java.name)),
            isNull(),
        )
    }

    @Test
    fun whenAddressBarDisabledThenAllSearchWidgetProvidersUseLegacyInfo() {
        whenever(addressBarToggle.isEnabled()).thenReturn(false)

        testee.sync()

        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchWidget::class.java.name)),
            eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
        )
        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchWidgetLight::class.java.name)),
            eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
        )
        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchOnlyWidget::class.java.name)),
            eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
        )
        verify(appWidgetManager).updateAppWidgetProviderInfo(
            eq(ComponentName(PACKAGE_NAME, SearchAndFavoritesWidget::class.java.name)),
            eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
        )
    }

    @Test
    fun whenPinningWidgetThenProviderInfoIsSynchronizedBeforeRequest() {
        whenever(addressBarToggle.isEnabled()).thenReturn(false)
        val provider = ComponentName(PACKAGE_NAME, SearchWidget::class.java.name)

        testee.syncAndRequestPinAppWidget(provider, pendingIntent)

        inOrder(appWidgetManager) {
            verify(appWidgetManager).updateAppWidgetProviderInfo(
                eq(ComponentName(PACKAGE_NAME, SearchWidget::class.java.name)),
                eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
            )
            verify(appWidgetManager).updateAppWidgetProviderInfo(
                eq(ComponentName(PACKAGE_NAME, SearchWidgetLight::class.java.name)),
                eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
            )
            verify(appWidgetManager).updateAppWidgetProviderInfo(
                eq(ComponentName(PACKAGE_NAME, SearchOnlyWidget::class.java.name)),
                eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
            )
            verify(appWidgetManager).updateAppWidgetProviderInfo(
                eq(ComponentName(PACKAGE_NAME, SearchAndFavoritesWidget::class.java.name)),
                eq(LEGACY_PROVIDER_INFO_METADATA_KEY),
            )
            verify(appWidgetManager).requestPinAppWidget(provider, null, pendingIntent)
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.duckduckgo.mobile.android"
        private const val LEGACY_PROVIDER_INFO_METADATA_KEY = "com.duckduckgo.widget.legacy_provider_info"
    }
}
