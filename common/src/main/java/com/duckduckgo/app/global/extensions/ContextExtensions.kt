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

package com.duckduckgo.app.global.extensions

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build.VERSION_CODES.R
import androidx.annotation.RequiresApi
import java.util.*

@RequiresApi(R)
fun Context.historicalExitReasonsByProcessName(name: String, n: Int = 10): List<String> {
    val activityManager = applicationContext.getSystemService(ACTIVITY_SERVICE) as ActivityManager

    return activityManager
        .getHistoricalProcessExitReasons(null, 0, 0)
        .filter { it.processName == name }
        .take(n)
        .map { "[${Date(it.timestamp)} - Reason: ${it.reason}: ${it.description}" }
}
