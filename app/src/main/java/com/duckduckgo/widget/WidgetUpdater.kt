/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface WidgetUpdater {
    fun updateWidgets(context: Context)
}

@ContributesBinding(AppScope::class)
class WidgetUpdaterImpl @Inject constructor() : WidgetUpdater {

    override fun updateWidgets(context: Context) {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchWidget::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                SearchWidget::class.java,
            )
        }

        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchWidgetLight::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                SearchWidgetLight::class.java,
            )
        }

        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchOnlyWidget::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                SearchOnlyWidget::class.java,
            )
        }

        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DuckAiOnlyWidget::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                DuckAiOnlyWidget::class.java,
            )
        }

        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SearchAndFavoritesWidget::class.java))?.also {
            broadcastUpdate(
                it,
                context,
                SearchAndFavoritesWidget::class.java,
            )
        }
    }

    private fun broadcastUpdate(
        id: IntArray,
        context: Context,
        clazz: Class<*>,
    ) {
        val intent = Intent(context, clazz)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, id)
        context.sendBroadcast(intent)
    }
}
