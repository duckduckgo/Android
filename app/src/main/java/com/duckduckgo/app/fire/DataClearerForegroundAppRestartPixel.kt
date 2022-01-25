/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.di.scopes.AppScope
import timber.log.Timber
import javax.inject.Inject
import dagger.SingleInstanceIn

/**
 * Stores information about unsent automatic data clearer restart Pixels, detecting if user started the app from an external Intent.
 * Contains logic to send unsent pixels.
 *
 * When writing values here to SharedPreferences, it is crucial to use `commit = true`. As otherwise the change can be lost in the process restart.
 */
@SingleInstanceIn(AppScope::class)
class DataClearerForegroundAppRestartPixel @Inject constructor(
    private val context: Context,
    private val pixel: Pixel
) : LifecycleObserver {
    private var detectedUserIntent: Boolean = false

    private val pendingAppForegroundRestart: Int
        get() = preferences.getInt(KEY_UNSENT_CLEAR_APP_RESTARTED_PIXELS, 0)

    private val pendingAppForegroundRestartWithIntent: Int
        get() = preferences.getInt(KEY_UNSENT_CLEAR_APP_RESTARTED_WITH_INTENT_PIXELS, 0)

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onAppCreated() {
        Timber.i("onAppCreated firePendingPixels")
        firePendingPixels()
    }

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        Timber.i("Registered App on_stop")
        detectedUserIntent = false
    }

    fun registerIntent(intent: Intent?) {
        detectedUserIntent = widgetActivity(intent) || !intent?.intentText.isNullOrEmpty()
    }

    fun incrementCount() {
        if (detectedUserIntent) {
            Timber.i("Registered restart with intent")
            incrementCount(pendingAppForegroundRestart, KEY_UNSENT_CLEAR_APP_RESTARTED_WITH_INTENT_PIXELS)
        } else {
            Timber.i("Registered restart without intent")
            incrementCount(pendingAppForegroundRestartWithIntent, KEY_UNSENT_CLEAR_APP_RESTARTED_PIXELS)
        }
    }

    fun firePendingPixels() {
        firePendingPixels(pendingAppForegroundRestart, AppPixelName.FORGET_ALL_AUTO_RESTART)
        firePendingPixels(pendingAppForegroundRestartWithIntent, AppPixelName.FORGET_ALL_AUTO_RESTART_WITH_INTENT)
        resetCount()
    }

    private fun incrementCount(
        counter: Int,
        sharedPrefKey: String
    ) {
        val updated = counter + 1
        preferences.edit(commit = true) {
            putInt(sharedPrefKey, updated)
        }
    }

    private fun firePendingPixels(
        counter: Int,
        pixelName: Pixel.PixelName
    ) {
        if (counter > 0) {
            for (i in 1..counter) {
                Timber.i("Fired pixel: ${pixelName.pixelName}/$counter")
                pixel.fire(pixelName)
            }
        }
    }

    private fun resetCount() {
        preferences.edit(commit = true) {
            putInt(KEY_UNSENT_CLEAR_APP_RESTARTED_PIXELS, 0)
            putInt(KEY_UNSENT_CLEAR_APP_RESTARTED_WITH_INTENT_PIXELS, 0)
        }
        Timber.i("counter reset")
    }

    private fun widgetActivity(intent: Intent?): Boolean =
        intent?.component?.className?.contains(SystemSearchActivity::class.java.canonicalName.orEmpty()) == true

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    companion object {
        @VisibleForTesting
        const val FILENAME = "com.duckduckgo.app.fire.unsentpixels.settings"
        const val KEY_UNSENT_CLEAR_APP_RESTARTED_PIXELS = "KEY_UNSENT_CLEAR_APP_RESTARTED_PIXELS"
        const val KEY_UNSENT_CLEAR_APP_RESTARTED_WITH_INTENT_PIXELS = "KEY_UNSENT_CLEAR_APP_RESTARTED_WITH_INTENT_PIXELS"
    }
}
