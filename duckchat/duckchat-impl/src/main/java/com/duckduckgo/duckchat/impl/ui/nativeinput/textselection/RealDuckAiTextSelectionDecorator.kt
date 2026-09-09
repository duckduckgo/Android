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

package com.duckduckgo.duckchat.impl.ui.nativeinput.textselection

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.children
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiTextSelectionDecorator
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckAiTextSelectionDecorator @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
) : DuckAiTextSelectionDecorator {

    override fun decorate(callback: ActionMode.Callback?, pageUrl: String?): ActionMode.Callback? {
        if (callback == null) return null
        val hideAskDuckAi = pageUrl != null && duckChatInternal.isDuckChatUrl(pageUrl.toUri())
        return object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = callback.onCreateActionMode(mode, menu)

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                val isPrepared = callback.onPrepareActionMode(mode, menu)
                menu?.decorateWithDuckAi(isHidden = hideAskDuckAi)
                return isPrepared
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                val isHandled = callback.onActionItemClicked(mode, item)
                if (item?.itemId == R.id.askDuckAi) mode?.finish()
                return isHandled
            }

            override fun onDestroyActionMode(mode: ActionMode?) = callback.onDestroyActionMode(mode)

            override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: Rect?) {
                if (callback is ActionMode.Callback2) {
                    callback.onGetContentRect(mode, view, outRect)
                } else {
                    super.onGetContentRect(mode, view, outRect)
                }
            }
        }
    }

    private fun Menu.decorateWithDuckAi(isHidden: Boolean) {
        if (findItem(R.id.askDuckAi) != null) return
        val processText = children.firstOrNull { it.intent?.component?.className == SELECTED_TEXT_ACTIVITY } ?: return
        processText.isVisible = false
        if (isHidden) return
        add(processText.groupId, R.id.askDuckAi, FIRST_ITEM, processText.title)
            .setIntent(processText.intent)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private companion object {
        private val SELECTED_TEXT_ACTIVITY = SelectedTextDuckAiActivity::class.java.name
        private const val FIRST_ITEM = 0
    }
}
