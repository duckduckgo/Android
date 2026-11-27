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

package com.duckduckgo.pagetranslation.impl.menu

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.view.isGone
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.common.ui.view.MenuItemViewSize
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.pagetranslation.impl.R
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as DesignSystemR

@InjectWith(ViewScope::class)
class TranslationMenuRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var visibleTab: VisibleTabHolder

    @Inject
    lateinit var deviceLanguages: DeviceLanguages

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val menuItem: MenuItemView by lazy {
        MenuItemView(context).apply {
            setSize(MenuItemViewSize.MEDIUM)
            // Placeholder until the Translate-24 icon can be imported from the internal Icons repository.
            setIcon(DesignSystemR.drawable.ic_globe_24)
        }
    }

    private var onHostClick: (() -> Unit)? = null
    private var scope: CoroutineScope? = null

    init {
        orientation = VERTICAL
        isGone = true
        addView(menuItem)
    }

    fun bind(onHostClick: () -> Unit) {
        this.onHostClick = onHostClick
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        menuItem.setOnClickListener {
            visibleTab.onRowTapped()
            onHostClick?.invoke()
        }

        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main()).also { scope ->
            visibleTab.viewState.onEach { render(it) }.launchIn(scope)
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun render(viewState: TranslationMenuViewState) {
        isGone = viewState == TranslationMenuViewState.Hidden
        when (viewState) {
            TranslationMenuViewState.Hidden -> Unit
            is TranslationMenuViewState.Translate -> {
                val language = deviceLanguages.displayName(viewState.target)
                show(context.getString(R.string.pageTranslationMenuTranslatePage, language), viewState.enabled)
            }
            TranslationMenuViewState.InProgress -> show(context.getString(R.string.pageTranslationMenuTranslating), enabled = false)
            TranslationMenuViewState.ShowOriginal -> show(
                context.getString(R.string.pageTranslationMenuShowOriginalPage),
                enabled = true,
                showDot = true,
            )
        }
    }

    private fun show(label: String, enabled: Boolean, showDot: Boolean = false) {
        menuItem.label(label)
        menuItem.isEnabled = enabled
        menuItem.showDotIndicator = showDot
    }
}
