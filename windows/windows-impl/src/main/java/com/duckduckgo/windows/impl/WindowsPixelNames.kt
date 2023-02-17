/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.windows.impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class WindowsPixelNames(override val pixelName: String) : Pixel.PixelName {
    WINDOWS_WAITLIST_NOTIFICATION_LAUNCHED("m_notification_launch_windows_waitlist"),
    WINDOWS_WAITLIST_NOTIFICATION_CANCELLED("m_notification_cancel_windows_waitlist"),
    WINDOWS_WAITLIST_NOTIFICATION_SHOWN("m_notification_shown_windows_waitlist"),
    WINDOWS_WAITLIST_SHARE_PRESSED("m_windows_waitlist_did_press_share_button"),
    WINDOWS_WAITLIST_SHARE_SHARED("m_windows_waitlist_did_press_share_button_shared"),
}
