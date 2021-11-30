/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.mobile.android.vpn.R

enum class TrackingSignal(val signaltag: String, @StringRes val signalDisplayName: Int, @DrawableRes val signalIcon: Int) {
    AAID("AAID", R.string.atp_TrackingSignalAAID, R.drawable.ic_signal_id),
    DEVICE_ID("device_id", R.string.atp_TrackingSignalUniqueIdentifier, R.drawable.ic_signal_id),
    FB_PERSISTENT_ID("fb_persistent_id", R.string.atp_TrackingSignalUniqueIdentifier, R.drawable.ic_signal_id);

    companion object {
        fun fromTag(signalTag: String): TrackingSignal {
            return valueOf(signalTag.uppercase())
        }

    }
}
