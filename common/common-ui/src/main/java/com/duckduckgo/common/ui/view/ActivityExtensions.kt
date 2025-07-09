/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentActivity

fun Context.fadeTransitionConfig(): Bundle? {
    val config = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
    return config.toBundle()
}

fun Context.noAnimationConfig(): Bundle? =
    ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()

fun FragmentActivity.isFullScreen(): Boolean {
    return window.decorView.systemUiVisibility.and(View.SYSTEM_UI_FLAG_FULLSCREEN) == View.SYSTEM_UI_FLAG_FULLSCREEN
}

fun FragmentActivity.isImmersiveModeEnabled(): Boolean {
    val uiOptions = window.decorView.systemUiVisibility
    return uiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY == uiOptions
}
