/*
 * Copyright (c) 2019 DuckDuckGo
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
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.duckduckgo.widget.SearchWidget
import com.duckduckgo.widget.SearchWidgetLight
import javax.inject.Inject


interface WidgetCapabilities {
    val supportsStandardWidgetAdd: Boolean
    val supportsAutomaticWidgetAdd: Boolean
    val hasInstalledWidgets: Boolean
}

class AppWidgetCapabilities @Inject constructor(val context: Context) : WidgetCapabilities {

    override val supportsStandardWidgetAdd: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    override val supportsAutomaticWidgetAdd: Boolean
        get() = context.supportsAutomaticWidgetAdd

    override val hasInstalledWidgets: Boolean
        get() = context.hasInstalledWidgets
}

val Context.supportsAutomaticWidgetAdd: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported

val Context.hasInstalledWidgets: Boolean
    get() {
        val manager = AppWidgetManager.getInstance(this)
        val hasDarkWidget = manager.getAppWidgetIds(ComponentName(this, SearchWidget::class.java)).any()
        val hasLightWidget = manager.getAppWidgetIds(ComponentName(this, SearchWidgetLight::class.java)).any()
        return hasDarkWidget || hasLightWidget
    }