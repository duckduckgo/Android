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

package com.duckduckgo.app.privacy.ui

import androidx.annotation.StringRes
import com.duckduckgo.app.trackerdetection.model.TrackerStatus

data class TrackerNetworksSection(
    @StringRes val descriptionRes: Int? = null,
    @StringRes val linkTextRes: Int? = null,
    @StringRes val linkUrlRes: Int? = null,
    val trackerStatus: TrackerStatus,
    val domain: String? = null,
)
