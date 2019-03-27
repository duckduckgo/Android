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
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.APP_LAUNCH
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CANCEL
import com.duckduckgo.app.notification.NotificationHandlerService.NotificationEvent.CLEAR_DATA_LAUNCH
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class NotificationHandlerService : IntentService("NotificationHandlerService") {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var context: Context

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    @VisibleForTesting
    public override fun onHandleIntent(workIntent: Intent) {
        when (workIntent.type) {
            APP_LAUNCH -> onAppLaunched()
            CLEAR_DATA_LAUNCH -> onClearDataLaunched()
            CANCEL -> onCancelled()
        }
    }

    private fun onAppLaunched() {
        val intent = BrowserActivity.intent(context, newSearch = true)
        startActivity(intent)
        pixel.fire(Pixel.PixelName.NOTIFICATION_LAUNCHED)
    }

    private fun onClearDataLaunched() {
        Timber.i("Clear Data Launched!")
        val settingsIntent = SettingsActivity.intent(context)
        TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(settingsIntent)
            .startActivities()
        pixel.fire(Pixel.PixelName.NOTIFICATION_LAUNCHED)
    }

    private fun onCancelled() {
        pixel.fire(Pixel.PixelName.NOTIFICATION_CANCELLED)
    }

    object NotificationEvent {
        const val APP_LAUNCH = "com.duckduckgo.notification.launch.app"
        const val CLEAR_DATA_LAUNCH = "com.duckduckgo.notification.launch.clearData"
        const val CANCEL = "com.duckduckgo.notification.cancel"
    }
}