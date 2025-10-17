/*
 * Copyright (c) 2021 DuckDuckGo
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
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favicon.FaviconPersister
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.global.view.generateDefaultDrawable
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.domain
import com.duckduckgo.savedsites.api.SavedSitesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

class FavoritesWidgetItemFactory(
    val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private val theme = WidgetTheme.getThemeFrom(intent.extras?.getString(THEME_EXTRAS))

    @Inject
    lateinit var savedSitesRepository: SavedSitesRepository

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var faviconPersister: FaviconPersister

    @Inject
    lateinit var widgetPrefs: WidgetPreferences

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID,
    )

    private val faviconItemSize = context.resources.getDimension(CommonR.dimen.savedSiteGridItemFavicon).toInt()
    private val faviconItemCornerRadius = CommonR.dimen.searchWidgetFavoritesCornerRadius

    private val maxItems: Int
        get() {
            return widgetPrefs.widgetSize(appWidgetId).let { it.first * it.second }
        }

    // New data class returning a Uri reference instead of an in-memory Bitmap
    /**
     * Represents a widget favorite whose favicon is exposed as a Uri rather than an in-memory Bitmap.
     * If the favicon file cannot be found locally, [bitmapUri] will be null and callers should
     * fallback to a placeholder (as done for the Bitmap variant).
     */
    data class WidgetFavoriteUri(
        val title: String,
        val url: String,
        val bitmapUri: Uri?,
    )

    private val _widgetFavoritesFlow = MutableStateFlow<List<WidgetFavoriteUri>>(emptyList())

    private val currentFavorites: List<WidgetFavoriteUri>
        get() = _widgetFavoritesFlow.value

    override fun onCreate() {
        inject(context)
    }

    override fun onDataSetChanged() {
        // no-op, we use our own update mechanism
    }

    suspend fun updateWidgetFavoritesAsync() {
        runCatching {
            val latestWidgetFavorites = fetchFavoritesWithBitmapUris()
            _widgetFavoritesFlow.value = latestWidgetFavorites
        }.onFailure { error ->
            logcat { "Failed to update favorites in Search and Favorites widget: ${error.message}" }
        }
    }

    // New function to fetch favorites with bitmap URIs
    private suspend fun fetchFavoritesWithBitmapUris(): List<WidgetFavoriteUri> {
        return withContext(dispatchers.io()) {
            savedSitesRepository.getFavoritesSync().take(maxItems).map { favorite ->
                val file = runCatching { faviconManager.tryFetchFaviconForUrl(favorite.url) }.getOrNull()
                val uri = if (file != null) {
                    // Use existing favicon file
                    runCatching {
                        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        // Grant permission for widget launchers to read the URI
                        grantUriPermissionsForWidget(contentUri)
                        contentUri
                    }.getOrNull()
                } else {
                    // Generate and save placeholder if no favicon exists
                    val placeholder = generateDefaultDrawable(
                        context = context,
                        domain = favorite.url.extractDomain().orEmpty(),
                        cornerRadius = faviconItemCornerRadius,
                    ).toBitmap(faviconItemSize, faviconItemSize)
                    saveBitmapAndGetUri(placeholder, favorite.url.extractDomain().orEmpty())
                }
                WidgetFavoriteUri(
                    title = favorite.title,
                    url = favorite.url,
                    bitmapUri = uri,
                )
            }
        }
    }

    override fun onDestroy() {
        // no-op
    }

    override fun getCount(): Int {
        return maxItems
    }

    private fun String.extractDomain(): String? {
        return if (this.startsWith("http")) {
            this.toUri().domain()
        } else {
            "https://$this".extractDomain()
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val item = if (position >= currentFavorites.size) null else currentFavorites[position]

        val remoteViews = RemoteViews(context.packageName, getItemLayout())
        if (item != null) {
            // This item has a favorite. Show the favorite view.
            if (item.bitmapUri != null) {
                remoteViews.setViewVisibility(R.id.quickAccessFavicon, View.VISIBLE)
                remoteViews.setImageViewUri(R.id.quickAccessFavicon, item.bitmapUri)
            } else {
                // If we don't have a URI (shouldn't happen), hide the favicon
                remoteViews.setViewVisibility(R.id.quickAccessFavicon, View.GONE)
            }
            remoteViews.setViewVisibility(R.id.quickAccessFaviconContainer, View.VISIBLE)
            remoteViews.setTextViewText(R.id.quickAccessTitle, item.title)
            remoteViews.setViewVisibility(R.id.quickAccessTitle, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.placeholderFavicon, View.GONE)
            configureClickListener(remoteViews, item.url)
        } else {
            return RemoteViews(context.packageName, R.layout.empty_view)
        }

        return remoteViews
    }

    private fun getItemLayout(): Int {
        return when (theme) {
            WidgetTheme.LIGHT -> R.layout.view_favorite_widget_light_item
            WidgetTheme.DARK -> R.layout.view_favorite_widget_dark_item
            WidgetTheme.SYSTEM_DEFAULT -> R.layout.view_favorite_widget_daynight_item
        }
    }

    private fun configureClickListener(
        remoteViews: RemoteViews,
        item: String,
    ) {
        val bundle = Bundle()
        bundle.putString(Intent.EXTRA_TEXT, item)
        bundle.putBoolean(BrowserActivity.NEW_SEARCH_EXTRA, false)
        bundle.putBoolean(BrowserActivity.LAUNCH_FROM_FAVORITES_WIDGET, true)
        bundle.putBoolean(BrowserActivity.NOTIFY_DATA_CLEARED_EXTRA, false)
        val intent = Intent()
        intent.putExtras(bundle)
        remoteViews.setOnClickFillInIntent(R.id.quickAccessFaviconContainer, intent)
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.empty_view)
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    /**
     * Saves a bitmap to a file and returns a content URI for it.
     * This prevents RemoteViews crashes from storing whole bitmaps directly.
     * Uses FaviconPersister to save the bitmap in the same way as real favicons.
     */
    private suspend fun saveBitmapAndGetUri(bitmap: Bitmap, domain: String): Uri? {
        return runCatching {
            // Use FaviconPersister to store the placeholder bitmap in the same directory as real favicons
            val file = faviconPersister.store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, bitmap, domain)

            if (file != null) {
                // Return a content URI for the file using the correct FileProvider authority
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

                // Grant read permission to all packages that might host the widget
                grantUriPermissionsForWidget(uri)

                uri
            } else {
                null
            }
        }.getOrElse { error ->
            logcat { "Failed to save placeholder bitmap: ${error.message}" }
            null
        }
    }

    /**
     * Grants read URI permission to packages that host widgets (launchers).
     * We grant to common launcher packages since we can't reliably detect which launcher is being used.
     */
    private fun grantUriPermissionsForWidget(uri: Uri) {
        runCatching {
            // Grant to system server which manages RemoteViews
            context.grantUriPermission(
                "android",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )

            // Grant to common launcher packages
            val commonLaunchers = listOf(
                "com.android.launcher3", // AOSP/Pixel Launcher
                "com.google.android.apps.nexuslauncher", // Pixel Launcher
                "com.android.launcher",
                "com.sec.android.app.launcher", // Samsung
                "com.huawei.android.launcher", // Huawei
                "com.mi.android.globallauncher", // Xiaomi
                "com.miui.home", // MIUI
                "com.oneplus.launcher", // OnePlus
            )

            commonLaunchers.forEach { packageName ->
                runCatching {
                    context.grantUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        }.onFailure { error ->
            logcat { "Failed to grant URI permissions: ${error.message}" }
        }
    }

    private fun inject(context: Context) {
        val application = context.applicationContext as DuckDuckGoApplication
        application.daggerAppComponent.inject(this)
    }

    companion object {
        const val THEME_EXTRAS = "THEME_EXTRAS"
    }
}
