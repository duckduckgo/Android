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
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignDialogWelcomeBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogTitleController
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows

/**
 * Stateless. Covers the INITIAL / INITIAL_REINSTALL_USER / SYNC_RESTORE legacy dialog types — they all
 * resolve to this same [ContentConfig.Welcome] shape, only the copy differs (see DialogConfigResolver).
 *
 * Ported from BrandDesignUpdateWelcomePage:
 *  - animated path :888-971 (configureDaxCta, INITIAL/INITIAL_REINSTALL_USER/SYNC_RESTORE branch)
 *  - snap path :1709-1769 (showDialogWithoutAnimation, same branch)
 */
class WelcomeBinder(private val binding: IncludeBrandDesignDialogWelcomeBinding) : DialogBinder<ContentConfig.Welcome> {

    override val view: View = binding.root

    override fun bind(content: ContentConfig.Welcome, scope: BindScope): ContentHandle {
        val context = binding.root.context

        // Legacy applies preventWidows() to both body lines (:917-920, :1752-1756) and additionally routes
        // body1 through .html() for the sync-restore/custom-AI variants (:909-910, :914-915, :1744-1745,
        // :1749-1750) — body2 is never wrapped in .html() in any branch, since it's always null there.
        // ContentConfig.Welcome doesn't distinguish those variants from plain copy, so body1's .html() is
        // applied unconditionally here — it's identity on the current plain-text body1 string resource.
        binding.bodyText1.text = content.body1.resolve(context).preventWidows().html(context)
        binding.bodyText2.isVisible = content.body2 != null
        content.body2?.let { binding.bodyText2.text = it.resolve(context).preventWidows() }

        val title = DialogTitleController(binding.titleText, binding.hiddenTitleText)
        title.set(content.title.resolve(context))

        return ContentHandle(
            title = title,
            fadeTargets = listOfNotNull(binding.bodyText1, binding.bodyText2.takeIf { content.body2 != null }),
        )
    }
}
