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

package com.duckduckgo.app.statistics.config

import com.duckduckgo.app.statistics.BuildConfig

/**
 * If you want to configure the statistics library, have your app extend this listener
 * and implement the different methods.
 * The library will check through the application context.
 */
interface StatisticsLibraryConfig {
    fun shouldFirePixelsAsDev(): Boolean
}

class DefaultStatisticsLibraryConfig : StatisticsLibraryConfig {
    override fun shouldFirePixelsAsDev(): Boolean {
        return BuildConfig.DEBUG
    }
}