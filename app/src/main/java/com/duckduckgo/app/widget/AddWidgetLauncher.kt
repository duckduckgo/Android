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
import android.content.ContextWrapper
import android.content.Intent
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsActivity
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.widget.SearchAndFavoritesWidget
import com.duckduckgo.widget.SearchOnlyWidget
import com.duckduckgo.widget.SearchWidget
import com.duckduckgo.widget.SearchWidgetLight
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
class AddWidgetCompatLauncher @Inject constructor(
    @Named("appWidgetManagerAddWidgetLauncher") private val defaultAddWidgetLauncher: AddWidgetLauncher,
    @Named("legacyAddWidgetLauncher") private val legacyAddWidgetLauncher: AddWidgetLauncher,
    private val widgetCapabilities: WidgetCapabilities,
) : AddWidgetLauncher {

    override fun launchAddWidget(
        activityContext: Context,
        simpleWidgetPrompt: Boolean,
        searchOnlyWidgetPrompt: Boolean,
        duckAiOnlyWidgetPrompt: Boolean,
    ) {
        if (widgetCapabilities.supportsAutomaticWidgetAdd) {
            defaultAddWidgetLauncher.launchAddWidget(activityContext, simpleWidgetPrompt, searchOnlyWidgetPrompt, duckAiOnlyWidgetPrompt)
        } else {
            legacyAddWidgetLauncher.launchAddWidget(activityContext, simpleWidgetPrompt, searchOnlyWidgetPrompt, duckAiOnlyWidgetPrompt)
        }
    }
}

@ContributesBinding(AppScope::class)
@Named("appWidgetManagerAddWidgetLauncher")
class AppWidgetManagerAddWidgetLauncher @Inject constructor(
    private val appTheme: AppTheme,
) : AddWidgetLauncher {
    companion object {
        const val ACTION_ADD_WIDGET = "actionWidgetAdded"
        const val EXTRA_WIDGET_ADDED_LABEL = "extraWidgetAddedLabel"
        private const val CODE_ADD_WIDGET = 11922
    }

    @SuppressLint("NewApi")
    override fun launchAddWidget(
        activityContext: Context,
        simpleWidgetPrompt: Boolean,
        searchOnlyWidgetPrompt: Boolean,
        duckAiOnlyWidgetPrompt: Boolean,
    ) {
        val widgetLabel: String
        val provider = when {
            simpleWidgetPrompt && appTheme.isLightModeEnabled() -> {
                widgetLabel = activityContext.getString(R.string.searchWidgetLabel)
                ComponentName(activityContext, SearchWidgetLight::class.java)
            }
            simpleWidgetPrompt -> {
                widgetLabel = activityContext.getString(R.string.searchWidgetLabel)
                ComponentName(activityContext, SearchWidget::class.java)
            }
            searchOnlyWidgetPrompt -> {
                widgetLabel = activityContext.getString(R.string.searchOnlyWidgetLabel)
                ComponentName(activityContext, SearchOnlyWidget::class.java)
            }
            else -> {
                widgetLabel = activityContext.getString(R.string.favoritesWidgetLabel)
                ComponentName(activityContext, SearchAndFavoritesWidget::class.java)
            }
        }
        AppWidgetManager.getInstance(activityContext).requestPinAppWidget(provider, null, buildPendingIntent(activityContext, widgetLabel))
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun buildPendingIntent(context: Context, widgetLabel: String): PendingIntent? {
        val intent = Intent(ACTION_ADD_WIDGET).run {
            putExtra(EXTRA_WIDGET_ADDED_LABEL, widgetLabel)
        }
        return PendingIntent.getBroadcast(
            context,
            CODE_ADD_WIDGET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) // Will not return null since FLAG_UPDATE_CURRENT has been supplied
    }
}

@ContributesBinding(AppScope::class)
@Named("legacyAddWidgetLauncher")
class LegacyAddWidgetLauncher @Inject constructor() : AddWidgetLauncher {
    override fun launchAddWidget(
        activityContext: Context,
        simpleWidgetPrompt: Boolean,
        searchOnlyWidgetPrompt: Boolean,
        duckAiOnlyWidgetPrompt: Boolean,
    ) {
        val options = activityContext.findActivity()?.let { ActivityOptions.makeSceneTransitionAnimation(it).toBundle() }
        activityContext.startActivity(AddWidgetInstructionsActivity.intent(activityContext), options)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
