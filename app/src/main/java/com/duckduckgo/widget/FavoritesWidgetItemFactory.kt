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

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
import com.duckduckgo.savedsites.api.models.SavedSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import logcat.logcat
import java.io.File
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@SuppressLint("DenyListedApi")
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
    lateinit var widgetPrefs: WidgetPreferences

    @Inject
    lateinit var faviconPersister: FaviconPersister

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

    data class WidgetFavorite(
        val title: String,
        val url: String,
        val bitmapUri: Uri?,
    )

    private val _widgetFavoritesFlow = MutableStateFlow<List<WidgetFavorite>>(emptyList())

    private val currentFavorites: List<WidgetFavorite>
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

    private suspend fun fetchFavoritesWithBitmapUris(): List<WidgetFavorite> {
        return withContext(dispatchers.io()) {
            val deferredFavorites = savedSitesRepository
                .getFavoritesSync()
                .take(maxItems)
                .map { favorite ->
                    favorite.toWidgetFavorite()
                }
            deferredFavorites
        }
    }

    /**
     * Converts a SavedSite.Favorite to a WidgetFavorite by ensuring we have a bitmap URI for the favicon.
     */
    private suspend fun SavedSite.Favorite.toWidgetFavorite(): WidgetFavorite {
        val domain = url.extractDomain().orEmpty()

        // step 1: check if any file (real favicon or placeholder) already exists on disk to avoid fetching/generating it again
        val existingFile = faviconPersister.faviconFile(
            directory = FAVICON_PERSISTED_DIR,
            subFolder = NO_SUBFOLDER,
            domain = domain,
        )
        var uri: Uri? = null

        if (existingFile != null) {
            // found existing file on disk (favicon or placeholder) - use it without network call
            uri = existingFile.getContentUri()
        }
        if (uri != null) {
            return WidgetFavorite(
                title = title,
                url = url,
                bitmapUri = uri,
            )
        }

        // step 2: generate and save placeholder
        val placeholderBitmap = generateDefaultDrawable(
            context = context,
            domain = domain,
            cornerRadius = faviconItemCornerRadius,
        ).toBitmap(faviconItemSize, faviconItemSize)
        uri = faviconPersister.store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, placeholderBitmap, domain)?.getContentUri()

        return WidgetFavorite(
            title = title,
            url = url,
            bitmapUri = uri,
        )
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
            }
            remoteViews.setViewVisibility(R.id.quickAccessFaviconContainer, View.VISIBLE)
            remoteViews.setTextViewText(R.id.quickAccessTitle, item.title)
            remoteViews.setViewVisibility(R.id.quickAccessTitle, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.placeholderFavicon, View.GONE)
            configureClickListener(remoteViews, item.url)
        } else {
            if (currentFavorites.isEmpty()) {
                // We don't have any favorites, show placeholder view.
                remoteViews.setViewVisibility(R.id.quickAccessFaviconContainer, View.VISIBLE)
                remoteViews.setViewVisibility(R.id.quickAccessFavicon, View.GONE)
                remoteViews.setViewVisibility(R.id.placeholderFavicon, View.VISIBLE)
            } else {
                // We had at least one favorite, but not in this view. Don't show anything.
                remoteViews.setViewVisibility(R.id.quickAccessFaviconContainer, View.INVISIBLE)
            }
            remoteViews.setViewVisibility(R.id.quickAccessTitle, View.GONE)
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
        return RemoteViews(context.packageName, getItemLayout())
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
     * Creates a content URI for the given file that can be used for loading an image in the widget via URI.
     */
    private fun File.getContentUri(): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.$PROVIDER_SUFFIX", this).also { uri ->
            uri.grantPermissionsToWidget()
        }
    }.getOrNull()

    /**
     * Grants URI read permissions to packages that need to display the widget.
     *
     * This is needed for the RemoteViews to load the images from the content URI.
     */
    private fun Uri.grantPermissionsToWidget() {
        runCatching {
            // grant to system server which manages RemoteViews
            context.grantUriPermission(
                "android",
                this,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )

            // grant to the current default launcher/home app
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(launcherIntent, 0)
            resolveInfo?.activityInfo?.packageName?.let { launcherPackage ->
                context.grantUriPermission(
                    launcherPackage,
                    this,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } ?: logcat { "Could not determine launcher package for URI permissions" }
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
        private const val PROVIDER_SUFFIX = "provider"
    }
}
