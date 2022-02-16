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

package com.duckduckgo.macos_impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class MacOsPixelNames(override val pixelName: String) : Pixel.PixelName {
    MACOS_WAITLIST_NOTIFICATION_LAUNCHED("m_notification_launch_mac_waitlist"),
    MACOS_WAITLIST_NOTIFICATION_CANCELLED("m_notification_cancel_mac_waitlist"),
    MACOS_WAITLIST_NOTIFICATION_SHOWN("m_notification_shown_mac_waitlist"),
    MACOS_WAITLIST_DIALOG_SHOWN("m_macos_waitlist_did_show_waitlist_dialog"),
    MACOS_WAITLIST_DIALOG_DISMISS("m_macos_waitlist_did_press_waitlist_dialog_dismiss"),
    MACOS_WAITLIST_DIALOG_NOTIFY_ME("m_macos_waitlist_did_press_waitlist_dialog_notify_me"),
    MACOS_WAITLIST_SHARE_PRESSED("m_macos_waitlist_did_press_share_button"),
    MACOS_WAITLIST_SHARE_SHARED("m_macos_waitlist_did_press_share_button_shared"),
}
