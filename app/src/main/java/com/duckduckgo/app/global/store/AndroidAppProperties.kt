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

package com.duckduckgo.app.global.store

import android.content.Context
import android.os.Build
import androidx.webkit.WebViewCompat
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.browser.api.DeviceProperties
import java.util.*

class AndroidAppProperties(
) : AppProperties {

    override fun flavor(): String {
        TODO("Not yet implemented")
    }

    override fun appId(): Int {
        TODO("Not yet implemented")
    }

    override fun appVersion(): String {
        TODO("Not yet implemented")
    }

    override fun atb(): String {
        TODO("Not yet implemented")
    }

    override fun appAtb(): String {
        TODO("Not yet implemented")
    }

    override fun searchAtb(): String {
        TODO("Not yet implemented")
    }

    override fun expVariant(): String {
        TODO("Not yet implemented")
    }

    override fun installedGPlay(): String {
        TODO("Not yet implemented")
    }
}