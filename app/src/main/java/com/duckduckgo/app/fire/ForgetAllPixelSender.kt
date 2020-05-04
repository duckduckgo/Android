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

package com.duckduckgo.app.fire

import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.CLEAR_PERSONAL_DATA_FIREPROOF_WEBSITES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ForgetAllPixelSender(
    private val pixel: Pixel,
    private val fireproofWebsiteDao: FireproofWebsiteDao
) {
    suspend fun forgetAllPressed(origin: Pixel.PixelName) {
        withContext(Dispatchers.IO) {
            pixel.fire(
                pixel = origin,
                parameters = mapOf(
                    CLEAR_PERSONAL_DATA_FIREPROOF_WEBSITES to fireproofWebsiteDao.hasFireproofWebsites().toString()
                )
            )
        }
    }
}