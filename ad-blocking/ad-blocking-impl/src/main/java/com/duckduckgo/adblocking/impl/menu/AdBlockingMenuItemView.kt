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

package com.duckduckgo.adblocking.impl.menu

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.view.isGone
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.domain.AdBlockingMenuState
import com.duckduckgo.adblocking.impl.domain.AdBlockingMenuStateProvider
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.common.ui.view.MenuItemViewSize
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ViewScope
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class AdBlockingMenuItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var menuStateProvider: AdBlockingMenuStateProvider

    @Inject
    lateinit var menuController: AdBlockingMenuController

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    private val menuItem: MenuItemView by lazy {
        MenuItemView(context).apply {
            setSize(MenuItemViewSize.MEDIUM)
            setIcon(R.drawable.video_ad_blocked_24)
        }
    }

    private var url: Uri? = null
    private var onHostClick: (() -> Unit)? = null
    private var scope: CoroutineScope? = null
    private var menuState: AdBlockingMenuState = AdBlockingMenuState.Hidden

    init {
        orientation = VERTICAL
        isGone = true
        addView(menuItem)
    }

    fun bind(url: Uri, onHostClick: () -> Unit) {
        this.url = url
        this.onHostClick = onHostClick
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val url = this.url ?: return
        menuItem.setOnClickListener {
            when (menuState) {
                AdBlockingMenuState.Disabled -> menuController.enable()
                else -> showMenuBottomSheet()
            }
            onHostClick?.invoke()
        }

        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main()).also { scope ->
            menuStateProvider.observe(url)
                .flowOn(dispatcherProvider.io())
                .onEach { render(it) }
                .launchIn(scope)
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun showMenuBottomSheet() {
        AdBlockingMenuBottomSheetDialog(context, menuController.currentChoice(), edgeToEdgeProvider).apply {
            eventListener = object : AdBlockingMenuBottomSheetDialog.EventListener {
                override fun onChoiceSelected(choice: AdBlockingChoice) {
                    menuController.onChoiceSelected(choice)
                    when (choice) {
                        AdBlockingChoice.DISABLE_UNTIL_RELAUNCH, AdBlockingChoice.ALWAYS_OFF -> showDisabledBottomSheet()
                        AdBlockingChoice.ALWAYS_ON -> {}
                    }
                }
            }
        }.show()
    }

    private fun showDisabledBottomSheet() {
        AdBlockingDisabledBottomSheetDialog(context, edgeToEdgeProvider).show()
    }

    private fun render(state: AdBlockingMenuState) {
        this.menuState = state
        when (state) {
            AdBlockingMenuState.Hidden -> isGone = true
            AdBlockingMenuState.Enabled -> {
                isGone = false
                menuItem.label(context.getString(R.string.ad_blocking_menu_disable))
            }
            AdBlockingMenuState.Disabled -> {
                isGone = false
                menuItem.label(context.getString(R.string.ad_blocking_menu_enable))
            }
        }
    }
}
