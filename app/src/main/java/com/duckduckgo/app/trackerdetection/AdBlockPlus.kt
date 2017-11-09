/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection;


class AdBlockPlus {

    companion object FilterOption {
        val None = 0
        val Script = 1
        val Image = 2
        val Stylesheet = 4
    }

    init {
        System.loadLibrary("adblockplus-lib")
    }

    external fun loadData(easylistData: ByteArray, easyprivacyData: ByteArray): Boolean


    fun matches(url: String, documentUrl: String): Boolean {

        var filterOption = FilterOption.None
        if (url.endsWith(".js", true)) {
            filterOption = FilterOption.Script
        } else if (url.endsWith(".css", true)) {
            filterOption = FilterOption.Stylesheet
        } else if (url.endsWith(".png", true) || url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) || url.endsWith(".gif", true)) {
            filterOption = FilterOption.Image
        }

        return matches(url, documentUrl, filterOption)
    }

    external fun matches(url: String, documentUrl: String, filterOption: Int): Boolean
}
