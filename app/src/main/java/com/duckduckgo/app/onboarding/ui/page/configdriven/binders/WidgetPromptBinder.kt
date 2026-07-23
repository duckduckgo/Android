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

package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.view.View
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignWidgetPromptBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogTitleController
import com.duckduckgo.common.utils.extensions.preventWidows

/**
 * Stateless. Ported from BrandDesignUpdateWelcomePage:
 *  - animated path :1172-1268 (title/body/media setup, ChangeBounds transition owned by the engine's caller)
 *  - snap path :1886-1963
 */
class WidgetPromptBinder(private val binding: IncludeBrandDesignWidgetPromptBinding) : DialogBinder<ContentConfig.WidgetPrompt> {

    override val view: View = binding.root

    override fun bind(content: ContentConfig.WidgetPrompt, scope: BindScope): ContentHandle {
        val context = binding.root.context

        binding.widgetPromptBody.text = content.body.resolve(context).preventWidows()

        val title = DialogTitleController(binding.widgetPromptTitle, binding.widgetPromptTitleHidden)
        title.set(content.title.resolve(context))

        return ContentHandle(
            title = title,
            fadeTargets = listOf(binding.widgetPromptBody, binding.widgetPromptMedia),
        )
    }
}
