/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.firebutton

import android.app.Activity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.FireAnimationActivity
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.availableFireAnimations
import com.duckduckgo.app.settings.clear.displayLabelResId
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder

internal fun Activity.launchBrandDesignFireAnimationSelector(
    animation: FireAnimation,
    onAnimationSelected: (FireAnimation) -> Unit,
) {
    val animations = availableFireAnimations(includesInferno = true)
    val currentIndex = animations.indexOf(animation).coerceAtLeast(0) + 1
    val labels = animations.map { it.displayLabelResId(includesInferno = true) }

    RadioListAlertDialogBuilder(this)
        .setTitle(R.string.settingsSelectFireAnimationDialog)
        .setOptions(labels, currentIndex)
        .setPositiveButton(R.string.settingsSelectFireAnimationDialogSave)
        .setNegativeButton(R.string.cancel)
        .addEventListener(
            object : RadioListAlertDialogBuilder.EventListener() {
                override fun onPositiveButtonClicked(selectedItem: Int) {
                    onAnimationSelected(animations[selectedItem - 1])
                }

                override fun onRadioItemSelected(selectedItem: Int) {
                    val selectedAnimation = animations[selectedItem - 1]
                    if (selectedAnimation != FireAnimation.None) {
                        startActivity(FireAnimationActivity.intent(baseContext, selectedAnimation))
                    }
                }
            },
        )
        .show()
}
