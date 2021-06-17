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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconPersister
import com.duckduckgo.app.browser.favicon.FaviconSource
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.view.generateDefaultDrawable
import com.duckduckgo.app.global.view.toDp
import com.duckduckgo.app.global.view.toPx
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.widget.WidgetTheme
import timber.log.Timber
import javax.inject.Inject

class FavoritesWidgetService : RemoteViewsService() {

    companion object {
        const val MAX_ITEMS_EXTRAS = "MAX_ITEMS_EXTRAS"
        const val THEME_EXTRAS = "THEME_EXTRAS"
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FavoritesWidgetItemFactory(this.applicationContext, intent)
    }

    class FavoritesWidgetItemFactory(val context: Context, intent: Intent) : RemoteViewsFactory {

        private val maxItems = intent.extras?.getInt(MAX_ITEMS_EXTRAS, 2) ?: 2

        private val theme =  WidgetTheme.getThemeFrom(intent.extras?.getString(THEME_EXTRAS))

        @Inject
        lateinit var favoritesDataRepository: FavoritesRepository

        @Inject
        lateinit var faviconPersister: FaviconPersister

        private val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        data class WidgetFavorite(val title: String, val url: String, val bitmap: Bitmap?)
        private val domains = mutableListOf<WidgetFavorite>()

        override fun onCreate() {
            inject(context)
        }

        override fun onDataSetChanged() {
            domains.clear()
            domains.addAll(favoritesDataRepository.favoritesBlockingGet().map {
                val faviconFile = faviconPersister.faviconFile(
                    FileBasedFaviconPersister.FAVICON_PERSISTED_DIR,
                    FileBasedFaviconPersister.NO_SUBFOLDER,
                    it.url.extractDomain()!!
                )
                val bitmap = if(faviconFile != null) {
                    Glide.with(context)
                        .asBitmap()
                        .load(faviconFile)
                        .transform(RoundedCorners(10.toPx()))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .submit(56.toPx(), 56.toPx())
                        .get()
                } else {
                    generateDefaultDrawable(context, it.url.extractDomain()!!).toBitmap(56.toPx(), 56.toPx())
                }

                WidgetFavorite(it.title, it.url, bitmap)
            })
        }

        override fun onDestroy() {
            Timber.i("SearchAndFavoritesWidget - onDestroy")
        }

        override fun getCount(): Int {
            Timber.i("SearchAndFavoritesWidget - getCount")
            return domains.size.coerceAtMost(maxItems)
        }

        private fun String.extractDomain(): String? {
            return if (this.startsWith("http")) {
                this.toUri().domain()
            } else {
                "https://$this".extractDomain()
            }
        }

        override fun getViewAt(position: Int): RemoteViews {
            Timber.i("SearchAndFavoritesWidget - getViewAt")
            val item = if (position >= domains.size) null else domains[position]
            val remoteViews = RemoteViews(context.packageName, getItemLayout())
            if(item != null) {
                if (item.bitmap != null) {
                    remoteViews.setImageViewBitmap(R.id.quickAccessFavicon, item.bitmap);
                }
                remoteViews.setTextViewText(R.id.quickAccessTitle, item.title)
                configureClickListener(remoteViews, item.url)
            } else {
                remoteViews.setTextViewText(R.id.quickAccessTitle, "")
                remoteViews.setImageViewResource(R.id.quickAccessFavicon, R.drawable.search_widget_favorite_favicon_light_background)
            }

            return remoteViews
        }

        private fun getItemLayout(): Int {
            Timber.i("SearchAndFavoritesWidget - fav getItemLayout for $theme")
            return when (theme) {
                WidgetTheme.LIGHT -> R.layout.view_favorite_widget_light_item
                WidgetTheme.DARK -> R.layout.view_favorite_widget_dark_item
                WidgetTheme.SYSTEM_DEFAULT -> R.layout.view_favorite_widget_daynight_item
            }
        }

        private fun configureClickListener(remoteViews: RemoteViews, item: String) {
            val bundle = Bundle()
            bundle.putString(Intent.EXTRA_TEXT, item)
            bundle.putBoolean(BrowserActivity.NEW_SEARCH_EXTRA, false)
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

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = SystemSearchActivity.fromWidget(context)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun inject(context: Context) {
            val application = context.applicationContext as DuckDuckGoApplication
            application.daggerAppComponent.inject(this)
        }
    }
}
