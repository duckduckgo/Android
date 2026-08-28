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
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Keeps the launcher-owned search widget previews aligned with the temporary address bar rollout.
 *
 * Android persists provider info changes, so this is synchronized at process creation, after privacy
 * config downloads, and immediately before requesting a pinned widget.
 */
@SingleInstanceIn(AppScope::class)
class SearchWidgetProviderInfoUpdater internal constructor(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val appBrandDesignUpdateToggles: AppBrandDesignUpdateToggles,
) {

    @Inject
    constructor(
        context: Context,
        appBrandDesignUpdateToggles: AppBrandDesignUpdateToggles,
    ) : this(
        context = context,
        appWidgetManager = AppWidgetManager.getInstance(context),
        appBrandDesignUpdateToggles = appBrandDesignUpdateToggles,
    )

    fun sync() {
        val metadataKey = if (appBrandDesignUpdateToggles.addressBar().isEnabled()) {
            null
        } else {
            LEGACY_PROVIDER_INFO_METADATA_KEY
        }

        appWidgetManager.updateAppWidgetProviderInfo(ComponentName(context, SearchWidget::class.java), metadataKey)
        appWidgetManager.updateAppWidgetProviderInfo(ComponentName(context, SearchWidgetLight::class.java), metadataKey)
        appWidgetManager.updateAppWidgetProviderInfo(ComponentName(context, SearchOnlyWidget::class.java), metadataKey)
        appWidgetManager.updateAppWidgetProviderInfo(ComponentName(context, SearchAndFavoritesWidget::class.java), metadataKey)
    }

    fun syncAndRequestPinAppWidget(
        provider: ComponentName,
        successCallback: PendingIntent?,
    ) {
        sync()
        appWidgetManager.requestPinAppWidget(provider, null, successCallback)
    }

    private companion object {
        const val LEGACY_PROVIDER_INFO_METADATA_KEY = "com.duckduckgo.widget.legacy_provider_info"
    }
}
