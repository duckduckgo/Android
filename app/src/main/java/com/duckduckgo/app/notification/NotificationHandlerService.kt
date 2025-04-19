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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ServiceScope
import dagger.android.AndroidInjection
import javax.inject.Inject

@InjectWith(ServiceScope::class)
class NotificationHandlerService : IntentService("NotificationHandlerService") {

    @Inject
    lateinit var schedulableNotificationPluginPoint: PluginPoint<SchedulableNotificationPlugin>

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    @VisibleForTesting
    public override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val notificationJavaClass = intent.type ?: return
        val notificationPlugin = schedulableNotificationPluginPoint.getPlugins().firstOrNull {
            notificationJavaClass == it.getSchedulableNotification().javaClass.simpleName
        }
        notificationPlugin?.onNotificationCancelled()
    }

    companion object {
        fun pendingCancelNotificationHandlerIntent(
            context: Context,
            notificationJavaClass: Class<SchedulableNotification>,
        ): PendingIntent {
            val intent = Intent(context, NotificationHandlerService::class.java)
            intent.type = notificationJavaClass.simpleName
            return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)!!
        }
    }
}
