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

package com.duckduckgo.app.widget.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppWidgetCapabilitiesTest {

    private val manager: AppWidgetManager = mock()
    private val context: Context = mock<Context>().also {
        whenever(it.packageName).thenReturn(PACKAGE)
        whenever(it.getSystemService(Context.APPWIDGET_SERVICE)).thenReturn(manager)
    }
    private val installed = mutableListOf<AppWidgetProviderInfo>()
    private val testee = AppWidgetCapabilities(context)

    @Test
    fun whenNoProviderInstalledThenHasNoInstalledWidgets() {
        whenever(manager.getInstalledProvidersForPackage(eq(PACKAGE), anyOrNull())).thenReturn(emptyList())

        assertFalse(testee.hasInstalledWidgets)
    }

    @Test
    fun whenProvidersHaveNoPlacedWidgetsThenHasNoInstalledWidgets() {
        givenProvider(SEARCH_PROVIDER, placedIds = intArrayOf())
        givenProvider(DUCK_AI_PROVIDER, placedIds = intArrayOf())

        assertFalse(testee.hasInstalledWidgets)
    }

    @Test
    fun whenAnyOwnProviderHasPlacedWidgetThenHasInstalledWidgets() {
        givenProvider(SEARCH_PROVIDER, placedIds = intArrayOf())
        givenProvider(DUCK_AI_PROVIDER, placedIds = intArrayOf(7))

        assertTrue(testee.hasInstalledWidgets)
    }

    private fun givenProvider(
        className: String,
        placedIds: IntArray,
    ) {
        val component = ComponentName(PACKAGE, className)
        whenever(manager.getAppWidgetIds(component)).thenReturn(placedIds)
        installed += AppWidgetProviderInfo().also { it.provider = component }
        whenever(manager.getInstalledProvidersForPackage(eq(PACKAGE), anyOrNull())).thenReturn(installed.toList())
    }

    private companion object {
        const val PACKAGE = "com.duckduckgo.mobile.android"
        const val SEARCH_PROVIDER = "com.duckduckgo.widget.SearchWidget"
        const val DUCK_AI_PROVIDER = "com.duckduckgo.duckchat.impl.widget.DuckAiWidgetProvider"
    }
}
