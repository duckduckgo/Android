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

package com.duckduckgo.app.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.voice.listeningmode.VoiceSearchActivity
import com.duckduckgo.app.voice.VoiceSearchLauncher.Event
import com.duckduckgo.app.voice.listeningmode.ui.addBlur
import com.duckduckgo.app.voice.listeningmode.ui.removeBlur
import javax.inject.Inject

@RequiresApi(VERSION_CODES.S)
class VoiceSearchActivityLauncher @Inject constructor(
    private val context: Context,
    private val pixel: Pixel
) : VoiceSearchLauncher {

    private lateinit var voiceSearchActivityLaucher: ActivityResultLauncher<Intent>
    private var _activity: Activity? = null

    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        onEvent: (Event) -> Unit
    ) {
        _activity = activity
        voiceSearchActivityLaucher = caller.registerForActivityResult(StartActivityForResult()) { result ->
            result?.let {
                if (it.resultCode == Activity.RESULT_OK) {
                    it.data?.getStringExtra(VoiceSearchActivity.EXTRA_VOICE_RESULT)?.let { data ->
                        if (data.isNotEmpty()) {
                            pixel.fire(AppPixelName.VOICE_SEARCH_DONE)
                            onEvent(Event.VoiceRecognitionSuccess(data))
                        } else {
                            onEvent(Event.SearchCancelled)
                        }
                    }
                } else {
                    onEvent(Event.SearchCancelled)
                }
            }
            _activity?.window?.decorView?.rootView?.removeBlur()
        }
    }

    override fun launch() {
        launchVoiceSearch(context)
    }

    private fun launchVoiceSearch(context: Context) {
        _activity?.window?.decorView?.rootView?.addBlur()
        pixel.fire(AppPixelName.VOICE_SEARCH_STARTED)
        voiceSearchActivityLaucher.launch(Intent(context, VoiceSearchActivity::class.java))
    }
}
