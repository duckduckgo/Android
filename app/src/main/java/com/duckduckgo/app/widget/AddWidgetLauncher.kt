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

package com.duckduckgo.app.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsActivity
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.widget.SearchAndFavoritesWidget
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AddWidgetLauncher {
    fun launchAddWidget(activity: Activity?)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AddWidgetCompatLauncher @Inject constructor(
    private val defaultAddWidgetLauncher: AppWidgetManagerAddWidgetLauncher,
    private val legacyAddWidgetLauncher: LegacyAddWidgetLauncher,
    private val widgetCapabilities: WidgetCapabilities
) : AddWidgetLauncher {

    override fun launchAddWidget(activity: Activity?) {
        if (widgetCapabilities.supportsAutomaticWidgetAdd)
            defaultAddWidgetLauncher.launchAddWidget(activity) else legacyAddWidgetLauncher.launchAddWidget(activity)
    }
}

class AppWidgetManagerAddWidgetLauncher @Inject constructor() : AddWidgetLauncher {
    companion object {
        const val ACTION_ADD_WIDGET = "actionWidgetAdded"
        private const val CODE_ADD_WIDGET = 11922
    }

    @SuppressLint("NewApi")
    override fun launchAddWidget(activity: Activity?) {
        activity?.let {
            val provider = ComponentName(it, SearchAndFavoritesWidget::class.java)
            AppWidgetManager.getInstance(it).requestPinAppWidget(provider, null, buildPendingIntent(it))
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun buildPendingIntent(context: Context): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            CODE_ADD_WIDGET,
            Intent(ACTION_ADD_WIDGET),
            PendingIntent.FLAG_UPDATE_CURRENT
        ) // Will not return null since FLAG_UPDATE_CURRENT has been supplied
    }
}

class LegacyAddWidgetLauncher @Inject constructor() : AddWidgetLauncher {
    override fun launchAddWidget(activity: Activity?) {
        activity?.let {
            val options = ActivityOptions.makeSceneTransitionAnimation(it).toBundle()
            it.startActivity(AddWidgetInstructionsActivity.intent(it), options)
        }
    }
}
