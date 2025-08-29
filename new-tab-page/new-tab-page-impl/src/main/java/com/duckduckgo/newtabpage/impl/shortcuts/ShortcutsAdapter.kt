/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.impl.databinding.RowShortcutSectionItemBinding
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.ShortcutViewHolder
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.ShortcutViewHolder.ItemState.Drag
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.ShortcutViewHolder.ItemState.LongPress
import com.duckduckgo.newtabpage.impl.shortcuts.ShortcutsAdapter.ShortcutViewHolder.ItemState.Stale

class ShortcutsAdapter(
    private val onMoveListener: (ViewHolder) -> Unit,
    private val onClickListener: (NewTabPageShortcutPlugin) -> Unit,
) : ListAdapter<ShortcutItem, ShortcutViewHolder>(NewTabSectionsDiffCallback()) {

    var expanded: Boolean = false

    companion object {
        const val SHORTCUT_ITEM_MAX_SIZE_DP = 90
        const val SHORTCUT_GRID_MAX_COLUMNS = 6
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ShortcutViewHolder {
        return ShortcutViewHolder(
            RowShortcutSectionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onMoveListener,
            onClickListener,
        )
    }

    override fun onBindViewHolder(
        holder: ShortcutViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position) as ShortcutItem)
    }

    class ShortcutViewHolder(
        private val binding: RowShortcutSectionItemBinding,
        private val onMoveListener: (ViewHolder) -> Unit,
        private val onClickListener: (NewTabPageShortcutPlugin) -> Unit,
    ) : ViewHolder(binding.root), DragDropViewHolderListener {

        private var itemState: ItemState = Stale

        sealed class ItemState {
            data object Stale : ItemState()
            data object LongPress : ItemState()
            data object Drag : ItemState()
        }

        private val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            binding.root,
            PropertyValuesHolder.ofFloat("scaleX", 1.2f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1.2f, 1f),
        ).apply {
            duration = 150L
        }
        private val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            binding.root,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f),
        ).apply {
            duration = 150L
        }

        fun bind(
            item: ShortcutItem,
        ) {
            with(binding.root) {
                setPrimaryText(item.plugin.getShortcut().titleResource())
                setLeadingIconDrawable(item.plugin.getShortcut().iconResource())
                setLongClickListener {
                    itemState = LongPress
                    scaleUpFavicon()
                    false
                }
                setClickListener {
                    onClickListener(item.plugin)
                    item.plugin.onClick(context)
                }
                configureTouchListener()
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun configureTouchListener() {
            binding.root.setTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (itemState != LongPress) return@setTouchListener false

                        onMoveListener(this@ShortcutViewHolder)
                    }

                    MotionEvent.ACTION_UP -> {
                        onItemReleased()
                    }
                }
                false
            }
        }

        override fun onDragStarted() {
            scaleUpFavicon()
            binding.root.hideTitle()
            itemState = Drag
        }

        override fun onItemMoved(
            dX: Float,
            dY: Float,
        ) {
            if (itemState != Drag) return
        }

        override fun onItemReleased() {
            scaleDownFavicon()
            binding.root.showTitle()
            itemState = Stale
        }

        private fun scaleUpFavicon() {
            if (binding.root.scaleX == 1f) {
                scaleUp.start()
            }
        }

        private fun scaleDownFavicon() {
            if (binding.root.scaleX != 1.0f) {
                scaleDown.start()
            }
        }
    }
}

data class ShortcutItem(val plugin: NewTabPageShortcutPlugin)

private class NewTabSectionsDiffCallback : DiffUtil.ItemCallback<ShortcutItem>() {
    override fun areItemsTheSame(
        oldItem: ShortcutItem,
        newItem: ShortcutItem,
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: ShortcutItem,
        newItem: ShortcutItem,
    ): Boolean {
        return oldItem == newItem
    }
}

interface DragDropViewHolderListener {
    fun onDragStarted()
    fun onItemMoved(
        dX: Float,
        dY: Float,
    )

    fun onItemReleased()
}
