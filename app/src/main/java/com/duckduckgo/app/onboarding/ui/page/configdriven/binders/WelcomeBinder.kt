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
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignDialogWelcomeBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows

class WelcomeBinder(
    private val binding: IncludeBrandDesignDialogWelcomeBinding,
) : DialogBinder<ContentConfig.Welcome> {

    override val view: View = binding.root

    override fun bind(content: ContentConfig.Welcome, scope: BindScope): ContentHandle = with(binding) {
        val context = root.context

        val body1 = content.body1.resolve(context).preventWidows()
        bodyText1.text = if (content.body1AsHtml) body1.html(context) else body1
        // Set explicitly: a previous render of the single-line copy leaves this hidden.
        bodyText2.isVisible = content.body2 != null
        content.body2?.let { bodyText2.text = it.resolve(context).preventWidows() }

        titleText.setTitle(content.title.resolve(context))

        ContentHandle(
            title = titleText,
            fadeTargets = listOfNotNull(bodyText1, bodyText2.takeIf { content.body2 != null }),
        )
    }
}
