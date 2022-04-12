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

package com.duckduckgo.voice.impl

import android.app.Activity
import androidx.activity.result.ActivityResultCaller
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action.LaunchVoiceSearch
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Request
import com.duckduckgo.voice.impl.listeningmode.ui.VoiceSearchBackgroundBlurRenderer
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchActivityLauncher {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        source: Source,
        onEvent: (Event) -> Unit
    )

    fun launch()
}

@ContributesBinding(ActivityScope::class)
class RealVoiceSearchActivityLauncher @Inject constructor(
    private val blurRenderer: VoiceSearchBackgroundBlurRenderer,
    private val pixel: Pixel,
    private val activityResultLauncherWrapper: ActivityResultLauncherWrapper
) : VoiceSearchActivityLauncher {

    companion object {
        private const val KEY_PARAM_SOURCE = "source"
    }

    private lateinit var _source: Source
    private var _activity: Activity? = null

    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        source: Source,
        onEvent: (Event) -> Unit
    ) {
        _activity = activity
        _source = source
        activityResultLauncherWrapper.register(
            caller,
            Request.ResultFromVoiceSearch { code, data ->
                if (code == Activity.RESULT_OK) {
                    if (data.isNotEmpty()) {
                        pixel.fire(
                            pixel = VoiceSearchPixelNames.VOICE_SEARCH_DONE,
                            parameters = mapOf(KEY_PARAM_SOURCE to _source.paramValueName)
                        )
                        onEvent(Event.VoiceRecognitionSuccess(data))
                    } else {
                        onEvent(Event.SearchCancelled)
                    }
                } else {
                    onEvent(Event.SearchCancelled)
                }

                _activity?.window?.decorView?.rootView?.let {
                    blurRenderer.removeBlur(it)
                }
            }
        )
    }

    override fun launch() {
        launchVoiceSearch()
    }

    private fun launchVoiceSearch() {
        _activity?.window?.decorView?.rootView?.let {
            blurRenderer.addBlur(it)
        }
        pixel.fire(
            pixel = VoiceSearchPixelNames.VOICE_SEARCH_STARTED,
            parameters = mapOf(KEY_PARAM_SOURCE to _source.paramValueName)
        )
        activityResultLauncherWrapper.launch(LaunchVoiceSearch)
    }
}
