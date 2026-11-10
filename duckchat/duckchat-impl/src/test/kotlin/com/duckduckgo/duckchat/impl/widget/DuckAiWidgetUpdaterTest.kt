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
package com.duckduckgo.duckchat.impl.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetUpdaterTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private val testee = DuckAiWidgetUpdater(context)

    @Test
    fun whenPrivacyConfigDownloadedThenPlacedWidgetIsRefreshed() {
        val widgetId = createWidget()

        testee.onPrivacyConfigDownloaded()

        val intent = shadowOf(context).broadcastIntents.single()
        assertEquals(DuckAiWidgetProvider::class.java.name, intent.component?.className)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_UPDATE, intent.action)
        assertEquals(listOf(widgetId), intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.toList())
    }

    @Test
    fun whenPrivacyConfigDownloadedWithoutWidgetsThenNoBroadcast() {
        testee.onPrivacyConfigDownloaded()

        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
    }

    private fun createWidget(widgetId: Int = WIDGET_ID): Int {
        val provider = ComponentName(context, DuckAiWidgetProvider::class.java)
        val manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply { this.provider = provider })
        manager.bindAppWidgetIdIfAllowed(widgetId, provider)
        return widgetId
    }

    private companion object {
        const val WIDGET_ID = 42
    }
}
