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

package com.duckduckgo.app.global.device

import android.content.Context
import android.util.TypedValue
import javax.inject.Inject


interface DeviceInfo {

    enum class FormFactor(val description: String) {

        PHONE("phone"),
        TABLET("tablet")

    }

    fun formFactor(): FormFactor

}

class ContextDeviceInfo @Inject constructor(private val context: Context) : DeviceInfo {

    override fun formFactor(): DeviceInfo.FormFactor {
        val metrics = context.resources.displayMetrics
        val smallestSize = Math.min(metrics.widthPixels, metrics.heightPixels)
        val tabletSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 600f, context.resources.displayMetrics).toInt()
        return if (smallestSize >= tabletSize) DeviceInfo.FormFactor.TABLET else DeviceInfo.FormFactor.PHONE
    }

}