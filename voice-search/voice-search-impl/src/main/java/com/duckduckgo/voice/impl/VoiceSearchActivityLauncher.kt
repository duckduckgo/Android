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
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.view.WindowInsets
import androidx.activity.result.ActivityResultCaller
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.VoiceSearchDisabled
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action.LaunchVoiceSearch
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Request
import com.duckduckgo.voice.impl.R.string
import com.duckduckgo.voice.impl.listeningmode.VoiceSearchActivity.Companion.VOICE_SEARCH_ERROR
import com.duckduckgo.voice.impl.listeningmode.ui.VoiceSearchBackgroundBlurRenderer
import com.duckduckgo.voice.store.VoiceSearchRepository
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchActivityLauncher {
    fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        source: Source,
        onEvent: (Event) -> Unit,
    )

    fun launch(activity: Activity)
}

@ContributesBinding(ActivityScope::class)
class RealVoiceSearchActivityLauncher @Inject constructor(
    private val blurRenderer: VoiceSearchBackgroundBlurRenderer,
    private val pixel: Pixel,
    private val activityResultLauncherWrapper: ActivityResultLauncherWrapper,
    private val voiceSearchRepository: VoiceSearchRepository,
    private val permissionRequest: VoiceSearchPermissionDialogsLauncher,
) : VoiceSearchActivityLauncher {

    companion object {
        private const val KEY_PARAM_SOURCE = "source"
        private const val KEY_PARAM_ERROR = "error"
        private const val SUGGEST_REMOVE_VOICE_SEARCH_AFTER_TIMES = 3
    }

    private var _source: Source? = null

    override fun registerResultsCallback(
        caller: ActivityResultCaller,
        activity: Activity,
        source: Source,
        onEvent: (Event) -> Unit,
    ) {
        _source = source
        activityResultLauncherWrapper.register(
            caller,
            Request.ResultFromVoiceSearch { code, data ->
                if (code == Activity.RESULT_OK) {
                    if (data.isNotEmpty()) {
                        if (duckAiFeatureState.showVoiceSearchToggle.value) {
                            val pixelName = when (mode) {
                                VoiceSearchMode.SEARCH -> VoiceSearchPixelNames.VOICE_SEARCH_SERP_DONE
                                VoiceSearchMode.DUCK_AI -> VoiceSearchPixelNames.VOICE_SEARCH_AICHAT_DONE
                            }
                            pixel.fire(
                                pixel = pixelName,
                                parameters = mapOf(KEY_PARAM_SOURCE to _source?.paramValueName.orEmpty()),
                            )
                        }
                        pixel.fire(
                            pixel = VoiceSearchPixelNames.VOICE_SEARCH_DONE,
                            parameters = mapOf(KEY_PARAM_SOURCE to _source?.paramValueName.orEmpty()),
                        )
                        voiceSearchRepository.resetVoiceSearchDismissed()
                        onEvent(Event.VoiceRecognitionSuccess(data))
                    } else {
                        pixel.fire(
                            pixel = VoiceSearchPixelNames.VOICE_SEARCH_CANCELLED,
                            parameters = mapOf(KEY_PARAM_SOURCE to _source?.paramValueName.orEmpty()),
                        )
                        onEvent(Event.SearchCancelled)
                    }
                } else {
                    if (code == VOICE_SEARCH_ERROR) {
                        pixel.fire(
                            pixel = VoiceSearchPixelNames.VOICE_SEARCH_ERROR,
                            parameters = mapOf(KEY_PARAM_ERROR to data),
                        )
                        activity.window?.decorView?.rootView?.let {
                            val snackbar = Snackbar.make(it, activity.getString(string.voiceSearchError), Snackbar.LENGTH_LONG)
                            snackbar.view.translationY =
                                if (VERSION.SDK_INT >= VERSION_CODES.R) {
                                    (-activity.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.systemBars()).bottom).toFloat()
                                } else {
                                    (-activity.window.decorView.rootWindowInsets.systemWindowInsetBottom).toFloat()
                                }
                            snackbar.show()
                        }
                    } else {
                        pixel.fire(
                            pixel = VoiceSearchPixelNames.VOICE_SEARCH_CANCELLED,
                            parameters = mapOf(KEY_PARAM_SOURCE to _source?.paramValueName.orEmpty()),
                        )
                        onEvent(Event.SearchCancelled)
                    }
                    voiceSearchRepository.dismissVoiceSearch()
                    if (voiceSearchRepository.countVoiceSearchDismissed() >= SUGGEST_REMOVE_VOICE_SEARCH_AFTER_TIMES) {
                        permissionRequest.showRemoveVoiceSearchDialog(
                            activity,
                            onRemoveVoiceSearch = {
                                voiceSearchRepository.setVoiceSearchUserEnabled(false)
                                onEvent(VoiceSearchDisabled)
                            },
                        )
                    }
                }
                activity.window?.decorView?.rootView?.let {
                    blurRenderer.removeBlur(it)
                }
            },
        )
    }

    override fun launch(activity: Activity) {
        launchVoiceSearch(activity)
    }

    private fun launchVoiceSearch(activity: Activity) {
        activity.window?.decorView?.rootView?.let {
            blurRenderer.addBlur(it)
        }
        pixel.fire(
            pixel = VoiceSearchPixelNames.VOICE_SEARCH_STARTED,
            parameters = mapOf(KEY_PARAM_SOURCE to _source?.paramValueName.orEmpty()),
        )
        activityResultLauncherWrapper.launch(LaunchVoiceSearch)
    }
}
