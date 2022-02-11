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

package com.duckduckgo.app.notification

object NotificationEvent {
    const val APP_LAUNCH = "com.duckduckgo.notification.launch.app"
    const val CLEAR_DATA_LAUNCH = "com.duckduckgo.notification.launch.clearData"
    const val CANCEL = "com.duckduckgo.notification.cancel"
    const val WEBSITE = "com.duckduckgo.notification.website"
    const val CHANGE_ICON_FEATURE = "com.duckduckgo.notification.app.feature.changeIcon"
    const val EMAIL_WAITLIST_CODE = "com.duckduckgo.notification.email.waitlist.code"
    const val APPTP_WAITLIST_CODE = "com.duckduckgo.notification.apptp.waitlist.code"
}
