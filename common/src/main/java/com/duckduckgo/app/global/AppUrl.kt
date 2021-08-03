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

class AppUrl {

    object Url {
        const val HOST = "duckduckgo.com"
        const val API = "https://$HOST"
        const val HOME = "https://$HOST"
        const val COOKIES = "https://$HOST"
        const val ABOUT = "https://$HOST/about"
        const val TOSDR = "https://tosdr.org"
        const val PIXEL = "https://improving.duckduckgo.com"
        const val EMAIL_SEGMENT = "email"
    }

    object ParamKey {
        const val QUERY = "q"
        const val SOURCE = "t"
        const val ATB = "atb"
        const val RETENTION_ATB = "set_atb"
        const val DEV_MODE = "test"
        const val LANGUAGE = "lg"
        const val COUNTRY = "co"
        const val HIDE_SERP = "ko"
        const val VERTICAL = "ia"
        const val VERTICAL_REWRITE = "iar"
    }

    object ParamValue {
        const val SOURCE = "ddg_android"
        const val SOURCE_EU_AUCTION = "ddg_androideu"
        const val HIDE_SERP = "-1"
    }

    object StaticUrl {
        const val SETTINGS = "/settings"
        const val PARAMS = "/params"
    }

}
