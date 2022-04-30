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

package com.duckduckgo.voice.impl.listeningmode.ui

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VoiceSearchBackgroundBlurRenderer {
    fun addBlur(view: View)
    fun removeBlur(view: View)
}

@ContributesBinding(ActivityScope::class)
class RealVoiceSearchBackgroundBlurRenderer @Inject constructor(
    private val appBuildConfig: AppBuildConfig
) : VoiceSearchBackgroundBlurRenderer {

    @SuppressLint("NewApi")
    override fun addBlur(view: View) {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.S) {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(70f, 70f, Shader.TileMode.MIRROR)
            )
        }
    }

    @SuppressLint("NewApi")
    override fun removeBlur(view: View) {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        }
    }
}
