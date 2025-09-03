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

package com.duckduckgo.app.notification.model

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.firebutton.FireButtonActivity
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import logcat.LogPriority.VERBOSE
import logcat.logcat

class ClearDataNotification(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val settingsDataStore: SettingsDataStore,
) : SchedulableNotification {

    override val id = "com.duckduckgo.privacytips.autoclear"

    override suspend fun canShow(): Boolean {
        if (notificationDao.exists(id)) {
            logcat(VERBOSE) { "Notification already seen" }
            return false
        }

        if (settingsDataStore.automaticallyClearWhatOption != ClearWhatOption.CLEAR_NONE) {
            logcat(VERBOSE) { "No need for notification, user already has clear option set" }
            return false
        }

        return true
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return ClearDataSpecification(context)
    }
}

class ClearDataSpecification(context: Context) : NotificationSpec {
    override val channel = NotificationRegistrar.ChannelType.TUTORIALS
    override val systemId = NotificationRegistrar.NotificationId.ClearData
    override val name = "Update auto clear data"
    override val icon = R.drawable.notification_fire
    override val title: String = context.getString(R.string.clearNotificationTitle)
    override val description: String = context.getString(R.string.clearNotificationDescription)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = "cd"
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue)
}

@ContributesMultibinding(AppScope::class)
class ClearDataNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: ClearDataNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun onNotificationCancelled() {
        pixel.fire(pixelName(AppPixelName.NOTIFICATION_CANCELLED.pixelName))
    }

    override fun onNotificationShown() {
        pixel.fire(pixelName(AppPixelName.NOTIFICATION_SHOWN.pixelName))
    }

    override fun getSpecification(): NotificationSpec {
        val deferred = coroutineScope.async(dispatcherProvider.io()) {
            schedulableNotification.buildSpecification()
        }
        return runBlocking {
            deferred.await()
        }
    }

    override fun getLaunchIntent(): PendingIntent? {
        val intent = FireButtonActivity.intent(context).apply {
            putExtra(FireButtonActivity.LAUNCH_FROM_NOTIFICATION_PIXEL_NAME, pixelName(AppPixelName.NOTIFICATION_LAUNCHED.pixelName))
        }
        val pendingIntent: PendingIntent? = taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return pendingIntent
    }

    private fun pixelName(notificationType: String) = "${notificationType}_${getSpecification().pixelSuffix}"
}
