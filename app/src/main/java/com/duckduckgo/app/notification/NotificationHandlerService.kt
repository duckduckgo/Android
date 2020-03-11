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

package com.duckduckgo.app.notification

import android.app.IntentService
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APP_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.STICKY_SEARCH_QUERY
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.STICKY_SEARCH_KEEP
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.STICKY_SEARCH_REMOVE
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATION_CANCELLED
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.NOTIFICATION_LAUNCHED
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class NotificationHandlerService : IntentService("NotificationHandlerService") {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    @Inject
    lateinit var notificationScheduler: NotificationScheduler

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    @VisibleForTesting
    public override fun onHandleIntent(intent: Intent) {
        val pixelSuffix = intent.getStringExtra(PIXEL_SUFFIX_EXTRA)
        when (intent.type) {
            APP_LAUNCH -> onAppLaunched(pixelSuffix)
            CLEAR_DATA_LAUNCH -> onClearDataLaunched(pixelSuffix)
            CANCEL -> onCancelled(pixelSuffix)
            STICKY_SEARCH_REMOVE -> onStickySearchNotificationRemove(intent)
            STICKY_SEARCH_KEEP -> onStickySearchNotificationKeep(intent)
            STICKY_SEARCH_QUERY -> onSearchQueryRequest(intent)
        }

        if (intent.getBooleanExtra(NOTIFICATION_AUTO_CANCEL, true)) {
            val notificationId = intent.getIntExtra(NOTIFICATION_SYSTEM_ID_EXTRA, 0)
            clearNotification(notificationId)
            closeNotificationPanel()
        }
    }

    private fun onAppLaunched(pixelSuffix: String) {
        val intent = BrowserActivity.intent(context, newSearch = true)
        TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intent)
            .startActivities()
        pixel.fire("${NOTIFICATION_LAUNCHED.pixelName}_$pixelSuffix")
    }

    private fun onClearDataLaunched(pixelSuffix: String) {
        Timber.i("Clear Data Launched!")
        val intent = SettingsActivity.intent(context)
        TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intent)
            .startActivities()
        pixel.fire("${NOTIFICATION_LAUNCHED.pixelName}_$pixelSuffix")
    }

    private fun onCancelled(pixelSuffix: String) {
        pixel.fire("${NOTIFICATION_CANCELLED.pixelName}_$pixelSuffix")
    }

    private fun onStickySearchNotificationKeep(intent: Intent) {
        Timber.i("Sticky Search Notification Requested!")
        val pixelSuffix = intent.getStringExtra(PIXEL_SUFFIX_EXTRA)
        settingsDataStore.searchNotificationEnabled = true
        notificationScheduler.launchStickySearchNotification()
        pixel.fire(Pixel.PixelName.QUICK_SEARCH_PROMPT_NOTIFICATION_KEEP)
    }

    private fun onStickySearchNotificationRemove(intent: Intent) {
        Timber.i("Sticky Search Notification Dismissed!")
        val notificationId = intent.getIntExtra(NOTIFICATION_SYSTEM_ID_EXTRA, 0)
        pixel.fire(Pixel.PixelName.QUICK_SEARCH_PROMPT_NOTIFICATION_REMOVE)
        settingsDataStore.searchNotificationEnabled = false
        clearNotification(notificationId)
        closeNotificationPanel()
    }

    private fun onSearchQueryRequest(intent: Intent) {
        Timber.i("Search from Notification Requested!")
        val searchIntent = SystemSearchActivity.intent(context)
        TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(searchIntent)
            .startActivities()
        pixel.fire(Pixel.PixelName.QUICK_SEARCH_NOTIFICATION_PRESSED)
    }

    private fun clearNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun closeNotificationPanel() {
        val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        context.sendBroadcast(it)
    }

    object NotificationEvent {
        const val APP_LAUNCH = "com.duckduckgo.notification.launch.app"
        const val CLEAR_DATA_LAUNCH = "com.duckduckgo.notification.launch.clearData"
        const val CANCEL = "com.duckduckgo.notification.cancel"
        const val STICKY_SEARCH_KEEP = "com.duckduckgo.notification.search.accept"
        const val STICKY_SEARCH_REMOVE = "com.duckduckgo.notification.search.dismiss"
        const val STICKY_SEARCH_QUERY = "com.duckduckgo.notification.search"
    }

    companion object {
        const val PIXEL_SUFFIX_EXTRA = "PIXEL_SUFFIX_EXTRA"
        const val NOTIFICATION_SYSTEM_ID_EXTRA = "NOTIFICATION_SYSTEM_ID"
        const val NOTIFICATION_AUTO_CANCEL = "NOTIFICATION_AUTO_CANCEL"
    }
}