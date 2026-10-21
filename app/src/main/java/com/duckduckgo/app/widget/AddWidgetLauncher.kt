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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.widget.SearchAndFavoritesWidget
import com.duckduckgo.widget.SearchOnlyWidget
import com.duckduckgo.widget.SearchWidget
import com.duckduckgo.widget.SearchWidgetLight
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
class AddWidgetCompatLauncher @Inject constructor(
    @Named("appWidgetManagerAddWidgetLauncher") private val defaultAddWidgetLauncher: AddWidgetLauncher,
    @Named("legacyAddWidgetLauncher") private val legacyAddWidgetLauncher: AddWidgetLauncher,
    private val widgetCapabilities: WidgetCapabilities,
) : AddWidgetLauncher {

    override fun launchAddWidget(
        context: Context,
        widgetType: WidgetType,
    ) {
        if (widgetCapabilities.supportsAutomaticWidgetAdd) {
            defaultAddWidgetLauncher.launchAddWidget(context, widgetType)
        } else {
            legacyAddWidgetLauncher.launchAddWidget(context, widgetType)
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
        context: Context,
        widgetType: WidgetType,
    ) {
        val widgetLabel: String
        val provider = when (widgetType) {
            WidgetType.SEARCH -> {
                widgetLabel = context.getString(R.string.searchWidgetLabel)
                val widgetClass = if (appTheme.isLightModeEnabled()) SearchWidgetLight::class.java else SearchWidget::class.java
                ComponentName(context, widgetClass)
            }
            WidgetType.SEARCH_ONLY -> {
                widgetLabel = context.getString(R.string.searchOnlyWidgetLabel)
                ComponentName(context, SearchOnlyWidget::class.java)
            }
            WidgetType.SEARCH_AND_FAVORITES -> {
                widgetLabel = context.getString(R.string.favoritesWidgetLabel)
                ComponentName(context, SearchAndFavoritesWidget::class.java)
            }
        }
        AppWidgetManager.getInstance(context).requestPinAppWidget(provider, null, buildPendingIntent(context, widgetLabel))
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
class LegacyAddWidgetLauncher @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : AddWidgetLauncher {
    override fun launchAddWidget(
        context: Context,
        widgetType: WidgetType,
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            check(!appBuildConfig.isDebug && !appBuildConfig.isInternalBuild()) { "AddWidgetLauncher needs an Activity behind the context" }
            return
        }
        val options = ActivityOptions.makeSceneTransitionAnimation(activity).toBundle()
        activity.startActivity(AddWidgetInstructionsActivity.intent(activity), options)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
