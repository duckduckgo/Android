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

import android.view.LayoutInflater
import android.view.ViewGroup
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignOptionButtonPrimaryBinding
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignOptionButtonSecondaryBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option

/**
 * Replaces this container's children with a button per option — the first styled as the primary
 * action, the rest secondary — each reporting its own pick. The buttons are returned so the binder
 * can release their listeners when it unbinds.
 */
internal fun ViewGroup.addOptionButtons(
    options: List<Option>,
    scope: BindScope,
): List<DaxButton> {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    return options.mapIndexed { index, option ->
        val button = if (index == 0) {
            IncludeBrandDesignOptionButtonPrimaryBinding.inflate(inflater, this, false).root
        } else {
            IncludeBrandDesignOptionButtonSecondaryBinding.inflate(inflater, this, false).root
        }
        button.text = option.label
        button.setOnClickListener { scope.execute(ContentInteraction.SelectSingleChoiceOption(option)) }
        addView(button)
        button
    }
}
