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

package com.duckduckgo.duckchat.impl.ui

import android.os.Bundle
import android.transition.Transition
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSearchTabBinding
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import javax.inject.Inject
import kotlinx.coroutines.launch

@InjectWith(FragmentScope::class)
class SearchTabFragment : DuckDuckGoFragment(R.layout.fragment_search_tab) {

    @Inject
    lateinit var newTabPagePlugins: ActivePluginPoint<NewTabPagePlugin>

    private val binding: FragmentSearchTabBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.sharedElementEnterTransition?.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    setupNewTabPage()
                    transition.removeListener(this)
                }
                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )
    }

    private fun setupNewTabPage() {
        lifecycleScope.launch {
            newTabPagePlugins.getPlugins().firstOrNull()?.let { plugin ->
                val newTabView = plugin.getView(requireContext())
                newTabView.alpha = 0f

                val displayMetrics = requireContext().resources.displayMetrics
                val slideDistance = displayMetrics.heightPixels * CONTENT_SLIDE_DISTANCE
                newTabView.translationY = -slideDistance

                binding.contentContainer.addView(
                    newTabView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                newTabView.animate()
                    .alpha(1f)
                    .setDuration(CONTENT_ANIMATION_DURATION)
                    .start()

                newTabView.animate()
                    .translationY(0f)
                    .setInterpolator(OvershootInterpolator(CONTENT_INTERPOLATOR_TENSION))
                    .setDuration(CONTENT_ANIMATION_DURATION)
                    .start()
            }
        }
    }

    companion object {
        const val CONTENT_ANIMATION_DURATION = 500L
        const val CONTENT_INTERPOLATOR_TENSION = 1F
        const val CONTENT_SLIDE_DISTANCE = 0.05F
    }
}
