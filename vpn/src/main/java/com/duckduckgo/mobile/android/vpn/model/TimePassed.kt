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

package com.duckduckgo.mobile.android.vpn.model

import timber.log.Timber

data class TimePassed(val hours: Long, val minutes: Long, val seconds: Long) {

        override fun toString(): String {
            Timber.i("Time passed $hours hr, $minutes min $seconds sec")

            if (hours > 0) {
                return if (minutes > 0) {
                    "$hours hr $minutes min"
                } else {
                    "$hours hr"
                }
            }

            if (minutes > 0) {
                return "$minutes min"
            }

            if (seconds > 0){
                return "$seconds sec"
            }

            return "1 sec"
        }

        companion object {

            fun between(currentMillis: Long, oldMillis: Long): TimePassed {
                return fromMilliseconds(currentMillis - oldMillis)
            }

            fun fromMilliseconds(millis: Long): TimePassed {
                val seconds = (millis / 1000)  % 60
                val minutes = (millis / (1000 * 60) % 60)
                val hours = (millis / (1000) / 3600)

                return TimePassed(hours, minutes, seconds)
            }
        }
    } 