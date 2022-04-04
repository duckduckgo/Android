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

package com.duckduckgo.mobile.android.voice.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.voice.api.VoiceSearchLauncher.Event
import com.duckduckgo.mobile.android.voice.api.VoiceSearchLauncher.Source
import com.duckduckgo.mobile.android.voice.impl.listeningmode.VoiceSearchActivity
import com.duckduckgo.mobile.android.voice.impl.listeningmode.ui.VoiceSearchBackgroundBlurRenderer
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
    private val context: Context,
    private val pixel: Pixel
) : VoiceSearchActivityLauncher {

    companion object {
        private const val KEY_PARAM_SOURCE = "source"
    }

    private lateinit var voiceSearchActivityLaucher: ActivityResultLauncher<Intent>
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
        voiceSearchActivityLaucher = caller.registerForActivityResult(StartActivityForResult()) { result ->
            result?.let {
                if (it.resultCode == Activity.RESULT_OK) {
                    val data = it.data?.getStringExtra(VoiceSearchActivity.EXTRA_VOICE_RESULT) ?: ""
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
            }
            _activity?.window?.decorView?.rootView?.let {
                blurRenderer.removeBlur(it)
            }
        }
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
        voiceSearchActivityLaucher.launch(Intent(context, VoiceSearchActivity::class.java))
    }
}
