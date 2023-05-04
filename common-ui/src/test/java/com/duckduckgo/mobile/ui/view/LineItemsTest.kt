/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.ui.view

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.duckduckgo.mobile.android.databinding.ViewTwoLineItemBinding
import org.junit.Rule
import org.junit.Test

class LineItemsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "Theme.DuckDuckGo.Dark",
    )

    @Test
    fun testViews() {
        val binding = ViewTwoLineItemBinding.inflate(paparazzi.layoutInflater)
        with(binding) {
            binding.primaryText.text = "Two Line Item"
            paparazzi.snapshot(root, "two_line")
        }
    }
}
