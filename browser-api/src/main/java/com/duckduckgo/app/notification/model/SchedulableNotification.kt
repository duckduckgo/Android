/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.os.Bundle
import androidx.annotation.StringRes

/**
 * This interface is used whenever we want to create a notification that can be scheduled
 * if cancelIntent is null it then uses the default if "com.duckduckgo.notification.cancel"
 * which will cancel the notification and send a pixel
 */
interface SchedulableNotification {
    val id: String
    val launchIntent: String
    val cancelIntent: String
    suspend fun canShow(): Boolean
    suspend fun buildSpecification(): NotificationSpec
}

interface NotificationSpec {
    val channel: Channel
    val systemId: Int
    val name: String
    val icon: Int
    val title: String
    val description: String
    val launchButton: String?
    val closeButton: String?
    val pixelSuffix: String
    val autoCancel: Boolean
    val bundle: Bundle
    val color: Int
}

data class Channel(
    val id: String,
    @StringRes val name: Int,
    val priority: Int
)
