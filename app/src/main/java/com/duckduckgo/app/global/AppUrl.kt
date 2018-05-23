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

package com.duckduckgo.app.global

import com.duckduckgo.app.browser.BuildConfig


class AppUrl {

    object Url {
        const val HOST = "duckduckgo.com"
        const val API = "https://$HOST"
        const val HOME =  "https://$HOST"
        const val ABOUT = "https://$HOST/about"
        const val FEEDBACK = "https://$HOST/feedback"
        const val TOSDR = "https://tosdr.org"
    }

    object ParamKey {
        const val QUERY = "q"
        const val SOURCE = "t"
        const val APP_VERSION = "tappv"
        const val ATB = "atb"
        const val RETENTION_ATB = "set_atb"
    }

    object ParamValue {
        const val SOURCE = "ddg_android"

        val appVersion: String get() {
            return String.format("android_%s", BuildConfig.VERSION_NAME)
        }
    }
}