/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_LOCALE_CHANGED
import android.content.ComponentName

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE

class VoiceSearchWidgetUpdater : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action == ACTION_LOCALE_CHANGED) {
            updateWidgets(context.applicationContext)
        }
    }

    private fun updateWidgets(context: Context) {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchWidget::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                SearchWidget::class.java
            )
        }

        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchWidgetLight::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                SearchWidgetLight::class.java
            )
        }

        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchAndFavoritesWidget::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                SearchAndFavoritesWidget::class.java
            )
        }
    }

    private fun broadcastUpdate(
        id: IntArray,
        context: Context,
        clazz: Class<*>
    ) {
        val intent = Intent(context, clazz)
        intent.action = ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, id)
        context.sendBroadcast(intent)
    }
}
