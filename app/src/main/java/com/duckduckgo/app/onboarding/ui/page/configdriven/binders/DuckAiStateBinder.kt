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

package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.view.View
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignDuckAiStateBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.common.utils.extensions.preventWidows

class DuckAiStateBinder(
    private val binding: IncludeBrandDesignDuckAiStateBinding,
) : DialogBinder<ContentConfig.DuckAiState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.DuckAiState,
        scope: BindScope,
    ): ContentHandle {
        val context = binding.root.context

        binding.duckAiStateTitle.setTitle(content.title.resolve(context))
        binding.duckAiStateBody.text = content.body.resolve(context).preventWidows()

        val buttons = binding.duckAiStateOptions.addOptionButtons(content.options, scope)

        return ContentHandle(
            title = binding.duckAiStateTitle,
            preTitleFadeTargets = listOf(binding.duckAiStatePictogram),
            fadeTargets = listOf(binding.duckAiStateContentContainer),
            unbind = {
                buttons.forEach { it.setOnClickListener(null) }
                binding.duckAiStateOptions.removeAllViews()
            },
        )
    }
}
