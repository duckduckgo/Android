/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.di

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.duckduckgo.app.fire.DataClearingWorker
import com.duckduckgo.app.global.job.AppConfigurationWorker
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.job.ConfigurationDownloader
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.NotificationScheduler.ClearDataNotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.PrivacyNotificationWorker
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.ClearDataNotification
import com.duckduckgo.app.notification.model.PrivacyProtectionNotification
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.OfflinePixelScheduler
import com.duckduckgo.app.statistics.api.OfflinePixelSender
import com.duckduckgo.app.statistics.pixels.Pixel
import timber.log.Timber

class DaggerWorkerFactory(
    private val offlinePixelSender: OfflinePixelSender,
    private val settingsDataStore: SettingsDataStore,
    private val clearDataAction: ClearDataAction,
    private val notificationManager: NotificationManagerCompat,
    private val notificationDao: NotificationDao,
    private val notificationFactory: NotificationFactory,
    private val clearDataNotification: ClearDataNotification,
    private val privacyProtectionNotification: PrivacyProtectionNotification,
    private val configurationDownloader: ConfigurationDownloader,
    private val pixel: Pixel
) : WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {

        try {
            val workerClass = Class.forName(workerClassName).asSubclass(ListenableWorker::class.java)
            val constructor = workerClass.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            val instance = constructor.newInstance(appContext, workerParameters)

            when (instance) {
                is OfflinePixelScheduler.OfflinePixelWorker -> injectOfflinePixelWorker(instance)
                is DataClearingWorker -> injectDataClearWorker(instance)
                is ClearDataNotificationWorker -> injectClearDataNotificationWorker(instance)
                is PrivacyNotificationWorker -> injectPrivacyNotificationWorker(instance)
                is AppConfigurationWorker -> injectAppConfigurationWorker(instance)
                else -> Timber.i("No injection required for worker $workerClassName")
            }

            return instance
        } catch (exception: Exception) {
            Timber.e(exception, "Worker $workerClassName could not be created")
            return null
        }

    }

    private fun injectAppConfigurationWorker(worker: AppConfigurationWorker) {
        worker.appConfigurationDownloader = configurationDownloader
    }

    private fun injectOfflinePixelWorker(worker: OfflinePixelScheduler.OfflinePixelWorker) {
        worker.offlinePixelSender = offlinePixelSender
    }

    private fun injectDataClearWorker(worker: DataClearingWorker) {
        worker.settingsDataStore = settingsDataStore
        worker.clearDataAction = clearDataAction
    }

    private fun injectClearDataNotificationWorker(worker: ClearDataNotificationWorker) {
        worker.manager = notificationManager
        worker.notificationDao = notificationDao
        worker.factory = notificationFactory
        worker.pixel = pixel
        worker.notification = clearDataNotification
    }

    private fun injectPrivacyNotificationWorker(worker: PrivacyNotificationWorker) {
        worker.manager = notificationManager
        worker.notificationDao = notificationDao
        worker.factory = notificationFactory
        worker.pixel = pixel
        worker.notification = privacyProtectionNotification
    }

}
