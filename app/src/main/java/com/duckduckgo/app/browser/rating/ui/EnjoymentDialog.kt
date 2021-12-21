/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.rating.ui

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.statistics.pixels.Pixel
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

abstract class EnjoymentDialog : DialogFragment() {

    @Inject lateinit var pixel: Pixel

    val promptCount: PromptCount
        get() = PromptCount(requireArguments()[PROMPT_COUNT_BUNDLE_KEY] as Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    fun firePixelWithPromptCount(name: Pixel.PixelName) {
        val formattedPixelName = String.format(name.pixelName, promptCount.value)
        pixel.fire(formattedPixelName)
    }

    companion object {
        const val PROMPT_COUNT_BUNDLE_KEY = "PROMPT_COUNT"
    }
}
