/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.animations

import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@ContributesBinding(FragmentScope::class)
class ExperimentTrackersCountAnimationHelperImpl @Inject constructor() : ExperimentTrackersCountAnimationHelper {

    private val conflatedJob = ConflatedJob()

    override var isAnimating: Boolean = false

    override fun animate(trackersCountView: TextView, siteLiveData: MutableLiveData<Site>) {
        val site = siteLiveData.value ?: return
        val count = site.trackerCount
        val previousCount = site.previousNumberOfBlockedTrackers ?: 0
        if (!isAnimating && (trackersCountView.text.isNullOrEmpty() || count.toString() != trackersCountView.text || count != previousCount)) {
            isAnimating = true
            updateTrackersCountWithAnimation(count, previousCount, trackersCountView, site)
        }
    }

    private fun updateTrackersCountWithAnimation(
        count: Int,
        previousCount: Int,
        trackersCountView: TextView,
        site: Site,
    ) {
        fun updateTrackersCountText(index: Int) {
            conflatedJob += MainScope().launch {
                if (index <= count) {
                    trackersCountView.text = if (index == 0) "" else index.toString()
                    delay(150L)
                    updateTrackersCountText(index + 1)
                } else {
                    isAnimating = false
                    site.previousNumberOfBlockedTrackers = count
                }
            }
        }

        updateTrackersCountText(previousCount)
    }

    override fun cancelAnimations() {
        isAnimating = false
        conflatedJob.cancel()
    }
}
