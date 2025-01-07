/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class AutoConsentPixel(override val pixelName: String) : Pixel.PixelName {

    SETTINGS_AUTOCONSENT_SHOWN("m_settings_autoconsent_shown"),
    SETTINGS_AUTOCONSENT_ON("m_settings_autoconsent_on"),
    SETTINGS_AUTOCONSENT_OFF("m_settings_autoconsent_off"),
}
